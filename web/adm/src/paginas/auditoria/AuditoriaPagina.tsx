import {
  CabecalhoPagina,
  CampoSelect,
  CampoTexto,
  Detalhes,
  Dialogo,
  Status,
  Tabela,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import {
  OPERACAO_AUDITORIA,
  opcoes,
  type OperacaoAuditoria,
  type RegistroAuditoria,
} from '../../tipos.ts';

const TAMANHO = 20;

/**
 * Trilha de auditoria: quem incluiu, alterou ou excluiu o quê, de onde e quando. Só leitura (o banco
 * não aceita alteração da trilha).
 */
export function AuditoriaPagina() {
  const [pagina, setPagina] = useState(1);
  const [entidade, setEntidade] = useState<string | null>(null);
  const [operacao, setOperacao] = useState<OperacaoAuditoria | null>(null);
  const [email, setEmail] = useState('');
  const [entidadeId, setEntidadeId] = useState('');
  const [aberto, setAberto] = useState<RegistroAuditoria | null>(null);
  const filtroEmail = email.trim();
  const filtroId = entidadeId.trim();

  const entidades = useCarregar(() => api.get<string[]>('/api/adm/auditoria/entidades'), []);
  const registros = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (entidade) filtro.set('entidade', entidade);
    if (operacao) filtro.set('operacao', operacao);
    if (filtroEmail) filtro.set('email', filtroEmail);
    if (filtroId) filtro.set('entidadeId', filtroId);
    return api.get<Pagina<RegistroAuditoria>>(`/api/adm/auditoria?${filtro}`);
  }, [pagina, entidade, operacao, filtroEmail, filtroId]);

  const mudar =
    <T,>(definir: (valor: T) => void) =>
    (valor: T) => {
      definir(valor);
      setPagina(1);
    };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Auditoria" icone="shield-check">
        Cada inclusão, alteração e exclusão feita na plataforma, com quem fez, de onde e o antes e o
        depois dos campos. Senhas, tokens e segredos aparecem como ***.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoSelect<string>
          rotulo="Entidade"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={(entidades.dados ?? []).map((e) => ({ valor: e, rotulo: e }))}
          valor={entidade}
          aoMudar={mudar(setEntidade)}
        />
        <CampoSelect
          rotulo="Operação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={opcoes(OPERACAO_AUDITORIA)}
          valor={operacao}
          aoMudar={mudar(setOperacao)}
        />
        <CampoTexto rotulo="Id" valor={entidadeId} aoMudar={mudar(setEntidadeId)} maxLength={64} />
        <CampoTexto
          rotulo="E-mail do usuário"
          valor={email}
          aoMudar={mudar(setEmail)}
          maxLength={254}
        />
      </div>
      <Tabela<RegistroAuditoria>
        rotulo="Trilha de auditoria"
        colunas={[
          { campo: 'em', rotulo: 'Quando', formato: (v) => dataHora(v as string) },
          {
            campo: 'operacao',
            rotulo: 'Operação',
            render: (r) => (
              <Status
                rotulo={OPERACAO_AUDITORIA[r.operacao].rotulo}
                severidade={OPERACAO_AUDITORIA[r.operacao].severidade}
              />
            ),
          },
          {
            campo: 'entidade',
            rotulo: 'Registro',
            destaque: true,
            formato: (v, r) => `${v as string}${r.entidadeId ? ` #${r.entidadeId}` : ''}`,
          },
          { campo: 'usuarioEmail', rotulo: 'Usuário', formato: (_, r) => quem(r) },
          { campo: 'origem', rotulo: 'Origem' },
        ]}
        registros={registros.dados?.itens ?? []}
        carregando={registros.carregando && !registros.dados}
        atualizando={registros.carregando && !!registros.dados}
        erro={registros.erro}
        aoRecarregar={registros.recarregar}
        vazioTitulo="Nada registrado"
        vazioDescricao="Nenhuma ação com estes filtros."
        vazioIcone="shield-check"
        aoClicarLinha={setAberto}
        paginacao={{
          total: registros.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto && <DialogoRegistro registro={aberto} aoFechar={() => setAberto(null)} />}
    </div>
  );
}

function quem(registro: RegistroAuditoria): string {
  if (registro.usuarioEmail) return registro.usuarioEmail;
  if (registro.usuarioId) return `Usuário #${registro.usuarioId}`;
  return registro.origem.startsWith('job ') ? 'Sistema (worker)' : 'Sistema ou visitante';
}

export interface Mudanca {
  campo: string;
  antes: string;
  depois: string;
}

/** Campos do antes e do depois lado a lado, na ordem em que aparecem. */
export function mudancas(antes: string | null, depois: string | null): Mudanca[] {
  const a = objeto(antes);
  const d = objeto(depois);
  const campos = [...new Set([...Object.keys(a), ...Object.keys(d)])];
  return campos.map((campo) => ({
    campo,
    antes: campo in a ? texto(a[campo]) : '—',
    depois: campo in d ? texto(d[campo]) : '—',
  }));
}

function objeto(json: string | null): Record<string, unknown> {
  if (!json) return {};
  try {
    const valor: unknown = JSON.parse(json);
    return valor && typeof valor === 'object' ? (valor as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function texto(valor: unknown): string {
  if (valor === null || valor === undefined) return 'vazio';
  return typeof valor === 'string' ? valor : JSON.stringify(valor);
}

function DialogoRegistro({
  registro,
  aoFechar,
}: {
  registro: RegistroAuditoria;
  aoFechar: () => void;
}) {
  const linhas = mudancas(registro.antes, registro.depois);
  return (
    <Dialogo
      aberto
      titulo={`${OPERACAO_AUDITORIA[registro.operacao].rotulo}: ${registro.entidade}${registro.entidadeId ? ` #${registro.entidadeId}` : ''}`}
      subtitulo={dataHora(registro.em)}
      tamanho="grande"
      aoFechar={aoFechar}
    >
      <div className="pc-pilha">
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Usuário" valor={registro.usuarioNome ?? quem(registro)} />
          <Detalhes rotulo="E-mail" valor={registro.usuarioEmail} />
          <Detalhes rotulo="Origem" valor={registro.origem} />
          <Detalhes rotulo="IP" valor={registro.ip} />
          <Detalhes rotulo="Id da requisição" valor={registro.requestId} />
          <Detalhes rotulo="Navegador" valor={registro.userAgent} />
        </div>
        <Tabela<Mudanca>
          rotulo="Campos"
          colunas={[
            { campo: 'campo', rotulo: 'Campo', destaque: true },
            { campo: 'antes', rotulo: 'Antes' },
            { campo: 'depois', rotulo: 'Depois' },
          ]}
          registros={linhas}
          chave={(m) => m.campo}
          vazioTitulo="Sem campos"
          vazioDescricao="O registro não guardou valores."
        />
      </div>
    </Dialogo>
  );
}

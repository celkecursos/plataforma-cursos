import {
  AcaoLinha,
  CabecalhoPagina,
  CampoSelect,
  CampoTexto,
  Status,
  Tabela,
  useConfirmacao,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { data } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { PAPEIS, type UsuarioAdm } from '../../tipos.ts';

const TAMANHO = 20;

type Situacao = 'ativos' | 'bloqueados';

/**
 * Contas da plataforma: busca por nome ou e-mail, bloqueio (a pessoa sai na hora e não entra mais) e
 * encerramento das sessões abertas. Cada ação fica na trilha de auditoria.
 */
export function UsuariosPagina() {
  const { estado } = useSessao();
  const meuId = estado.status === 'autenticado' ? estado.usuario.id : null;
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [pagina, setPagina] = useState(1);
  const [busca, setBusca] = useState('');
  const [papel, setPapel] = useState<keyof typeof PAPEIS | null>(null);
  const [situacao, setSituacao] = useState<Situacao | null>(null);
  const termo = busca.trim();

  const usuarios = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (termo) filtro.set('busca', termo);
    if (papel) filtro.set('papel', papel);
    if (situacao) filtro.set('ativo', String(situacao === 'ativos'));
    return api.get<Pagina<UsuarioAdm>>(`/api/adm/usuarios?${filtro}`);
  }, [pagina, termo, papel, situacao]);

  const mudar =
    <T,>(definir: (valor: T) => void) =>
    (valor: T) => {
      definir(valor);
      setPagina(1);
    };

  const alterarSituacao = async (u: UsuarioAdm) => {
    const bloquear = u.ativo;
    const sim = await confirmar({
      titulo: bloquear ? 'Bloquear a conta?' : 'Desbloquear a conta?',
      mensagem: bloquear
        ? `${u.nome} sai de todos os dispositivos na hora e não consegue mais entrar.`
        : `${u.nome} volta a entrar com a própria senha.`,
      severidade: bloquear ? 'err' : 'info',
      rotuloAceitar: bloquear ? 'Bloquear' : 'Desbloquear',
      rotuloRecusar: 'Voltar',
    });
    if (!sim) return;
    const ok = await executar(
      () => api.patch(`/api/adm/usuarios/${u.id}/situacao`, { ativo: !bloquear }).then(() => true),
      bloquear ? 'Conta bloqueada' : 'Conta desbloqueada',
    );
    if (ok) usuarios.recarregar();
  };

  const encerrarSessoes = async (u: UsuarioAdm) => {
    const sim = await confirmar({
      titulo: 'Encerrar as sessões?',
      mensagem: `${u.nome} sai de todos os dispositivos e precisa entrar de novo.`,
      severidade: 'warn',
      rotuloAceitar: 'Encerrar sessões',
      rotuloRecusar: 'Voltar',
    });
    if (!sim) return;
    const ok = await executar(
      () => api.post(`/api/adm/usuarios/${u.id}/encerrar-sessoes`).then(() => true),
      'Sessões encerradas',
    );
    if (ok) usuarios.recarregar();
  };

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Usuários" icone="users">
        Alunos e administradores. Bloquear tira o acesso na hora; encerrar as sessões faz a pessoa
        entrar de novo em todos os dispositivos. As ações ficam na auditoria.
      </CabecalhoPagina>
      <div className="pc-filtros">
        <CampoTexto
          rotulo="Nome ou e-mail"
          valor={busca}
          aoMudar={mudar(setBusca)}
          maxLength={254}
        />
        <CampoSelect
          rotulo="Papel"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={Object.entries(PAPEIS).map(([valor, rotulo]) => ({
            valor: valor as keyof typeof PAPEIS,
            rotulo,
          }))}
          valor={papel}
          aoMudar={mudar(setPapel)}
        />
        <CampoSelect<Situacao>
          rotulo="Situação"
          variante="inline"
          placeholder="Todas"
          botaoLimpar
          opcoes={[
            { valor: 'ativos', rotulo: 'Ativos' },
            { valor: 'bloqueados', rotulo: 'Bloqueados' },
          ]}
          valor={situacao}
          aoMudar={mudar(setSituacao)}
        />
      </div>
      <Tabela<UsuarioAdm>
        rotulo="Usuários"
        colunas={[
          { campo: 'nome', rotulo: 'Nome', destaque: true },
          { campo: 'email', rotulo: 'E-mail' },
          {
            campo: 'papeis',
            rotulo: 'Papel',
            formato: (_, u) => u.papeis.map((p) => PAPEIS[p]).join(', '),
          },
          {
            campo: 'ativo',
            rotulo: 'Situação',
            render: (u) => (
              <Status
                rotulo={u.ativo ? 'Ativo' : 'Bloqueado'}
                severidade={u.ativo ? 'ok' : 'err'}
              />
            ),
          },
          {
            campo: 'segundoFatorAtivo',
            rotulo: 'Duas etapas',
            formato: (_, u) =>
              u.papeis.includes('ADMIN') ? (u.segundoFatorAtivo ? 'Ativa' : 'Pendente') : '—',
          },
          { campo: 'criadoEm', rotulo: 'Desde', formato: (v) => data(v as string) },
        ]}
        registros={usuarios.dados?.itens ?? []}
        carregando={usuarios.carregando && !usuarios.dados}
        atualizando={usuarios.carregando && !!usuarios.dados}
        erro={usuarios.erro}
        aoRecarregar={usuarios.recarregar}
        vazioTitulo="Nenhum usuário"
        vazioDescricao="Nenhuma conta com estes filtros."
        vazioIcone="users"
        paginacao={{
          total: usuarios.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
        acoes={(u) =>
          u.id !== meuId && (
            <>
              <AcaoLinha
                icone="log-out"
                rotulo={`Encerrar as sessões de ${u.nome}`}
                onClick={() => void encerrarSessoes(u)}
              />
              <AcaoLinha
                icone={u.ativo ? 'lock' : 'lock-open'}
                severidade={u.ativo ? 'err' : undefined}
                rotulo={u.ativo ? `Bloquear ${u.nome}` : `Desbloquear ${u.nome}`}
                onClick={() => void alterarSituacao(u)}
              />
            </>
          )
        }
      />
    </div>
  );
}

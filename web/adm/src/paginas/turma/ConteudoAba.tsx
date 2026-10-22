import {
  AcaoLinha,
  Botao,
  Card,
  Carregando,
  Status,
  Vazio,
  Alerta,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoAula } from '../../caminhos.ts';
import { ArquivosApoio } from '../../componentes/ArquivosApoio.tsx';
import { DialogoTexto } from '../../componentes/DialogoTexto.tsx';
import { useAcao } from '../../componentes/useAcao.ts';
import { STATUS_CONTEUDO, type Aula, type Curso, type Modulo, type Turma } from '../../tipos.ts';

/** Ids na nova ordem, com o item da posição `de` trocado com o vizinho. */
export function trocar<T extends { id: number }>(itens: T[], de: number, para: number): number[] {
  const ids = itens.map((item) => item.id);
  [ids[de], ids[para]] = [ids[para]!, ids[de]!];
  return ids;
}

type Dialogo =
  | { tipo: 'novoModulo' }
  | { tipo: 'renomear'; modulo: Modulo }
  | { tipo: 'novaAula'; modulo: Modulo };

export function ConteudoAba({ curso, turma }: { curso: Curso; turma: Turma }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const modulos = useCarregar(
    () => api.get<Modulo[]>(`/api/adm/turmas/${turma.id}/modulos`),
    [turma.id],
  );

  if (modulos.erro && !modulos.dados) {
    return (
      <Alerta
        severidade="err"
        titulo="Não foi possível carregar os módulos"
        mensagem={modulos.erro}
      />
    );
  }
  if (!modulos.dados) return <Carregando mensagem="Carregando o conteúdo..." />;
  const lista = modulos.dados;

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        <Botao
          rotulo="Novo módulo"
          icone="plus"
          onClick={() => setDialogo({ tipo: 'novoModulo' })}
        />
      </div>
      {lista.length === 0 && (
        <Vazio
          titulo="Nenhum módulo"
          descricao="Crie o primeiro módulo e depois as aulas dele."
          icone="layers"
        />
      )}
      {lista.map((modulo, indice) => (
        <CardModulo
          key={modulo.id}
          modulo={modulo}
          primeiro={indice === 0}
          ultimo={indice === lista.length - 1}
          aoMover={async (para) => {
            modulos.definir(
              await api.put<Modulo[]>(`/api/adm/turmas/${turma.id}/modulos/ordem`, {
                ids: trocar(lista, indice, para === 'cima' ? indice - 1 : indice + 1),
              }),
            );
          }}
          aoMudar={(novo) => modulos.definir(lista.map((m) => (m.id === novo.id ? novo : m)))}
          aoExcluir={modulos.recarregar}
          aoRenomear={() => setDialogo({ tipo: 'renomear', modulo })}
          aoNovaAula={() => setDialogo({ tipo: 'novaAula', modulo })}
          aoAbrirAula={(aula) => navigate(caminhoAula(curso.id, turma.id, modulo.id, aula.id))}
        />
      ))}
      {dialogo?.tipo === 'novoModulo' && (
        <DialogoTexto
          titulo="Novo módulo"
          rotulo="Título do módulo"
          campo="titulo"
          maximo={150}
          rotuloSalvar="Criar módulo"
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            await api.post(`/api/adm/turmas/${turma.id}/modulos`, { titulo });
            toast.ok('Módulo criado', titulo);
            modulos.recarregar();
            setDialogo(null);
          }}
        />
      )}
      {dialogo?.tipo === 'renomear' && (
        <DialogoTexto
          titulo="Renomear módulo"
          rotulo="Título do módulo"
          campo="titulo"
          maximo={150}
          inicial={dialogo.modulo.titulo}
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            const novo = await api.put<Modulo>(`/api/adm/modulos/${dialogo.modulo.id}`, { titulo });
            modulos.definir(lista.map((m) => (m.id === novo.id ? novo : m)));
            toast.ok('Módulo renomeado');
            setDialogo(null);
          }}
        />
      )}
      {dialogo?.tipo === 'novaAula' && (
        <DialogoTexto
          titulo={`Nova aula em ${dialogo.modulo.titulo}`}
          rotulo="Título da aula"
          campo="titulo"
          maximo={150}
          rotuloSalvar="Criar aula"
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            const aula = await api.post<Aula>(`/api/adm/modulos/${dialogo.modulo.id}/aulas`, {
              titulo,
              gratuita: false,
            });
            toast.ok('Aula criada', aula.titulo);
            navigate(caminhoAula(curso.id, turma.id, dialogo.modulo.id, aula.id));
          }}
        />
      )}
    </div>
  );
}

interface PropsModulo {
  modulo: Modulo;
  primeiro: boolean;
  ultimo: boolean;
  aoMover: (para: 'cima' | 'baixo') => Promise<void>;
  aoMudar: (modulo: Modulo) => void;
  aoExcluir: () => void;
  aoRenomear: () => void;
  aoNovaAula: () => void;
  aoAbrirAula: (aula: Aula) => void;
}

function CardModulo({
  modulo,
  primeiro,
  ultimo,
  aoMover,
  aoMudar,
  aoExcluir,
  aoRenomear,
  aoNovaAula,
  aoAbrirAula,
}: PropsModulo) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const aulas = useCarregar(
    () => api.get<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas`),
    [modulo.id],
  );
  const status = STATUS_CONTEUDO[modulo.status];
  const lista = aulas.dados ?? [];

  return (
    <Card
      titulo={`${modulo.ordem}. ${modulo.titulo}`}
      aria-label={`Módulo ${modulo.titulo}`}
      ferramentas={
        <div className="pc-linha">
          <Status rotulo={status.rotulo} severidade={status.severidade} />
          <AcaoLinha
            icone="chevron-up"
            rotulo={`Subir ${modulo.titulo}`}
            disabled={primeiro || executando}
            onClick={() => void executar(() => aoMover('cima'))}
          />
          <AcaoLinha
            icone="chevron-down"
            rotulo={`Descer ${modulo.titulo}`}
            disabled={ultimo || executando}
            onClick={() => void executar(() => aoMover('baixo'))}
          />
          <AcaoLinha icone="pencil" rotulo={`Renomear ${modulo.titulo}`} onClick={aoRenomear} />
          <AcaoLinha
            icone={modulo.status === 'ATIVO' ? 'eye-off' : 'eye'}
            rotulo={
              modulo.status === 'ATIVO' ? `Inativar ${modulo.titulo}` : `Ativar ${modulo.titulo}`
            }
            onClick={async () => {
              const novo = await executar(() =>
                api.patch<Modulo>(`/api/adm/modulos/${modulo.id}/status`, {
                  status: modulo.status === 'ATIVO' ? 'INATIVO' : 'ATIVO',
                }),
              );
              if (novo) aoMudar(novo);
            }}
          />
          <AcaoLinha
            icone="trash-2"
            severidade="err"
            rotulo={`Excluir ${modulo.titulo}`}
            onClick={async () => {
              if (
                !(await confirmar.remover({
                  mensagem: `O módulo "${modulo.titulo}" será excluído.`,
                }))
              )
                return;
              const ok = await executar(
                () => api.delete(`/api/adm/modulos/${modulo.id}`).then(() => true),
                'Módulo excluído',
              );
              if (ok) aoExcluir();
            }}
          />
        </div>
      }
    >
      {aulas.erro && <Alerta severidade="err" mensagem={aulas.erro} />}
      {aulas.carregando && !aulas.dados && <Carregando mensagem="Carregando as aulas..." />}
      {aulas.dados && lista.length === 0 && <p className="pc-dica">Nenhuma aula neste módulo.</p>}
      {lista.length > 0 && (
        <ol className="pc-lista-aulas" aria-label={`Aulas de ${modulo.titulo}`}>
          {lista.map((aula, indice) => (
            <li key={aula.id}>
              <button
                type="button"
                className="pc-link pc-aula-titulo"
                onClick={() => aoAbrirAula(aula)}
              >
                {aula.titulo}
              </button>
              {aula.gratuita && <Status rotulo="Gratuita" severidade="info" />}
              <Status
                rotulo={STATUS_CONTEUDO[aula.status].rotulo}
                severidade={STATUS_CONTEUDO[aula.status].severidade}
              />
              <AcaoLinha
                icone="chevron-up"
                rotulo={`Subir ${aula.titulo}`}
                disabled={indice === 0 || executando}
                onClick={async () => {
                  const nova = await executar(() =>
                    api.put<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas/ordem`, {
                      ids: trocar(lista, indice, indice - 1),
                    }),
                  );
                  if (nova) aulas.definir(nova);
                }}
              />
              <AcaoLinha
                icone="chevron-down"
                rotulo={`Descer ${aula.titulo}`}
                disabled={indice === lista.length - 1 || executando}
                onClick={async () => {
                  const nova = await executar(() =>
                    api.put<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas/ordem`, {
                      ids: trocar(lista, indice, indice + 1),
                    }),
                  );
                  if (nova) aulas.definir(nova);
                }}
              />
            </li>
          ))}
        </ol>
      )}
      <div className="pc-linha">
        <Botao
          rotulo="Nova aula"
          icone="plus"
          aparencia="contorno"
          tamanho="pequeno"
          onClick={aoNovaAula}
        />
      </div>
      <ArquivosApoio dono="modulos" donoId={modulo.id} titulo="Arquivos de apoio do módulo" />
    </Card>
  );
}

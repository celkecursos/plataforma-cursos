import { AcaoLinha, Botao, Status, Tabela, useConfirmacao, useToast } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoTurma } from '../../caminhos.ts';
import { DialogoTexto } from '../../componentes/DialogoTexto.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_TURMA, type Curso, type Turma } from '../../tipos.ts';

export function TurmasAba({ curso }: { curso: Curso }) {
  const navigate = useNavigate();
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [dialogo, setDialogo] = useState<{ turma?: Turma } | null>(null);
  const turmas = useCarregar(
    () => api.get<Turma[]>(`/api/adm/cursos/${curso.id}/turmas`),
    [curso.id],
  );

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        <Botao rotulo="Nova turma" icone="plus" onClick={() => setDialogo({})} />
      </div>
      <Tabela<Turma>
        rotulo="Turmas"
        colunas={[
          { campo: 'nome', rotulo: 'Turma', destaque: true },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (t) => (
              <Status
                rotulo={STATUS_TURMA[t.status].rotulo}
                severidade={STATUS_TURMA[t.status].severidade}
              />
            ),
          },
          {
            campo: 'percentualConclusao',
            rotulo: 'Certificado com',
            formato: (v) => `${v as number}% concluído`,
          },
        ]}
        registros={turmas.dados ?? []}
        carregando={turmas.carregando && !turmas.dados}
        erro={turmas.erro}
        aoRecarregar={turmas.recarregar}
        vazioTitulo="Nenhuma turma"
        vazioDescricao="O conteúdo (módulos e aulas) e as matrículas ficam na turma."
        vazioIcone="users"
        aoClicarLinha={(t) => navigate(caminhoTurma(curso.id, t.id))}
        acoes={(t) => (
          <>
            <AcaoLinha
              icone="pencil"
              rotulo={`Renomear ${t.nome}`}
              onClick={() => setDialogo({ turma: t })}
            />
            <AcaoLinha
              icone="trash-2"
              severidade="err"
              rotulo={`Excluir ${t.nome}`}
              onClick={async () => {
                if (!(await confirmar.remover({ mensagem: `A turma "${t.nome}" será excluída.` })))
                  return;
                const ok = await executar(
                  () => api.delete(`/api/adm/turmas/${t.id}`).then(() => true),
                  'Turma excluída',
                );
                if (ok) turmas.recarregar();
              }}
            />
          </>
        )}
      />
      {dialogo && (
        <DialogoTexto
          titulo={dialogo.turma ? 'Renomear turma' : 'Nova turma'}
          rotulo="Nome da turma"
          campo="nome"
          maximo={120}
          inicial={dialogo.turma?.nome}
          rotuloSalvar={dialogo.turma ? 'Salvar' : 'Criar turma'}
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (nome) => {
            if (dialogo.turma) {
              await api.put(`/api/adm/turmas/${dialogo.turma.id}`, { nome });
              toast.ok('Turma renomeada');
              turmas.recarregar();
            } else {
              const turma = await api.post<Turma>(`/api/adm/cursos/${curso.id}/turmas`, { nome });
              toast.ok('Turma criada', turma.nome);
              navigate(caminhoTurma(curso.id, turma.id));
            }
            setDialogo(null);
          }}
        />
      )}
    </div>
  );
}

import { Botao, CabecalhoPagina, CampoSelect, Status, Tabela, useToast } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { caminhoCurso } from '../../caminhos.ts';
import { opcoes, STATUS_CURSO, type Curso, type StatusCurso } from '../../tipos.ts';
import { FormCurso } from './FormCurso.tsx';

const TAMANHO = 20;

export function CursosPagina() {
  const navigate = useNavigate();
  const toast = useToast();
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusCurso | null>(null);
  const [novo, setNovo] = useState(false);

  const cursos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    return api.get<Pagina<Curso>>(`/api/adm/cursos?${filtro}`);
  }, [pagina, status]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo="Cursos"
        icone="graduation-cap"
        acoes={<Botao rotulo="Novo curso" icone="plus" onClick={() => setNovo(true)} />}
      />
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Status"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={opcoes(STATUS_CURSO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
      </div>
      <Tabela<Curso>
        rotulo="Cursos"
        colunas={[
          { campo: 'titulo', rotulo: 'Título', destaque: true },
          { campo: 'slug', rotulo: 'Endereço' },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (c) => (
              <Status
                rotulo={STATUS_CURSO[c.status].rotulo}
                severidade={STATUS_CURSO[c.status].severidade}
              />
            ),
          },
          { campo: 'atualizadoEm', rotulo: 'Atualizado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={cursos.dados?.itens ?? []}
        carregando={cursos.carregando && !cursos.dados}
        atualizando={cursos.carregando && !!cursos.dados}
        erro={cursos.erro}
        aoRecarregar={cursos.recarregar}
        vazioTitulo="Nenhum curso"
        vazioDescricao={status ? 'Nenhum curso com este status.' : 'Cadastre o primeiro curso.'}
        vazioIcone="graduation-cap"
        aoClicarLinha={(c) => navigate(caminhoCurso(c.id))}
        paginacao={{
          total: cursos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {novo && (
        <FormCurso
          aberto
          aoFechar={() => setNovo(false)}
          aoSalvar={async (dados) => {
            const curso = await api.post<Curso>('/api/adm/cursos', dados);
            toast.ok('Curso cadastrado', curso.titulo);
            navigate(caminhoCurso(curso.id));
          }}
        />
      )}
    </div>
  );
}

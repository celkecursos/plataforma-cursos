import { Alerta, CampoTextarea, CampoTexto, Form, FormSecao } from '@celke/kole-react';
import { useState } from 'react';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio, semErros } from '@compartilhado/ui/validacao.ts';
import type { Curso } from '../../tipos.ts';

export interface DadosCurso {
  titulo: string;
  slug: string | null;
  resumo: string;
  descricao: string | null;
}

type Erros = Partial<Record<keyof DadosCurso, string>>;

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

interface Props {
  aberto: boolean;
  /** Sem curso, é cadastro novo. */
  curso?: Curso;
  aoSalvar: (dados: DadosCurso) => Promise<void>;
  aoFechar: () => void;
}

/** Cadastro e edição dos dados de um curso, em diálogo. */
export function FormCurso({ aberto, curso, aoSalvar, aoFechar }: Props) {
  const [titulo, setTitulo] = useState(curso?.titulo ?? '');
  const [slug, setSlug] = useState(curso?.slug ?? '');
  const [resumo, setResumo] = useState(curso?.resumo ?? '');
  const [descricao, setDescricao] = useState(curso?.descricao ?? '');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Erros = {
      titulo: erroObrigatorio(titulo, 'Informe o título.', 120),
      resumo: erroObrigatorio(resumo, 'Informe o resumo.', 300),
      slug:
        slug.trim() && !SLUG.test(slug.trim())
          ? 'Use só letras minúsculas, números e hífens.'
          : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await aoSalvar({
        titulo: titulo.trim(),
        slug: slug.trim() || null,
        resumo: resumo.trim(),
        descricao: descricao.trim() || null,
      });
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          titulo: erro.erroDoCampo('titulo'),
          slug: erro.erroDoCampo('slug'),
          resumo: erro.erroDoCampo('resumo'),
          descricao: erro.erroDoCampo('descricao'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto={aberto}
      entidade="curso"
      modo={curso ? 'editar' : 'novo'}
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="grande"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Dados do curso" colunas={2}>
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={120}
        />
        <CampoTexto
          rotulo="Endereço (slug)"
          valor={slug}
          aoMudar={setSlug}
          erro={erros.slug}
          dica="Em branco, é gerado a partir do título."
        />
        <CampoTextarea
          rotulo="Resumo"
          valor={resumo}
          aoMudar={setResumo}
          erro={erros.resumo}
          obrigatorio
          maxLength={300}
          contador
          className="lp-col-full"
        />
        <CampoTextarea
          rotulo="Descrição"
          valor={descricao}
          aoMudar={setDescricao}
          erro={erros.descricao}
          rows={6}
          dica="Aparece na página do curso."
          className="lp-col-full"
        />
      </FormSecao>
    </Form>
  );
}

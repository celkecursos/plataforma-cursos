import { Botao, CampoTexto, Dialogo } from '@celke/kole-react';
import { useState } from 'react';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';

interface Props {
  titulo: string;
  rotulo: string;
  inicial?: string;
  maximo: number;
  /** Campo da API cujo erro aparece no campo (ex.: `nome`, `titulo`). */
  campo: string;
  rotuloSalvar?: string;
  aoSalvar: (valor: string) => Promise<void>;
  aoFechar: () => void;
}

/** Diálogo com um campo de texto só: nome de turma, título de módulo. */
export function DialogoTexto({
  titulo,
  rotulo,
  inicial = '',
  maximo,
  campo,
  rotuloSalvar = 'Salvar',
  aoSalvar,
  aoFechar,
}: Props) {
  const [valor, setValor] = useState(inicial);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(valor, `Informe ${rotulo.toLowerCase()}.`, maximo);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      await aoSalvar(valor.trim());
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo(campo)) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Dialogo
      aberto
      titulo={titulo}
      tamanho="pequeno"
      aoFechar={aoFechar}
      rodape={
        <>
          <Botao rotulo="Cancelar" aparencia="texto" onClick={aoFechar} />
          <Botao
            rotulo={rotuloSalvar}
            icone="save"
            carregando={salvando}
            type="submit"
            form="pc-dialogo-texto"
          />
        </>
      }
    >
      <form
        noValidate
        id="pc-dialogo-texto"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo={rotulo}
          valor={valor}
          aoMudar={setValor}
          erro={erro}
          obrigatorio
          maxLength={maximo}
          autoFocus
        />
      </form>
    </Dialogo>
  );
}

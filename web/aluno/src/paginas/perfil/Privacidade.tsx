import { Alerta, Botao, Card, CampoSenha, CampoTexto, Dialogo } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import {
  erroConfirmacaoExclusao,
  nomeArquivoDados,
  PALAVRA_EXCLUSAO,
  urlPoliticaPrivacidade,
} from './privacidade.ts';

/**
 * Direitos do titular: baixar a cópia de todos os dados da conta e excluir a conta. A política de
 * privacidade explica o que é apagado e o que fica pelo prazo legal.
 */
export function Privacidade() {
  const { executar, executando } = useAcao();
  const [excluindo, setExcluindo] = useState(false);

  const baixar = () =>
    executar(async () => {
      const dados = await api.get<unknown>('/api/perfil/dados');
      const arquivo = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(arquivo);
      link.download = nomeArquivoDados(new Date());
      link.click();
      URL.revokeObjectURL(link.href);
    }, 'Cópia dos dados baixada');

  return (
    <Card titulo="Privacidade">
      <div className="pc-pilha">
        <p>
          Baixe uma cópia de tudo o que a plataforma guarda sobre você: conta, matrículas,
          progresso, fórum, certificados, pedidos, aceites dos termos e registros de acesso.
        </p>
        <div>
          <Botao
            rotulo="Baixar meus dados"
            icone="download"
            aparencia="contorno"
            carregando={executando}
            onClick={() => void baixar()}
          />
        </div>
        <p>
          Excluir a conta apaga o seu nome, e-mail, foto, progresso e certificados e encerra todas
          as sessões. Matrículas, pedidos, aceites dos termos e registros de acesso ficam pelo prazo
          que a lei manda guardar, sem o seu nome nem o seu e-mail.{' '}
          <a
            className="pc-link"
            href={urlPoliticaPrivacidade(window.location)}
            target="_blank"
            rel="noreferrer"
          >
            Política de privacidade
          </a>
        </p>
        <div>
          <Botao
            rotulo="Excluir minha conta"
            icone="trash-2"
            severidade="err"
            aparencia="contorno"
            onClick={() => setExcluindo(true)}
          />
        </div>
      </div>
      {excluindo && <DialogoExclusao aoFechar={() => setExcluindo(false)} />}
    </Card>
  );
}

function DialogoExclusao({ aoFechar }: { aoFechar: () => void }) {
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<{ senha?: string; confirmacao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);

  const excluir = async () => {
    const novos = {
      senha: senha ? undefined : 'Informe a sua senha.',
      confirmacao: erroConfirmacaoExclusao(confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (novos.senha || novos.confirmacao) return;
    setEnviando(true);
    try {
      await api.post('/api/perfil/exclusao', { senha, confirmacao: confirmacao.trim() });
      // A API encerrou as sessões e limpou o cookie: recarrega na entrada, descartando o token da aba
      window.location.replace(`${CAMINHOS.entrar}?conta=excluida`);
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
      setEnviando(false);
    }
  };

  return (
    <Dialogo
      aberto
      titulo="Excluir minha conta"
      subtitulo="Não dá para desfazer."
      aoFechar={aoFechar}
      rodape={
        <>
          <Botao rotulo="Voltar" aparencia="contorno" onClick={aoFechar} />
          <Botao
            rotulo="Excluir a conta"
            icone="trash-2"
            severidade="err"
            carregando={enviando}
            onClick={() => void excluir()}
          />
        </>
      }
    >
      <div className="pc-pilha">
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Sua senha"
          valor={senha}
          aoMudar={setSenha}
          erro={erros.senha}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoTexto
          rotulo={`Digite ${PALAVRA_EXCLUSAO} para confirmar`}
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="off"
        />
      </div>
    </Dialogo>
  );
}

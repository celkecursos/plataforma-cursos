import {
  Alerta,
  Botao,
  Card,
  CampoTexto,
  Carregando,
  Detalhes,
  Progresso,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Certificado, Download, SituacaoCertificado } from '../../tipos.ts';

/** Situação do certificado na turma: falta quanto, emitir com o nome completo e baixar o PDF. */
export function CertificadoAba({ turmaId }: { turmaId: number }) {
  const situacao = useCarregar(
    () => api.get<SituacaoCertificado>(`/api/aluno/turmas/${turmaId}/certificado`),
    [turmaId],
  );

  if (situacao.erro && !situacao.dados) return <Alerta severidade="err" mensagem={situacao.erro} />;
  if (!situacao.dados) return <Carregando mensagem="Carregando o certificado..." />;
  const s = situacao.dados;

  if (s.certificado) return <CertificadoEmitido certificado={s.certificado} />;
  if (!s.concluiu) {
    return (
      <Card titulo="Certificado">
        <Progresso
          rotulo="Aulas vistas"
          valor={s.percentualVisto}
          marcos={[s.percentualExigido]}
          mostrarValor="fim"
        />
        <p className="pc-dica">
          O certificado é liberado ao ver {s.percentualExigido}% das aulas. Você já viu{' '}
          {s.percentualVisto}%.
        </p>
      </Card>
    );
  }
  return (
    <EmitirCertificado
      turmaId={turmaId}
      aoEmitir={(certificado) => situacao.definir({ ...s, certificado })}
    />
  );
}

function EmitirCertificado({
  turmaId,
  aoEmitir,
}: {
  turmaId: number;
  aoEmitir: (c: Certificado) => void;
}) {
  const { estado } = useSessao();
  const [nome, setNome] = useState(estado.status === 'autenticado' ? estado.usuario.nome : '');
  const [erro, setErro] = useState<string>();
  const [emitindo, setEmitindo] = useState(false);

  const emitir = async () => {
    const problema = erroObrigatorio(nome, 'Informe o nome completo.', 150);
    setErro(problema);
    if (problema) return;
    setEmitindo(true);
    try {
      aoEmitir(
        await api.post<Certificado>(`/api/aluno/turmas/${turmaId}/certificado`, {
          nomeCompleto: nome.trim(),
        }),
      );
    } catch (falha) {
      setErro(
        (falha instanceof ErroApi && falha.erroDoCampo('nomeCompleto')) || mensagemDeErro(falha),
      );
    } finally {
      setEmitindo(false);
    }
  };

  return (
    <Card titulo="Certificado liberado">
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void emitir();
        }}
      >
        <Alerta
          severidade="ok"
          mensagem="Parabéns, você concluiu o curso! Confira como o seu nome deve sair no certificado: depois de emitido, ele não muda."
        />
        <CampoTexto
          rotulo="Nome completo no certificado"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={150}
        />
        <div>
          <Botao type="submit" rotulo="Emitir certificado" icone="award" carregando={emitindo} />
        </div>
      </form>
    </Card>
  );
}

export function CertificadoEmitido({ certificado }: { certificado: Certificado }) {
  const { executar, executando } = useAcao();
  return (
    <Card titulo="Certificado emitido" aria-label={`Certificado de ${certificado.cursoTitulo}`}>
      <div className="pc-grade-detalhes">
        <Detalhes rotulo="Curso" valor={certificado.cursoTitulo} />
        <Detalhes rotulo="Nome" valor={certificado.nomeCompleto} />
        <Detalhes rotulo="Emitido em" valor={dataHora(certificado.emitidoEm)} />
        <Detalhes rotulo="Código de validação" valor={certificado.codigo} />
        {certificado.cargaHorariaHoras != null && (
          <Detalhes rotulo="Carga horária" valor={`${certificado.cargaHorariaHoras} h`} />
        )}
      </div>
      <div className="pc-linha">
        <Botao
          rotulo="Baixar PDF"
          icone="download"
          carregando={executando}
          onClick={async () => {
            const download = await executar(() =>
              api.get<Download>(`/api/aluno/certificados/${certificado.id}/download`),
            );
            if (download) window.open(download.url, '_blank', 'noopener');
          }}
        />
      </div>
    </Card>
  );
}

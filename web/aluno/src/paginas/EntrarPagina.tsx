import { TelaLogin } from '@celke/kole-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { DesafioSegundoFator } from '@compartilhado/api/tipos.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { SegundoFator } from '@compartilhado/sessao/SegundoFator.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  // Quem veio de "Comprar" cria a conta e volta para a compra depois de entrar
  const comVoltar = parametros.get('voltar')
    ? `?voltar=${encodeURIComponent(parametros.get('voltar')!)}`
    : '';

  // Administrador que entra pela área do aluno também passa pelo segundo fator
  const [desafio, setDesafio] = useState<DesafioSegundoFator>();

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  if (desafio) {
    return (
      <SegundoFator
        marca="Plataforma de Cursos"
        desafio={desafio}
        aoConcluir={() => navigate(destino, { replace: true })}
        aoVoltar={() => setDesafio(undefined)}
      />
    );
  }

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
          : parametros.get('conta') === 'excluida'
            ? 'Sua conta foi excluída. Enviamos a confirmação para o seu e-mail.'
            : 'Acesse seus cursos, aulas e certificados.'
      }
      apresentacao={{
        chamada: 'Plataforma de Cursos',
        titulo: 'Aprenda no seu ritmo',
        descricao:
          'Aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado ao concluir.',
        destaques: [
          { icone: 'circle-play', texto: 'Aulas em vídeo de qualquer lugar' },
          { icone: 'message-square', texto: 'Dúvidas respondidas no fórum' },
          { icone: 'award', texto: 'Certificado com código de validação' },
        ],
      }}
      linkSenha={<Link to={CAMINHOS.recuperarSenha}>Esqueci minha senha</Link>}
      acoes={
        <p>
          Ainda não tem conta? <Link to={CAMINHOS.cadastro + comVoltar}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          const resultado = await entrar(usuario.trim(), senha);
          if (resultado.tipo === 'desafio') {
            setDesafio(resultado.desafio);
            return;
          }
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}

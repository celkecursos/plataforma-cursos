import { TelaLogin } from '@celke/kole-react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
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
          Ainda não tem conta? <Link to={CAMINHOS.cadastro}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          await entrar(usuario.trim(), senha);
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}

import { TelaLogin } from '@celke/kole-react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { MARCA } from '../marca.ts';

/** Login do ADM. Quem entra sem o papel de administrador sai na hora, com o aviso. */
export function EntrarPagina() {
  const { estado, entrar, sair } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));

  if (estado.status === 'autenticado' && estado.usuario.papeis.includes('ADMIN')) {
    return <Navigate to={destino} replace />;
  }

  return (
    <TelaLogin
      marca={MARCA}
      modelo="formulario"
      identificador="email"
      titulo="Administração"
      descricao="Acesso restrito à equipe da plataforma."
      aoEntrar={async ({ usuario, senha }) => {
        try {
          const conta = await entrar(usuario.trim(), senha);
          if (!conta.papeis.includes('ADMIN')) {
            await sair();
            return { erro: 'Acesso restrito a administradores.' };
          }
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}

import { TelaLogin } from '@celke/kole-react';
import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { DesafioSegundoFator } from '@compartilhado/api/tipos.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { SegundoFator } from '@compartilhado/sessao/SegundoFator.tsx';
import { MARCA } from '../marca.ts';

/**
 * Login do ADM: e-mail e senha, depois o segundo fator (obrigatório para administradores). Quem entra
 * sem o papel de administrador sai na hora, com o aviso.
 */
export function EntrarPagina() {
  const { estado, entrar, sair } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  const [desafio, setDesafio] = useState<DesafioSegundoFator>();

  if (estado.status === 'autenticado' && estado.usuario.papeis.includes('ADMIN')) {
    return <Navigate to={destino} replace />;
  }

  if (desafio) {
    return (
      <SegundoFator
        marca="Administração"
        desafio={desafio}
        aoConcluir={() => navigate(destino, { replace: true })}
        aoVoltar={() => setDesafio(undefined)}
      />
    );
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
          const resultado = await entrar(usuario.trim(), senha);
          if (resultado.tipo === 'desafio') {
            setDesafio(resultado.desafio);
            return;
          }
          if (!resultado.usuario.papeis.includes('ADMIN')) {
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

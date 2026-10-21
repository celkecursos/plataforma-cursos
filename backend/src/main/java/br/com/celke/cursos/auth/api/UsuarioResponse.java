package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.List;

/** Dados públicos do usuário: nunca inclui senha, hash ou tokens. */
public record UsuarioResponse(Long id, String nome, String email, List<Papel> papeis) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPapeis().stream().sorted().toList());
    }
}

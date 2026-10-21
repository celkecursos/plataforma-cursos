package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de usuários para testes unitários (sem banco: o id é preenchido aqui). */
public final class UsuarioFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private UsuarioFixture() {}

    public static Usuario usuario(long id, String email, String senhaHash, Papel... papeis) {
        Usuario usuario = new Usuario("Usuário " + id, email, senhaHash, Set.of(papeis), CRIADO_EM);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    public static Usuario inativo(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "ativo", false);
        return usuario;
    }
}

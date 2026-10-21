package br.com.celke.cursos.support;

import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seed do profile {@code test}: um admin e um aluno, com as credenciais de {@link UsuariosSeed}. */
@Component
@Profile("test")
class SeedTeste implements ApplicationRunner {

    private final UsuarioService usuarioService;

    SeedTeste(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do Seed", UsuariosSeed.ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do Seed", UsuariosSeed.ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criarConfirmado(nome, email, UsuariosSeed.SENHA, Set.of(papel));
        }
    }
}

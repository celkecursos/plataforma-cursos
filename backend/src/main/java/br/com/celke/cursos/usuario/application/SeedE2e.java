package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed da stack de e2e (profile {@code e2e}): um admin e um aluno confirmados, com a senha de
 * {@code SEED_E2E_SENHA}. Nunca ligado nos servidores.
 */
@Component
@Profile("e2e")
public class SeedE2e implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin.e2e@teste.celke.uno";
    public static final String ALUNO_EMAIL = "aluno.e2e@teste.celke.uno";

    private final UsuarioService usuarioService;
    private final String senha;

    public SeedE2e(UsuarioService usuarioService, @Value("${SEED_E2E_SENHA}") String senha) {
        String problema = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN);
        if (problema != null) {
            throw new IllegalArgumentException("SEED_E2E_SENHA inválida: " + problema);
        }
        this.usuarioService = usuarioService;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do e2e", ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do e2e", ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criarConfirmado(nome, email, senha, Set.of(papel));
        }
    }
}

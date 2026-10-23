package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.usuario.application.SeedE2e;
import br.com.celke.cursos.usuario.application.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seed da stack de e2e: o admin do seed já entra com o segundo fator ativo, com o segredo de
 * {@code SEED_E2E_TOTP} (Base32), que os testes usam para calcular o código. Nunca ligado nos servidores.
 */
@Component
@Profile("e2e")
@Order(2)
public class SeedSegundoFatorE2e implements ApplicationRunner {

    private final UsuarioService usuarioService;
    private final SegundoFatorService segundoFatorService;
    private final String segredo;

    public SeedSegundoFatorE2e(
            UsuarioService usuarioService,
            SegundoFatorService segundoFatorService,
            @Value("${SEED_E2E_TOTP}") String segredo) {
        this.usuarioService = usuarioService;
        this.segundoFatorService = segundoFatorService;
        this.segredo = segredo;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarioService
                .buscarPorEmail(SeedE2e.ADMIN_EMAIL)
                .ifPresent(admin -> segundoFatorService.ativarComSegredo(admin.getId(), segredo));
    }
}

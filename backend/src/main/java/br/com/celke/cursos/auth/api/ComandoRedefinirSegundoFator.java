package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.auth.application.SegundoFatorService;
import br.com.celke.cursos.usuario.api.ComandoCriarAdmin;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code redefinir-2fa <email>}: para o administrador que perdeu o celular e os códigos de
 * recuperação. Apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
 *
 * <pre>docker compose run --rm api redefinir-2fa admin@celke.com.br</pre>
 */
@Component
@Profile(ComandoCriarAdmin.PERFIL)
public class ComandoRedefinirSegundoFator implements ApplicationRunner {

    public static final String NOME = "redefinir-2fa";

    private static final Logger LOG = LoggerFactory.getLogger(ComandoRedefinirSegundoFator.class);

    private final UsuarioService usuarioService;
    private final SegundoFatorService segundoFatorService;
    private final RefreshTokenService refreshTokenService;

    public ComandoRedefinirSegundoFator(
            UsuarioService usuarioService,
            SegundoFatorService segundoFatorService,
            RefreshTokenService refreshTokenService) {
        this.usuarioService = usuarioService;
        this.segundoFatorService = segundoFatorService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var argumentos = args.getNonOptionArgs();
        if (argumentos.isEmpty() || !NOME.equals(argumentos.get(0))) {
            return; // outro comando
        }
        if (argumentos.size() != 2) {
            throw new IllegalArgumentException("Uso: redefinir-2fa <email>");
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(argumentos.get(1))
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma conta com o e-mail " + argumentos.get(1)));
        boolean tinha = segundoFatorService.redefinir(usuario.getId());
        refreshTokenService.revogarTodas(usuario.getId());
        LOG.warn("Segundo fator do usuário {} redefinido pelo comando de linha", usuario.getId());
        System.out.printf(
                "%s: %s O próximo login pede o cadastro do aplicativo autenticador.%n",
                usuario.getEmail(), tinha ? "segundo fator apagado e sessões encerradas." : "não tinha segundo fator.");
    }
}

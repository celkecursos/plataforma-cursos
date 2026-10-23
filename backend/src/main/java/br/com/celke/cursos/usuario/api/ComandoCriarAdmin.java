package br.com.celke.cursos.usuario.api;

import br.com.celke.cursos.usuario.application.CriarAdminService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Comando {@code criar-admin <email> <nome>}: pede a senha duas vezes no terminal, sem eco.
 *
 * <pre>docker compose run --rm api criar-admin admin@celke.uno "Nome do Admin"</pre>
 */
@Component
@Profile(ComandoCriarAdmin.PERFIL)
public class ComandoCriarAdmin implements ApplicationRunner {

    public static final String NOME = "criar-admin";
    public static final String PERFIL = "comando";

    private final CriarAdminService criarAdminService;

    public ComandoCriarAdmin(CriarAdminService criarAdminService) {
        this.criarAdminService = criarAdminService;
    }

    /** Comandos de linha: o nome é o primeiro argumento. */
    private static final java.util.Set<String> COMANDOS = java.util.Set.of(NOME, "redefinir-2fa");

    /** Algum comando de linha (criar-admin, redefinir-2fa) foi pedido no primeiro argumento. */
    public static boolean foiPedido(String[] args) {
        return args.length > 0 && COMANDOS.contains(args[0]);
    }

    @Override
    public void run(ApplicationArguments args) {
        var argumentos = args.getNonOptionArgs();
        if (argumentos.isEmpty() || !NOME.equals(argumentos.get(0))) {
            return; // outro comando
        }
        if (argumentos.size() != 3) {
            throw new IllegalArgumentException("Uso: criar-admin <email> \"<nome>\"");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("O comando precisa de um terminal (docker compose run com TTY).");
        }
        char[] senha = console.readPassword("Senha do administrador: ");
        char[] confirmacao = console.readPassword("Repita a senha: ");
        try {
            if (!Arrays.equals(senha, confirmacao)) {
                throw new IllegalArgumentException("As senhas não conferem.");
            }
            Usuario admin = criarAdminService.criar(argumentos.get(2), argumentos.get(1), new String(senha));
            console.printf("Administrador %s criado (id %d).%n", admin.getEmail(), admin.getId());
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(confirmacao, '\0');
        }
    }
}

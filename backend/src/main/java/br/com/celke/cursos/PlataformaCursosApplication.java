package br.com.celke.cursos;

import br.com.celke.cursos.usuario.api.ComandoCriarAdmin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlataformaCursosApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PlataformaCursosApplication.class);
        if (ComandoCriarAdmin.foiPedido(args)) {
            // Comando de linha: sem servidor HTTP; termina ao fim do comando
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles(ComandoCriarAdmin.PERFIL);
            System.exit(SpringApplication.exit(app.run(args)));
        }
        app.run(args);
    }
}

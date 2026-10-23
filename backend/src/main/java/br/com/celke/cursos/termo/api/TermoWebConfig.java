package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.termo.application.TermoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga o {@link TermoPendenteInterceptor} às rotas de conteúdo do aluno. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TermoWebConfig implements WebMvcConfigurer {

    private final TermoService termoService;

    public TermoWebConfig(TermoService termoService) {
        this.termoService = termoService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TermoPendenteInterceptor(termoService))
                .addPathPatterns("/api/aluno/**", "/api/aulas/**");
    }
}

package br.com.celke.cursos.forum.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do fórum.
 *
 * @param mensagensPorJanela perguntas e respostas que um aluno pode publicar dentro da janela
 * @param janela período do limite de mensagens
 * @param anexosPorMensagem imagens por pergunta ou resposta
 */
@Validated
@ConfigurationProperties("app.forum")
public record ForumProperties(
        @Min(1) int mensagensPorJanela,
        @NotNull Duration janela,
        @Min(0) @Max(10) int anexosPorMensagem) {}

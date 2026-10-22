package br.com.celke.cursos.arquivo.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Corpos das rotas de arquivos de apoio do ADM. */
public final class ArquivoRequests {

    private ArquivoRequests() {}

    public record IniciarEnvio(
            @NotBlank String contentType, @Positive long tamanho) {}

    /** O nome do arquivo é limpo e ganha a extensão do formato real. */
    public record Confirmar(
            @NotBlank @Size(max = 200) String chave,
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 255) String nomeArquivo) {}
}

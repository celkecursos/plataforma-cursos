package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos das rotas de cadastro, confirmação e recuperação de senha. */
public final class ContaRequests {

    private ContaRequests() {}

    public record Cadastro(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 254) String email,

            @NotBlank @Size(min = PoliticaSenha.MINIMO_ALUNO, max = PoliticaSenha.MAXIMO)
            String senha) {}

    public record EmailInformado(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record Token(@NotBlank @Size(max = 100) String token) {}

    public record RedefinirSenha(
            @NotBlank @Size(max = 100) String token,

            @NotBlank @Size(min = PoliticaSenha.MINIMO_ALUNO, max = PoliticaSenha.MAXIMO)
            String senha) {}
}

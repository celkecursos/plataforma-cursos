package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.AuthService.Desafio;
import br.com.celke.cursos.auth.application.SegundoFatorService.Cadastro;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corpos e respostas da segunda etapa do login dos administradores. */
public final class SegundoFatorDtos {

    private SegundoFatorDtos() {}

    /** O que falta para entrar: {@code CODIGO} (aplicativo já cadastrado) ou {@code CADASTRO}. */
    public enum Etapa {
        CODIGO,
        CADASTRO
    }

    /** Resposta do login com a senha certa de um administrador: ainda sem sessão nem cookie. */
    public record DesafioResponse(Etapa segundoFator, String desafio, Instant expiraEm) {

        public static DesafioResponse de(Desafio desafio) {
            return new DesafioResponse(
                    desafio.cadastro() ? Etapa.CADASTRO : Etapa.CODIGO, desafio.token(), desafio.expiraEm());
        }
    }

    public record CadastroRequest(@NotBlank @Size(max = 100) String desafio) {}

    /** Segredo para digitar no aplicativo e o endereço do QR code. */
    public record CadastroResponse(String segredo, String uri) {

        public static CadastroResponse de(Cadastro cadastro) {
            return new CadastroResponse(cadastro.segredo(), cadastro.uri());
        }
    }

    /** O código do aplicativo ou, sem o celular, um código de recuperação. */
    public record ConfirmacaoRequest(
            @NotBlank @Size(max = 100) String desafio,
            @Size(max = 10) String codigo,
            @Size(max = 20) String codigoRecuperacao) {

        @AssertTrue(message = "Informe o código do aplicativo ou um código de recuperação.")
        public boolean isCodigoInformado() {
            return (codigo != null && !codigo.isBlank()) || (codigoRecuperacao != null && !codigoRecuperacao.isBlank());
        }
    }
}

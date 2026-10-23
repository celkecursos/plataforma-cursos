package br.com.celke.cursos.e2e.support;

/**
 * Conta própria de uma jornada, criada pelo caminho real (cadastro → e-mail → confirmação). Os
 * usuários do seed são só leitura: logout e sessões usam uma conta assim.
 */
public final class Contas {

    public static final String SENHA = "girassol-azul-da-jornada";

    private Contas() {}

    public static String criarConfirmada() {
        String email = Dados.email();
        Api.aluno()
                .body("{\"nome\":\"Conta e2e\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, SENHA))
                .post("/api/auth/cadastro")
                .then()
                .statusCode(202);
        String token = Mailpit.token(Mailpit.aguardar(email, "Confirme o seu e-mail"));
        Api.aluno()
                .body("{\"token\":\"%s\"}".formatted(token))
                .post("/api/auth/confirmar-email")
                .then()
                .statusCode(200);
        return email;
    }
}

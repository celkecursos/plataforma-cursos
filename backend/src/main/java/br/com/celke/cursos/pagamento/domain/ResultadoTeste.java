package br.com.celke.cursos.pagamento.domain;

/** Resultado do botão "Testar credenciais" do painel. */
public record ResultadoTeste(boolean ok, String mensagem) {

    public static ResultadoTeste ok(String mensagem) {
        return new ResultadoTeste(true, mensagem);
    }

    public static ResultadoTeste falha(String mensagem) {
        return new ResultadoTeste(false, mensagem);
    }
}

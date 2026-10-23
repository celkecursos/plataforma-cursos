package br.com.celke.cursos.e2e.support;

/**
 * Endereços da stack de e2e. Padrões iguais aos de {@code infra/e2e.env}; podem ser trocados por
 * propriedades do sistema ({@code -De2e.porta=...}).
 */
public final class E2eConfig {

    /** Porta do Caddy no host. Os sites são escolhidos pelo cabeçalho Host. */
    public static final int PORTA = Integer.getInteger("e2e.porta", 8088);

    public static final String MAILPIT = System.getProperty("e2e.mailpit", "http://localhost:8026");

    public static final String SENHA_SEED = System.getProperty("e2e.senha-seed", "123456A#b123");

    /** Segredo (Base32) do aplicativo autenticador do admin do seed ({@code SEED_E2E_TOTP}). */
    public static final String TOTP_SEED = System.getProperty("e2e.totp-seed", "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");

    public static final String HOST_ALUNO = "aluno.localhost";
    public static final String HOST_ADM = "adm.localhost";

    public static final String ADMIN_SEED = "admin@celke.com.br";
    public static final String ALUNO_SEED = "aluno@celke.com.br";

    private E2eConfig() {}

    /** Origem que o navegador envia para um host da plataforma ({@code http://aluno.localhost:8088}). */
    public static String origem(String host) {
        return "http://" + host + ":" + PORTA;
    }
}

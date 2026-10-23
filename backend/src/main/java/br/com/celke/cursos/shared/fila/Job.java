package br.com.celke.cursos.shared.fila;

/**
 * Job reservado para processamento.
 *
 * @param payload JSON com os dados do job, lido pelo manipulador do tipo
 * @param tentativas tentativas feitas, contando a atual
 */
public record Job(long id, String tipo, String payload, int tentativas, int maxTentativas) {

    /** Tentativa atual é a última: se falhar, o job vai para ERRO. */
    public boolean ultimaTentativa() {
        return tentativas >= maxTentativas;
    }

    /** Passou do limite: acontece quando o worker cai no meio do processamento várias vezes. */
    public boolean excedeuTentativas() {
        return tentativas > maxTentativas;
    }
}

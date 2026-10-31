package br.com.celke.cursos.arquivo.application;

/** A aula ou o módulo a que o arquivo pertence. */
public record DonoArquivo(Tipo tipo, Long id) {

    public enum Tipo {
        AULA,
        MODULO
    }

    public static DonoArquivo aula(Long id) {
        return new DonoArquivo(Tipo.AULA, id);
    }

    public static DonoArquivo modulo(Long id) {
        return new DonoArquivo(Tipo.MODULO, id);
    }

    /** Trecho da chave no bucket: {@code aulas/<id>} ou {@code modulos/<id>}. */
    public String pasta() {
        return (tipo == Tipo.AULA ? "aulas/" : "modulos/") + id;
    }
}

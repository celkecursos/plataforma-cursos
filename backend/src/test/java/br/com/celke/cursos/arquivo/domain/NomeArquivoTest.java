package br.com.celke.cursos.arquivo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NomeArquivoTest {

    @ParameterizedTest(name = "\"{0}\" ({1}) → {2}")
    @CsvSource({
        "codigo-fonte.zip,            ZIP,   codigo-fonte.zip",
        "Apostila Módulo 1.pdf,       PDF,   Apostila-Modulo-1.pdf",
        "../../etc/passwd,            TEXTO, passwd.txt",
        "C:\\Users\\ana\\aula.zip,    ZIP,   aula.zip",
        "relatorio.exe,               PDF,   relatorio.pdf",
        "sem extensao,                ZIP,   sem-extensao.zip",
        "'\"x\";filename=y.zip',      ZIP,   x-filename-y.zip",
        ".oculto,                     TEXTO, oculto.txt",
        "'   ',                       PDF,   arquivo.pdf",
        "%%%.zip,                     ZIP,   arquivo.zip",
    })
    @DisplayName("limpa o nome: sem caminho, sem acento, sem aspas, com a extensão do formato real")
    void nomes(String informado, FormatoArquivo formato, String esperado) {
        assertThat(NomeArquivo.de(informado, formato)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nome nulo vira arquivo")
    void nulo() {
        assertThat(NomeArquivo.de(null, FormatoArquivo.ZIP)).isEqualTo("arquivo.zip");
    }

    @Test
    @DisplayName("nome longo é cortado em 100 caracteres, mantendo a extensão")
    void longo() {
        String nome = NomeArquivo.de("a".repeat(300) + ".pdf", FormatoArquivo.PDF);

        assertThat(nome).hasSize(NomeArquivo.TAMANHO_MAXIMO).endsWith(".pdf");
    }
}

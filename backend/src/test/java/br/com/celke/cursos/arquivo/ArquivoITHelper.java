package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Envia arquivos de apoio pela API do ADM (URL pré-assinada → PUT no Garage → confirmação). */
final class ArquivoITHelper {

    private final CursoITHelper adm;
    private final HttpClient http = HttpClient.newHttpClient();

    ArquivoITHelper(CursoITHelper adm) {
        this.adm = adm;
    }

    /** Inicia o envio em {@code dono} ({@code /api/adm/aulas/<id>} ou {@code .../modulos/<id>}), faz o PUT e devolve a chave. */
    String enviar(String dono, byte[] conteudo, String contentType) throws Exception {
        MvcTestResult inicio = adm.enviar(
                "POST",
                dono + "/arquivos/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, conteudo.length));
        assertThat(inicio).hasStatusOk();
        String corpo = CursoITHelper.corpo(inicio);
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(conteudo))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    MvcTestResult confirmar(String dono, String chave, String titulo, String nomeArquivo) {
        return adm.enviar(
                "POST",
                dono + "/arquivos",
                "{\"chave\":\"%s\",\"titulo\":\"%s\",\"nomeArquivo\":\"%s\"}".formatted(chave, titulo, nomeArquivo));
    }

    /** Envia e confirma; confere o 201 e devolve o id do arquivo. */
    long publicar(String dono, byte[] conteudo, String contentType, String titulo, String nomeArquivo)
            throws Exception {
        return CursoITHelper.idCriado(confirmar(dono, enviar(dono, conteudo, contentType), titulo, nomeArquivo));
    }
}

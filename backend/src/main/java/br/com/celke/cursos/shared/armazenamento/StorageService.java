package br.com.celke.cursos.shared.armazenamento;

import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Acesso ao bucket da aplicação. O navegador envia e baixa arquivos direto do S3 por URLs
 * pré-assinadas; a API nunca recebe o conteúdo dos arquivos grandes.
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public StorageService(S3Client s3, S3Presigner presigner, ArmazenamentoProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.bucket();
    }

    /** URL para o navegador enviar um arquivo pequeno com um único PUT. */
    public URL urlUpload(String chave, String contentType, Duration validade) {
        return presigner
                .presignPutObject(p -> p.signatureDuration(validade)
                        .putObjectRequest(o -> o.bucket(bucket).key(chave).contentType(contentType)))
                .url();
    }

    /** URL para baixar um arquivo. */
    public URL urlDownload(String chave, Duration validade) {
        return presigner
                .presignGetObject(p -> p.signatureDuration(validade)
                        .getObjectRequest(o -> o.bucket(bucket).key(chave)))
                .url();
    }

    /** Inicia um upload em partes e devolve o id do upload. */
    public String iniciarMultipart(String chave, String contentType) {
        return s3.createMultipartUpload(r -> r.bucket(bucket).key(chave).contentType(contentType))
                .uploadId();
    }

    /** URL para o navegador enviar uma parte (a partir de 1). Toda parte, menos a última, tem ao menos 5 MB. */
    public URL urlParte(String chave, String uploadId, int numeroParte, Duration validade) {
        return presigner
                .presignUploadPart(p -> p.signatureDuration(validade)
                        .uploadPartRequest(u ->
                                u.bucket(bucket).key(chave).uploadId(uploadId).partNumber(numeroParte)))
                .url();
    }

    /** Junta as partes enviadas; o ETag de cada parte vem da resposta do PUT da parte. */
    public void concluirMultipart(String chave, String uploadId, List<ParteEnviada> partes) {
        List<CompletedPart> concluidas = partes.stream()
                .sorted(Comparator.comparingInt(ParteEnviada::numero))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.numero())
                        .eTag(p.etag())
                        .build())
                .toList();
        s3.completeMultipartUpload(
                r -> r.bucket(bucket).key(chave).uploadId(uploadId).multipartUpload(m -> m.parts(concluidas)));
    }

    public void abortarMultipart(String chave, String uploadId) {
        s3.abortMultipartUpload(r -> r.bucket(bucket).key(chave).uploadId(uploadId));
    }

    /** Tamanho e tipo do arquivo, ou vazio se ele não existe. */
    public Optional<InfoArquivo> info(String chave) {
        try {
            HeadObjectResponse head = s3.headObject(r -> r.bucket(bucket).key(chave));
            return Optional.of(new InfoArquivo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Conteúdo de um arquivo pequeno (imagem a processar); use {@link #info} antes para conferir o tamanho. */
    public byte[] baixar(String chave) {
        return s3.getObjectAsBytes(r -> r.bucket(bucket).key(chave)).asByteArray();
    }

    /** Grava um arquivo gerado pela própria aplicação (ex.: imagem processada). */
    public void gravar(String chave, byte[] conteudo, String contentType) {
        s3.putObject(r -> r.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromBytes(conteudo));
    }

    public void excluir(String chave) {
        s3.deleteObject(r -> r.bucket(bucket).key(chave));
    }

    public record ParteEnviada(int numero, String etag) {}

    public record InfoArquivo(long tamanho, String contentType) {}
}

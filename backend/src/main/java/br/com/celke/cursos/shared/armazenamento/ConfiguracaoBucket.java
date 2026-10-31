package br.com.celke.cursos.shared.armazenamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

/**
 * Aplica a configuração do bucket ao subir a API (idempotente): CORS para o front enviar e baixar
 * direto do bucket, e expiração de uploads abandonados. Fica no código para valer igual em todo
 * ambiente e em qualquer S3 compatível.
 */
@Component
@ConditionalOnWebApplication
public class ConfiguracaoBucket implements ApplicationRunner {

    static final String PREFIXO_UPLOADS = "uploads/";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoBucket.class);

    private final S3Client s3;
    private final ArmazenamentoProperties props;

    public ConfiguracaoBucket(S3Client s3, ArmazenamentoProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        aplicarCors();
        aplicarExpiracao();
    }

    private void aplicarCors() {
        if (props.corsOrigens().isEmpty()) {
            s3.deleteBucketCors(r -> r.bucket(props.bucket()));
            LOG.info("CORS do bucket {} removido (nenhuma origem configurada)", props.bucket());
            return;
        }
        CORSRule regra = CORSRule.builder()
                .allowedOrigins(props.corsOrigens())
                .allowedMethods("GET", "PUT", "HEAD")
                .allowedHeaders("*")
                // O ETag de cada parte é lido pelo navegador para concluir o upload em partes
                .exposeHeaders("ETag")
                .maxAgeSeconds(3600)
                .build();
        s3.putBucketCors(r -> r.bucket(props.bucket()).corsConfiguration(c -> c.corsRules(regra)));
        LOG.info("CORS do bucket {} aplicado para {}", props.bucket(), props.corsOrigens());
    }

    private void aplicarExpiracao() {
        LifecycleRule regra = LifecycleRule.builder()
                .id("expirar-uploads-abandonados")
                .status(ExpirationStatus.ENABLED)
                .filter(f -> f.prefix(PREFIXO_UPLOADS))
                .expiration(e -> e.days(props.diasUploadsAbandonados()))
                .abortIncompleteMultipartUpload(a -> a.daysAfterInitiation(1))
                .build();
        s3.putBucketLifecycleConfiguration(r -> r.bucket(props.bucket()).lifecycleConfiguration(l -> l.rules(regra)));
        LOG.info(
                "Uploads em {} expiram em {} dia(s) no bucket {}",
                PREFIXO_UPLOADS,
                props.diasUploadsAbandonados(),
                props.bucket());
    }
}

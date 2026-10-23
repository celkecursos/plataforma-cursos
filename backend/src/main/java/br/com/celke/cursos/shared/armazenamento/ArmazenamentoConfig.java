package br.com.celke.cursos.shared.armazenamento;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes do S3. O {@link S3Client} fala com o Garage pela rede interna; o {@link S3Presigner}
 * assina URLs com o endereço público, porque a assinatura inclui o host acessado pelo navegador.
 */
@Configuration
@EnableConfigurationProperties(ArmazenamentoProperties.class)
public class ArmazenamentoConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(ArmazenamentoProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.regiao()))
                .credentialsProvider(credenciais(props))
                .forcePathStyle(true)
                // Checksums só quando a operação exige: compatível com S3 que não é da AWS
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(ArmazenamentoProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpointPublico()))
                .region(Region.of(props.regiao()))
                .credentialsProvider(credenciais(props))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credenciais(ArmazenamentoProperties props) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}

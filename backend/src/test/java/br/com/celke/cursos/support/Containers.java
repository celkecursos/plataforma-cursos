package br.com.celke.cursos.support;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers dos testes de integração. Como todas as classes de teste compartilham o mesmo
 * contexto Spring, cada container sobe uma vez por execução.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Containers {

    public static final String S3_BUCKET = "midia";
    public static final String S3_ACCESS_KEY = "GK" + hex(12);
    public static final String S3_SECRET_KEY = hex(32);

    private static final String GARAGE_TOML = """
            metadata_dir = "/tmp/garage/meta"
            data_dir = "/tmp/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            """;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine");
    }

    /** Garage em servidor único, com o bucket e a chave da aplicação criados na subida. */
    @Bean
    GenericContainer<?> garage() {
        return new GenericContainer<>("dxflrs/garage:v2.4.1")
                .withCopyToContainer(Transferable.of(GARAGE_TOML), "/etc/garage.toml")
                .withEnv("GARAGE_RPC_SECRET", hex(32))
                .withEnv("GARAGE_DEFAULT_BUCKET", S3_BUCKET)
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", S3_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", S3_SECRET_KEY)
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withExposedPorts(3900)
                // O bucket e a chave são criados antes de a API S3 abrir
                .waitingFor(Wait.forLogMessage(".*S3 API server listening.*", 1));
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>("axllent/mailpit:v1.31.6")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/readyz").forPort(8025));
    }

    @Bean
    Mailpit mailpitApi(GenericContainer<?> mailpit) {
        return new Mailpit("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025));
    }

    /** Endereços dos containers sem conexão automática do Spring Boot. */
    @Bean
    DynamicPropertyRegistrar propriedadesContainers(GenericContainer<?> garage, GenericContainer<?> mailpit) {
        return registro -> {
            String s3 = "http://" + garage.getHost() + ":" + garage.getMappedPort(3900);
            registro.add("app.armazenamento.endpoint", () -> s3);
            registro.add("app.armazenamento.endpoint-publico", () -> s3);
            registro.add("app.armazenamento.bucket", () -> S3_BUCKET);
            registro.add("app.armazenamento.access-key", () -> S3_ACCESS_KEY);
            registro.add("app.armazenamento.secret-key", () -> S3_SECRET_KEY);
            registro.add("spring.mail.host", mailpit::getHost);
            registro.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
        };
    }

    private static String hex(int bytes) {
        byte[] aleatorios = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(aleatorios);
        return HexFormat.of().formatHex(aleatorios);
    }
}

package br.com.celke.cursos.support;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base de todos os testes de integração (*IT). Não altere para atender um teste específico:
 * qualquer variação cria outro contexto Spring e deixa a suíte lenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Containers.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    /** Identificador único em runtime para os dados criados pelo teste (prefixo {@code it-}). */
    protected static String stamp() {
        return "it-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }
}

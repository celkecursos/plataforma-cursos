package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.application.AulaAdmService;
import br.com.celke.cursos.curso.application.CursoAdmService;
import br.com.celke.cursos.curso.application.CursosExemplo;
import br.com.celke.cursos.curso.application.CursosExemplo.Exemplo;
import br.com.celke.cursos.curso.application.CursosExemplo.ModuloExemplo;
import br.com.celke.cursos.curso.application.ModuloAdmService;
import br.com.celke.cursos.curso.application.TurmaAdmService;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Cobre: o curso de exemplo da homologação sai publicado no catálogo, com capa no Garage, turma
 * ativa e só a primeira aula gratuita; rodar de novo pula o slug que já existe.
 * Não cobre: a lista fixa dos seis cursos (ligada só com CURSOS_EXEMPLO=true, fora dos testes).
 * Dado criado: curso it-<stamp> e a capa dele em capas/.
 */
class CursosExemploIT extends IntegrationTest {

    @Autowired
    private CursoRepository cursos;

    @Autowired
    private CursoAdmService cursoAdmService;

    @Autowired
    private TurmaAdmService turmaAdmService;

    @Autowired
    private ModuloAdmService moduloAdmService;

    @Autowired
    private AulaAdmService aulaAdmService;

    @Autowired
    private StorageService storage;

    @Test
    @DisplayName("cria o curso publicado com capa e pula o que já existe")
    void criaEPula() {
        var exemplos =
                new CursosExemplo(cursos, cursoAdmService, turmaAdmService, moduloAdmService, aulaAdmService, storage);
        String slug = stamp();
        var exemplo = new Exemplo(
                slug,
                "IT Exemplo " + slug,
                "Resumo do exemplo",
                "Descrição do exemplo",
                0x1f5fe0,
                0x6a3df0,
                8,
                List.of(new ModuloExemplo("Módulo", List.of("Primeira", "Segunda"))));

        assertThat(exemplos.criar(exemplo)).isTrue();

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"slug":"%s","titulo":"IT Exemplo %s",
                         "modulos":[{"titulo":"Módulo","aulas":[
                           {"titulo":"Primeira","gratuita":true},
                           {"titulo":"Segunda","gratuita":false}]}]}
                        """.formatted(slug, slug));
        String capa = cursos.findBySlugAndStatus(slug, StatusCurso.PUBLICADO)
                .orElseThrow()
                .getCapaChave();
        assertThat(capa).startsWith("capas/").endsWith(".jpg");
        assertThat(storage.info(capa)).isPresent();

        assertThat(exemplos.criar(exemplo)).isFalse();
        assertThat(mvc.get().uri("/api/cursos/" + slug)).hasStatus(HttpStatus.OK);
    }
}

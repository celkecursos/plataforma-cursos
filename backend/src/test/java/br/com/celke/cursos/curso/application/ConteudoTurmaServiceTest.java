package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConteudoTurmaServiceTest {

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    @Mock
    private CapaCursoService capaCursoService;

    private ConteudoTurmaService service;
    private final Turma turma = turma(5, publicado(curso(1, "java")));

    @BeforeEach
    void setUp() {
        service = new ConteudoTurmaService(turmas, modulos, aulas, capaCursoService);
    }

    @Test
    @DisplayName("resumo da turma traz os dados do curso e a URL da capa")
    void resumo() throws Exception {
        Turma comCapa = turma(6, comCapa(curso(2, "react"), "capas/2/a.jpg"));
        when(turmas.comCurso(List.of(6L))).thenReturn(List.of(comCapa));
        when(capaCursoService.url(comCapa.getCurso()))
                .thenReturn(Optional.of(URI.create("http://s3/capa").toURL()));

        var resumo = service.turma(6L).orElseThrow();

        assertThat(resumo.cursoSlug()).isEqualTo("react");
        assertThat(resumo.turmaStatus()).isEqualTo(StatusTurma.PREPARACAO);
        assertThat(resumo.cursoStatus()).isEqualTo(StatusCurso.RASCUNHO);
        assertThat(resumo.capaUrl()).isEqualTo("http://s3/capa");
    }

    @Test
    @DisplayName("turma inexistente fica fora do resultado")
    void turmaInexistente() {
        when(turmas.comCurso(List.of(9L))).thenReturn(List.of());

        assertThat(service.turma(9L)).isEmpty();
    }

    @Test
    @DisplayName("agrupa as aulas ativas por módulo, na ordem; módulo sem aulas vem com lista vazia")
    void agrupaAulas() {
        var primeiro = modulo(10, turma, 1);
        var segundo = modulo(20, turma, 2);
        when(modulos.findByTurmaIdAndStatusOrderByOrdem(5L, StatusConteudo.ATIVO))
                .thenReturn(List.of(primeiro, segundo));
        when(aulas.findByModuloIdInAndStatusOrderByOrdem(List.of(10L, 20L), StatusConteudo.ATIVO))
                .thenReturn(List.of(aula(100, primeiro, 1), aula(101, primeiro, 2)));

        var conteudo = service.conteudoAtivo(5L);

        assertThat(conteudo).hasSize(2);
        assertThat(conteudo.get(0).aulas()).extracting(AulaConteudo::id).containsExactly(100L, 101L);
        assertThat(conteudo.get(1).aulas()).isEmpty();
    }

    @ParameterizedTest(name = "aula {0}, módulo {1} → encontrada: {2}")
    @CsvSource({"ATIVO, ATIVO, true", "INATIVO, ATIVO, false", "ATIVO, INATIVO, false"})
    void aulaAtivaSoComAulaEModuloAtivos(StatusConteudo statusAula, StatusConteudo statusModulo, boolean encontrada) {
        var modulo = modulo(10, turma, 1);
        modulo.alterarStatus(statusModulo, turma.getCurso().getCriadoEm());
        Aula aula = aula(100, modulo, 1);
        aula.alterarStatus(statusAula, turma.getCurso().getCriadoEm());
        when(aulas.comHierarquia(100L)).thenReturn(Optional.of(aula));

        assertThat(service.aulaAtiva(100L).isPresent()).isEqualTo(encontrada);
    }

    @ParameterizedTest(name = "gratuita {0}, turma {1}, curso {2} → aberta: {3}")
    @CsvSource({
        "true,  ATIVA,      PUBLICADO, true",
        "false, ATIVA,      PUBLICADO, false",
        "true,  PREPARACAO, PUBLICADO, false",
        "true,  ENCERRADA,  PUBLICADO, false",
        "true,  ATIVA,      RASCUNHO,  false",
        "true,  ATIVA,      ARQUIVADO, false",
    })
    void aulaAberta(boolean gratuita, StatusTurma turma, StatusCurso curso, boolean aberta) {
        assertThat(new AulaContexto(1L, "Aula", null, gratuita, 5L, turma, curso).aberta())
                .isEqualTo(aberta);
    }
}

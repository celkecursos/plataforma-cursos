package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas ao conteúdo exportadas para os outros módulos (matrícula, vídeo, progresso...), que não
 * acessam os repositórios do curso. Devolve só dados prontos, sem entidades com carregamento tardio.
 * O conteúdo visível é sempre o ativo: módulo e aula {@code ATIVO}, na ordem.
 */
@Service
public class ConteudoTurmaService {

    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final CapaCursoService capaCursoService;

    public ConteudoTurmaService(
            TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas, CapaCursoService capaCursoService) {
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
        this.capaCursoService = capaCursoService;
    }

    @Transactional(readOnly = true)
    public Optional<TurmaResumo> turma(Long turmaId) {
        return turmas.comCurso(List.of(turmaId)).stream().findFirst().map(this::resumo);
    }

    /** Turmas indexadas pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, TurmaResumo> turmas(Collection<Long> turmaIds) {
        return turmas.comCurso(turmaIds).stream()
                .map(this::resumo)
                .collect(Collectors.toMap(TurmaResumo::turmaId, Function.identity()));
    }

    /** Módulos ativos da turma, cada um com as aulas ativas, na ordem. */
    @Transactional(readOnly = true)
    public List<ModuloConteudo> conteudoAtivo(Long turmaId) {
        List<Modulo> ativos = modulos.findByTurmaIdAndStatusOrderByOrdem(turmaId, StatusConteudo.ATIVO);
        Map<Long, List<Aula>> aulasPorModulo = aulas
                .findByModuloIdInAndStatusOrderByOrdem(
                        ativos.stream().map(Modulo::getId).toList(), StatusConteudo.ATIVO)
                .stream()
                .collect(Collectors.groupingBy(aula -> aula.getModulo().getId()));
        return ativos.stream()
                .map(modulo -> new ModuloConteudo(
                        modulo.getId(),
                        modulo.getTitulo(),
                        aulasPorModulo.getOrDefault(modulo.getId(), List.of()).stream()
                                .map(aula -> new AulaConteudo(aula.getId(), aula.getTitulo(), aula.isGratuita()))
                                .toList()))
                .toList();
    }

    /** Aula ativa de módulo ativo, com o que a política de acesso precisa; vazio se não existe ou está inativa. */
    @Transactional(readOnly = true)
    public Optional<AulaContexto> aulaAtiva(Long aulaId) {
        return aulas.comHierarquia(aulaId)
                .filter(aula -> aula.getStatus() == StatusConteudo.ATIVO
                        && aula.getModulo().getStatus() == StatusConteudo.ATIVO)
                .map(aula -> {
                    Turma turma = aula.getModulo().getTurma();
                    return new AulaContexto(
                            aula.getId(),
                            aula.getTitulo(),
                            aula.getDescricao(),
                            aula.isGratuita(),
                            turma.getId(),
                            turma.getStatus(),
                            turma.getCurso().getStatus());
                });
    }

    private TurmaResumo resumo(Turma turma) {
        Curso curso = turma.getCurso();
        return new TurmaResumo(
                turma.getId(),
                turma.getNome(),
                turma.getStatus(),
                curso.getId(),
                curso.getTitulo(),
                curso.getSlug(),
                curso.getStatus(),
                capaCursoService.url(curso).map(Object::toString).orElse(null));
    }

    /** Turma com os dados do curso; {@code capaUrl} é temporária (vale uma hora). */
    public record TurmaResumo(
            Long turmaId,
            String turmaNome,
            StatusTurma turmaStatus,
            Long cursoId,
            String cursoTitulo,
            String cursoSlug,
            StatusCurso cursoStatus,
            String capaUrl) {}

    public record ModuloConteudo(Long id, String titulo, List<AulaConteudo> aulas) {}

    public record AulaConteudo(Long id, String titulo, boolean gratuita) {}

    public record AulaContexto(
            Long id,
            String titulo,
            String descricao,
            boolean gratuita,
            Long turmaId,
            StatusTurma turmaStatus,
            StatusCurso cursoStatus) {

        /** Aula de degustação: gratuita, de turma ativa e de curso publicado (rascunho não vaza). */
        public boolean aberta() {
            return gratuita && turmaStatus == StatusTurma.ATIVA && cursoStatus == StatusCurso.PUBLICADO;
        }
    }
}

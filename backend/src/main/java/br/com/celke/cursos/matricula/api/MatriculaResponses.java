package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.matricula.application.AlunoCursosService.MeuCurso;
import br.com.celke.cursos.matricula.application.AlunoCursosService.TurmaDoAluno;
import br.com.celke.cursos.matricula.application.MatriculaAdmService.MatriculaComAluno;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import java.time.Instant;
import java.util.List;

/** Respostas das rotas de matrícula: ADM ({@code /api/adm}) e área do aluno ({@code /api/aluno}). */
public final class MatriculaResponses {

    private MatriculaResponses() {}

    public record MatriculaAdm(
            Long id,
            Long turmaId,
            Long usuarioId,
            String nome,
            String email,
            Instant inicio,
            Instant fim,
            StatusMatricula status,
            SituacaoMatricula situacao,
            FormaPagamento formaPagamento,
            String transacao) {

        public static MatriculaAdm de(MatriculaComAluno item, Instant agora) {
            Matricula m = item.matricula();
            return new MatriculaAdm(
                    m.getId(),
                    m.getTurmaId(),
                    m.getUsuarioId(),
                    item.usuario() == null ? null : item.usuario().getNome(),
                    item.usuario() == null ? null : item.usuario().getEmail(),
                    m.getInicio(),
                    m.getFim(),
                    m.getStatus(),
                    m.situacao(agora),
                    m.getFormaPagamento(),
                    m.getTransacao());
        }
    }

    /** Curso na lista do aluno; {@code capaUrl} é temporária (vale uma hora). */
    public record MeuCursoResponse(
            Long matriculaId,
            Long turmaId,
            String cursoTitulo,
            String cursoSlug,
            String turmaNome,
            String capaUrl,
            Instant inicio,
            Instant fim,
            SituacaoMatricula situacao,
            boolean arquivada) {

        public static MeuCursoResponse de(MeuCurso curso) {
            Matricula m = curso.matricula();
            return new MeuCursoResponse(
                    m.getId(),
                    m.getTurmaId(),
                    curso.turma().cursoTitulo(),
                    curso.turma().cursoSlug(),
                    curso.turma().turmaNome(),
                    curso.turma().capaUrl(),
                    m.getInicio(),
                    m.getFim(),
                    curso.situacao(),
                    m.isArquivada());
        }
    }

    public record TurmaAlunoResponse(
            Long matriculaId,
            Long turmaId,
            String turmaNome,
            String cursoTitulo,
            String cursoSlug,
            Instant inicio,
            Instant fim,
            List<ModuloAluno> modulos) {

        public static TurmaAlunoResponse de(TurmaDoAluno turma) {
            Matricula m = turma.matricula();
            return new TurmaAlunoResponse(
                    m.getId(),
                    m.getTurmaId(),
                    turma.turma().turmaNome(),
                    turma.turma().cursoTitulo(),
                    turma.turma().cursoSlug(),
                    m.getInicio(),
                    m.getFim(),
                    turma.modulos().stream().map(ModuloAluno::de).toList());
        }
    }

    public record ModuloAluno(Long id, String titulo, List<AulaResumo> aulas) {

        static ModuloAluno de(ModuloConteudo modulo) {
            return new ModuloAluno(
                    modulo.id(),
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaResumo::de).toList());
        }
    }

    public record AulaResumo(Long id, String titulo, boolean gratuita) {

        static AulaResumo de(AulaConteudo aula) {
            return new AulaResumo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    public record AulaAlunoResponse(Long id, Long turmaId, String titulo, String descricao, boolean gratuita) {

        public static AulaAlunoResponse de(AulaContexto aula) {
            return new AulaAlunoResponse(aula.id(), aula.turmaId(), aula.titulo(), aula.descricao(), aula.gratuita());
        }
    }
}

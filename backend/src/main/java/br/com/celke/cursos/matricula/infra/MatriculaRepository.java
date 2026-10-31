package br.com.celke.cursos.matricula.infra;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /** A matrícula ativa (vigente, futura ou vencida) do aluno na turma; o banco garante no máximo uma. */
    Optional<Matricula> findByUsuarioIdAndTurmaIdAndStatus(Long usuarioId, Long turmaId, StatusMatricula status);

    boolean existsByUsuarioIdAndTurmaIdAndStatus(Long usuarioId, Long turmaId, StatusMatricula status);

    List<Matricula> findByUsuarioIdAndStatus(Long usuarioId, StatusMatricula status);

    /** Matrícula do próprio aluno: a de outro aluno não é encontrada (404, sem revelar que existe). */
    Optional<Matricula> findByIdAndUsuarioId(Long id, Long usuarioId);

    Page<Matricula> findByTurmaId(Long turmaId, Pageable pageable);

    boolean existsByFormaPagamentoAndTransacao(FormaPagamento formaPagamento, String transacao);
}

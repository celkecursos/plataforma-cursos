package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TermoRepository extends JpaRepository<Termo, Long> {

    /** A maior versão de cada tipo. */
    @Query("""
            select t from Termo t
             where t.versao = (select max(t2.versao) from Termo t2 where t2.tipo = t.tipo)
             order by t.tipo
            """)
    List<Termo> vigentes();

    /** Versões vigentes que o usuário ainda não aceitou. */
    @Query("""
            select t from Termo t
             where t.versao = (select max(t2.versao) from Termo t2 where t2.tipo = t.tipo)
               and not exists (select a from AceiteTermo a where a.termoId = t.id and a.usuarioId = :usuarioId)
             order by t.tipo
            """)
    List<Termo> pendentes(Long usuarioId);

    Optional<Termo> findFirstByTipoOrderByVersaoDesc(TipoTermo tipo);

    List<Termo> findByTipoOrderByVersaoDesc(TipoTermo tipo);
}

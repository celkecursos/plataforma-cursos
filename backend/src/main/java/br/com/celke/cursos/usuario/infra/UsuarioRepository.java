package br.com.celke.cursos.usuario.infra;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Lista do ADM: busca por nome ou e-mail (sem diferenciar maiúsculas), papel e situação. */
    @Query("""
            SELECT DISTINCT u FROM Usuario u LEFT JOIN u.papeis p
             WHERE (:busca IS NULL OR LOWER(u.nome) LIKE :busca OR u.email LIKE :busca)
               AND (:papel IS NULL OR p = :papel)
               AND (:ativo IS NULL OR u.ativo = :ativo)
            """)
    Page<Usuario> buscar(String busca, Papel papel, Boolean ativo, Pageable pagina);
}

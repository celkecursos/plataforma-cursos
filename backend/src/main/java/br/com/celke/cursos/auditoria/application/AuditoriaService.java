package br.com.celke.cursos.auditoria.application;

import br.com.celke.cursos.auditoria.domain.RegistroAuditoria;
import br.com.celke.cursos.auditoria.infra.RegistroAuditoriaRepository;
import br.com.celke.cursos.auditoria.infra.RetencaoAuditoriaJdbc;
import br.com.celke.cursos.shared.auditoria.OperacaoAuditoria;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta da trilha de auditoria pelo ADM e a retenção. */
@Service
@EnableConfigurationProperties(AuditoriaProperties.class)
public class AuditoriaService {

    private final RegistroAuditoriaRepository registros;
    private final RetencaoAuditoriaJdbc retencao;
    private final UsuarioService usuarioService;
    private final AuditoriaProperties props;
    private final Clock clock;

    public AuditoriaService(
            RegistroAuditoriaRepository registros,
            RetencaoAuditoriaJdbc retencao,
            UsuarioService usuarioService,
            AuditoriaProperties props,
            Clock clock) {
        this.registros = registros;
        this.retencao = retencao;
        this.usuarioService = usuarioService;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Registros mais recentes primeiro. O filtro por e-mail procura o usuário; e-mail sem conta não
     * encontra nada.
     */
    @Transactional(readOnly = true)
    public Page<RegistroComUsuario> listar(FiltroAuditoria filtro, Pageable pagina) {
        PageRequest ordenada =
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "em", "id"));
        Long usuarioId = filtro.usuarioId();
        if (filtro.email() != null && !filtro.email().isBlank()) {
            Optional<Usuario> usuario = usuarioService.buscarPorEmail(filtro.email());
            if (usuario.isEmpty()) {
                return Page.empty(ordenada);
            }
            usuarioId = usuario.get().getId();
        }
        Page<RegistroAuditoria> page = registros.findAll(especificacao(filtro, usuarioId), ordenada);
        Map<Long, Usuario> usuarios = usuarioService.buscarPorIds(page.getContent().stream()
                .map(RegistroAuditoria::getUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return page.map(
                r -> new RegistroComUsuario(r, r.getUsuarioId() == null ? null : usuarios.get(r.getUsuarioId())));
    }

    @Transactional(readOnly = true)
    public RegistroComUsuario buscar(Long id) {
        RegistroAuditoria registro =
                registros.findById(id).orElseThrow(() -> new NaoEncontradoException("Registro não encontrado."));
        Usuario usuario = registro.getUsuarioId() == null
                ? null
                : usuarioService.buscarPorId(registro.getUsuarioId()).orElse(null);
        return new RegistroComUsuario(registro, usuario);
    }

    @Transactional(readOnly = true)
    public List<String> entidades() {
        return registros.entidades();
    }

    /** Apaga o que passou da retenção; devolve quantos registros saíram. */
    public int aplicarRetencao() {
        return retencao.apagarAnteriores(clock.instant().minus(props.retencao()));
    }

    private static Specification<RegistroAuditoria> especificacao(FiltroAuditoria filtro, Long usuarioId) {
        Specification<RegistroAuditoria> spec = Specification.unrestricted();
        if (usuarioId != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("usuarioId"), usuarioId));
        }
        if (filtro.entidade() != null && !filtro.entidade().isBlank()) {
            spec = spec.and((raiz, consulta, cb) ->
                    cb.equal(raiz.get("entidade"), filtro.entidade().strip()));
        }
        if (filtro.entidadeId() != null && !filtro.entidadeId().isBlank()) {
            spec = spec.and((raiz, consulta, cb) ->
                    cb.equal(raiz.get("entidadeId"), filtro.entidadeId().strip()));
        }
        if (filtro.operacao() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("operacao"), filtro.operacao()));
        }
        if (filtro.de() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("em"), filtro.de()));
        }
        if (filtro.ate() != null) {
            spec = spec.and((raiz, consulta, cb) -> cb.lessThan(raiz.get("em"), filtro.ate()));
        }
        return spec;
    }

    /** Filtros opcionais: usuário (id ou e-mail), entidade e id, operação e período ({@code ate} exclusivo). */
    public record FiltroAuditoria(
            Long usuarioId,
            String email,
            String entidade,
            String entidadeId,
            OperacaoAuditoria operacao,
            Instant de,
            Instant ate) {}

    /** @param usuario nulo quando a ação foi do sistema ou o usuário não existe mais */
    public record RegistroComUsuario(RegistroAuditoria registro, Usuario usuario) {}
}

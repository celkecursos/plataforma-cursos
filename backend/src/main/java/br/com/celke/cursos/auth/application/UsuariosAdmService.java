package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão das contas pelo ADM: bloquear, desbloquear e encerrar as sessões. Bloquear e encerrar revogam
 * os refresh tokens e invalidam na hora os access tokens já emitidos ({@link SessaoValidaValidator}).
 * As mudanças ficam na trilha de auditoria (a entidade do usuário é auditada).
 */
@Service
public class UsuariosAdmService {

    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final SegundoFatorService segundoFatorService;
    private final SessaoValidaValidator sessoes;

    public UsuariosAdmService(
            UsuarioService usuarioService,
            RefreshTokenService refreshTokenService,
            SegundoFatorService segundoFatorService,
            SessaoValidaValidator sessoes) {
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.segundoFatorService = segundoFatorService;
        this.sessoes = sessoes;
    }

    public Page<Usuario> listar(String busca, Papel papel, Boolean ativo, Pageable pagina) {
        return usuarioService.listar(busca, papel, ativo, pagina);
    }

    public boolean segundoFatorAtivo(Long usuarioId) {
        return segundoFatorService.ativo(usuarioId);
    }

    @Transactional
    public Usuario alterarSituacao(Long id, boolean ativo, Long adminId) {
        if (!ativo && id.equals(adminId)) {
            throw new RegraNegocioException(HttpStatus.CONFLICT, "Você não pode bloquear a própria conta.");
        }
        Usuario usuario = ativo ? usuarioService.desbloquear(id) : usuarioService.bloquear(id);
        if (!ativo) {
            refreshTokenService.revogarTodas(id);
        }
        sessoes.esquecer(id);
        return usuario;
    }

    @Transactional
    public Usuario encerrarSessoes(Long id) {
        Usuario usuario = usuarioService.encerrarSessoes(id);
        refreshTokenService.revogarTodas(id);
        sessoes.esquecer(id);
        return usuario;
    }
}

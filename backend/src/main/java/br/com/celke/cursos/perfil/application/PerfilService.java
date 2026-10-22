package br.com.celke.cursos.perfil.application;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil do próprio usuário: nome, senha (exige a atual e encerra todas as sessões) e foto, enviada
 * direto ao bucket e regravada em JPEG sem metadados (até 400 × 400).
 */
@Service
public class PerfilService {

    public static final long TAMANHO_MAXIMO_FOTO = 5L * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");
    static final int LADO_FOTO = 400;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(10);
    static final Duration VALIDADE_FOTO = Duration.ofHours(1);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final StorageService storage;
    private final ProcessadorImagem processador;
    private final Clock clock;

    public PerfilService(
            UsuarioService usuarioService,
            RefreshTokenService refreshTokenService,
            StorageService storage,
            ProcessadorImagem processador,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.refreshTokenService = refreshTokenService;
        this.storage = storage;
        this.processador = processador;
        this.clock = clock;
    }

    public Usuario buscar(Long usuarioId) {
        return usuarioService.buscarPorId(usuarioId).orElseThrow(PerfilService::semUsuario);
    }

    public Usuario renomear(Long usuarioId, String nome) {
        buscar(usuarioId);
        return usuarioService.renomear(usuarioId, nome);
    }

    /**
     * Troca a senha conferindo a atual e a política do papel (admin exige mais caracteres). Todas as
     * sessões são encerradas: o usuário entra de novo com a senha nova.
     */
    @Transactional
    public void trocarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = buscar(usuarioId);
        if (!usuarioService.senhaConfere(usuario, senhaAtual)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A senha atual não confere.");
        }
        int minimo =
                usuario.getPapeis().contains(Papel.ADMIN) ? PoliticaSenha.MINIMO_ADMIN : PoliticaSenha.MINIMO_ALUNO;
        String problema = PoliticaSenha.validar(novaSenha, minimo);
        if (problema != null) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, problema);
        }
        if (usuarioService.senhaConfere(usuario, novaSenha)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A nova senha precisa ser diferente da atual.");
        }
        usuarioService.trocarSenha(usuario, novaSenha);
        refreshTokenService.revogarTodas(usuarioId);
    }

    /** URL para o navegador enviar a foto, com o mesmo Content-Type informado aqui. */
    public EnvioFoto iniciarEnvioFoto(Long usuarioId, String contentType, long tamanho) {
        if (!TIPOS.contains(contentType)) {
            throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
        }
        if (tamanho > TAMANHO_MAXIMO_FOTO) {
            throw tamanhoExcedido();
        }
        String chave = prefixoUpload(usuarioId) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, contentType, VALIDADE_UPLOAD);
        return new EnvioFoto(chave, url, contentType, clock.instant().plus(VALIDADE_UPLOAD));
    }

    /** Confere a imagem enviada pelo próprio usuário, grava a versão processada e apaga a anterior. */
    public Usuario confirmarFoto(Long usuarioId, String chave) {
        buscar(usuarioId);
        String prefixo = prefixoUpload(usuarioId);
        if (chave == null
                || !chave.startsWith(prefixo)
                || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
            throw new ImagemInvalidaException("Envio de foto inválido. Comece um novo envio.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new ImagemInvalidaException("A foto não foi enviada. Comece um novo envio."));
        byte[] processada;
        try {
            if (info.tamanho() > TAMANHO_MAXIMO_FOTO) {
                throw tamanhoExcedido();
            }
            processada = processador.normalizar(storage.baixar(chave), LADO_FOTO, LADO_FOTO);
        } finally {
            storage.excluir(chave);
        }
        String foto = "fotos/%d/%s.jpg".formatted(usuarioId, UUID.randomUUID());
        storage.gravar(foto, processada, "image/jpeg");
        try {
            usuarioService.trocarFoto(usuarioId, foto).ifPresent(storage::excluir);
        } catch (RuntimeException e) {
            storage.excluir(foto);
            throw e;
        }
        return buscar(usuarioId);
    }

    public void removerFoto(Long usuarioId) {
        buscar(usuarioId);
        usuarioService.trocarFoto(usuarioId, null).ifPresent(storage::excluir);
    }

    /** URL temporária da foto, ou vazio sem foto. */
    public Optional<URL> urlFoto(Usuario usuario) {
        return Optional.ofNullable(usuario.getFotoChave()).map(chave -> storage.urlDownload(chave, VALIDADE_FOTO));
    }

    private static String prefixoUpload(Long usuarioId) {
        return "uploads/fotos/%d/".formatted(usuarioId);
    }

    private static ImagemInvalidaException tamanhoExcedido() {
        return new ImagemInvalidaException("A foto pode ter no máximo 5 MB.");
    }

    private static RegraNegocioException semUsuario() {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente.");
    }

    public record EnvioFoto(String chave, URL url, String contentType, Instant expiraEm) {}
}

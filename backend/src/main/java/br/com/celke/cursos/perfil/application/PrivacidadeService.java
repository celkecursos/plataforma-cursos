package br.com.celke.cursos.perfil.application;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.auth.application.SessaoValidaValidator;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Direitos do titular (LGPD, art. 18): a cópia de todos os dados da conta e a exclusão da conta. Cada
 * módulo responde pelas próprias tabelas ({@link DadosPessoais}); o que a lei manda guardar fica, sem
 * nome nem e-mail, até a retenção apagar.
 */
@Service
public class PrivacidadeService {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacidadeService.class);

    private final UsuarioService usuarioService;
    private final List<DadosPessoais> fontes;
    private final RefreshTokenService refreshTokenService;
    private final SessaoValidaValidator sessoes;
    private final StorageService storage;
    private final EmailService emailService;
    private final Clock clock;

    public PrivacidadeService(
            UsuarioService usuarioService,
            List<DadosPessoais> fontes,
            RefreshTokenService refreshTokenService,
            SessaoValidaValidator sessoes,
            StorageService storage,
            EmailService emailService,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.fontes = fontes;
        this.refreshTokenService = refreshTokenService;
        this.sessoes = sessoes;
        this.storage = storage;
        this.emailService = emailService;
        this.clock = clock;
    }

    /** Todos os dados da conta, por seção (conta, matrículas, progresso, fórum, pedidos...). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Long usuarioId) {
        Usuario usuario = buscar(usuarioId);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", clock.instant().toString());
        dados.put("conta", conta(usuario));
        fontes.stream()
                .sorted((a, b) -> a.secao().compareTo(b.secao()))
                .forEach(fonte -> dados.put(fonte.secao(), fonte.exportar(usuarioId)));
        return dados;
    }

    /**
     * Exclui a conta: confere a senha, avisa por e-mail, apaga ou anonimiza os dados de cada módulo,
     * anonimiza a conta e encerra as sessões. Administrador não se exclui por aqui (a plataforma ficaria
     * sem quem a administra).
     */
    @Transactional
    public void excluirConta(Long usuarioId, String senha) {
        Usuario usuario = buscar(usuarioId);
        if (usuario.getPapeis().contains(Papel.ADMIN)) {
            throw new RegraNegocioException(
                    HttpStatus.CONFLICT,
                    "Contas de administrador não são excluídas por aqui. Fale com outro administrador.");
        }
        if (!usuarioService.senhaConfere(usuario, senha)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A senha não confere.");
        }
        // O aviso vai para o e-mail de antes da anonimização (a fila guarda o destinatário)
        emailService.enviar(
                usuario.getEmail(), "Sua conta foi excluída", "conta-excluida", Map.of("nome", usuario.getNome()));
        fontes.forEach(fonte -> fonte.excluir(usuarioId));
        refreshTokenService.revogarTodas(usuarioId);
        usuarioService.anonimizar(usuarioId).ifPresent(this::apagarDepoisDoCommit);
        // O access token já emitido deixa de valer na hora
        sessoes.esquecer(usuarioId);
        LOG.info("Conta {} excluída a pedido do titular", usuarioId);
    }

    private Map<String, Object> conta(Usuario usuario) {
        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("id", usuario.getId());
        conta.put("nome", usuario.getNome());
        conta.put("email", usuario.getEmail());
        conta.put("papeis", usuario.getPapeis().stream().sorted().toList());
        conta.put("criadaEm", usuario.getCriadoEm().toString());
        conta.put(
                "emailConfirmadoEm",
                usuario.getEmailConfirmadoEm() == null
                        ? null
                        : usuario.getEmailConfirmadoEm().toString());
        conta.put("temFoto", usuario.getFotoChave() != null);
        return conta;
    }

    /** A foto sai do bucket só se a exclusão for gravada. */
    private void apagarDepoisDoCommit(String chave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.excluir(chave);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.excluir(chave);
            }
        });
    }

    private Usuario buscar(Long usuarioId) {
        return usuarioService
                .buscarPorId(usuarioId)
                .orElseThrow(() -> new NaoEncontradoException("Conta não encontrada."));
    }
}

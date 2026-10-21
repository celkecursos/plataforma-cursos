package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Capa do curso em duas etapas: o navegador envia a imagem direto ao bucket por uma URL
 * pré-assinada e a API, na confirmação, confere e normaliza o arquivo. Só a imagem processada
 * (JPEG sem metadados, até 1280 × 720) fica em {@code capas/}; o original é apagado.
 */
@Service
public class CapaCursoService {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");
    static final int LARGURA = 1280;
    static final int ALTURA = 720;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(10);
    static final Duration VALIDADE_DOWNLOAD = Duration.ofHours(1);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final CursoAdmService cursoAdmService;
    private final StorageService storage;
    private final ProcessadorImagem processador;
    private final Clock clock;

    public CapaCursoService(
            CursoAdmService cursoAdmService, StorageService storage, ProcessadorImagem processador, Clock clock) {
        this.cursoAdmService = cursoAdmService;
        this.storage = storage;
        this.processador = processador;
        this.clock = clock;
    }

    /** URL para o navegador enviar a imagem, com o mesmo Content-Type informado aqui. */
    public EnvioCapa iniciarEnvio(Long cursoId, String contentType, long tamanho) {
        cursoAdmService.buscar(cursoId);
        if (!TIPOS.contains(contentType)) {
            throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
        }
        if (tamanho > TAMANHO_MAXIMO) {
            throw tamanhoExcedido();
        }
        String chave = prefixoUpload(cursoId) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, contentType, VALIDADE_UPLOAD);
        return new EnvioCapa(chave, url, contentType, clock.instant().plus(VALIDADE_UPLOAD));
    }

    /**
     * Confere o arquivo enviado, grava a versão processada como capa e apaga o original e a capa
     * anterior. Só aceita chaves geradas por {@link #iniciarEnvio} para o mesmo curso.
     */
    public Curso confirmar(Long cursoId, String chave) {
        cursoAdmService.buscar(cursoId);
        String prefixo = prefixoUpload(cursoId);
        if (chave == null
                || !chave.startsWith(prefixo)
                || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
            throw new ImagemInvalidaException("Envio de capa inválido. Comece um novo envio.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new ImagemInvalidaException("A imagem não foi enviada. Comece um novo envio."));
        byte[] processada;
        try {
            if (info.tamanho() > TAMANHO_MAXIMO) {
                throw tamanhoExcedido();
            }
            processada = processador.normalizar(storage.baixar(chave), LARGURA, ALTURA);
        } finally {
            storage.excluir(chave);
        }
        String capa = "capas/%d/%s.jpg".formatted(cursoId, UUID.randomUUID());
        storage.gravar(capa, processada, "image/jpeg");
        try {
            cursoAdmService.trocarCapa(cursoId, capa).ifPresent(storage::excluir);
        } catch (RuntimeException e) {
            storage.excluir(capa);
            throw e;
        }
        return cursoAdmService.buscar(cursoId);
    }

    public void remover(Long cursoId) {
        cursoAdmService.trocarCapa(cursoId, null).ifPresent(storage::excluir);
    }

    /** Exclui o curso (só sem turmas) e a capa dele no bucket. */
    public void excluirCurso(Long cursoId) {
        cursoAdmService.excluir(cursoId).ifPresent(storage::excluir);
    }

    /** URL temporária para exibir a capa, ou vazio sem capa. */
    public Optional<URL> url(Curso curso) {
        return Optional.ofNullable(curso.getCapaChave()).map(chave -> storage.urlDownload(chave, VALIDADE_DOWNLOAD));
    }

    private static String prefixoUpload(Long cursoId) {
        return "uploads/capas/%d/".formatted(cursoId);
    }

    private static ImagemInvalidaException tamanhoExcedido() {
        return new ImagemInvalidaException("A imagem pode ter no máximo 5 MB.");
    }

    public record EnvioCapa(String chave, URL url, String contentType, Instant expiraEm) {}
}

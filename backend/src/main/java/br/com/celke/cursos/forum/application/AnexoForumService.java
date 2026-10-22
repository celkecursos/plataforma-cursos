package br.com.celke.cursos.forum.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Imagens anexadas a perguntas e respostas, no mesmo esquema da capa do curso: o navegador envia direto
 * ao bucket em {@code uploads/forum/<usuario>/<uuid>} e, ao publicar a mensagem, a API confere e regrava a
 * imagem (JPEG sem metadados, até 1600 × 1600) em {@code forum/anexos/}. Cada usuário só anexa o que ele
 * mesmo enviou: a chave traz o id dele (contra IDOR).
 */
@Service
@EnableConfigurationProperties(ForumProperties.class)
public class AnexoForumService {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("image/jpeg", "image/png");
    static final int LADO_MAXIMO = 1600;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(10);
    static final Duration VALIDADE_DOWNLOAD = Duration.ofMinutes(10);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final StorageService storage;
    private final ProcessadorImagem processador;
    private final ForumProperties props;
    private final Clock clock;

    public AnexoForumService(
            StorageService storage, ProcessadorImagem processador, ForumProperties props, Clock clock) {
        this.storage = storage;
        this.processador = processador;
        this.props = props;
        this.clock = clock;
    }

    /** URL para o navegador enviar a imagem, com o mesmo Content-Type informado aqui. */
    public EnvioAnexo iniciarEnvio(Long usuarioId, String contentType, long tamanho) {
        if (!TIPOS.contains(contentType)) {
            throw new ImagemInvalidaException("Anexe uma imagem JPEG ou PNG.");
        }
        if (tamanho > TAMANHO_MAXIMO) {
            throw tamanhoExcedido();
        }
        String chave = prefixo(usuarioId) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, contentType, VALIDADE_UPLOAD);
        return new EnvioAnexo(chave, url, contentType, clock.instant().plus(VALIDADE_UPLOAD));
    }

    /**
     * Confere e processa as imagens enviadas pelo usuário; devolve onde ficaram. Qualquer recusa desfaz
     * as imagens já gravadas desta mensagem. Os envios originais são sempre apagados.
     */
    public List<AnexoGravado> processar(Long usuarioId, List<String> chaves) {
        if (chaves == null || chaves.isEmpty()) {
            return List.of();
        }
        if (chaves.size() > props.anexosPorMensagem()) {
            throw new ImagemInvalidaException("Anexe no máximo %d imagens.".formatted(props.anexosPorMensagem()));
        }
        if (new HashSet<>(chaves).size() != chaves.size()) {
            throw new ImagemInvalidaException("A mesma imagem foi anexada duas vezes.");
        }
        String prefixo = prefixo(usuarioId);
        for (String chave : chaves) {
            if (chave == null
                    || !chave.startsWith(prefixo)
                    || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
                throw new ImagemInvalidaException("Anexo inválido. Envie a imagem de novo.");
            }
        }
        List<AnexoGravado> gravados = new ArrayList<>();
        try {
            for (String chave : chaves) {
                gravados.add(gravar(chave));
            }
            return gravados;
        } catch (RuntimeException e) {
            gravados.forEach(g -> storage.excluir(g.chave()));
            throw e;
        }
    }

    /** Apaga imagens já gravadas (a mensagem não chegou a ser salva). */
    public void descartar(List<AnexoGravado> gravados) {
        gravados.forEach(g -> storage.excluir(g.chave()));
    }

    public URL url(String chave) {
        return storage.urlDownload(chave, VALIDADE_DOWNLOAD);
    }

    private AnexoGravado gravar(String upload) {
        var info = storage.info(upload)
                .orElseThrow(() -> new ImagemInvalidaException("A imagem não foi enviada. Envie a imagem de novo."));
        byte[] processada;
        try {
            if (info.tamanho() > TAMANHO_MAXIMO) {
                throw tamanhoExcedido();
            }
            processada = processador.normalizar(storage.baixar(upload), LADO_MAXIMO, LADO_MAXIMO);
        } finally {
            storage.excluir(upload);
        }
        String chave = "forum/anexos/%s.jpg".formatted(UUID.randomUUID());
        storage.gravar(chave, processada, "image/jpeg");
        return new AnexoGravado(chave, processada.length);
    }

    private static String prefixo(Long usuarioId) {
        return "uploads/forum/%d/".formatted(usuarioId);
    }

    private static ImagemInvalidaException tamanhoExcedido() {
        return new ImagemInvalidaException("A imagem pode ter no máximo 5 MB.");
    }

    public record EnvioAnexo(String chave, URL url, String contentType, Instant expiraEm) {}

    public record AnexoGravado(String chave, long tamanho) {}
}

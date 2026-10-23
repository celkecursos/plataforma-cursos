package br.com.celke.cursos.arquivo.application;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.domain.FormatoArquivo;
import br.com.celke.cursos.arquivo.domain.NomeArquivo;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Arquivos de apoio pelo ADM, em duas etapas como a capa do curso: o navegador envia direto ao bucket
 * por uma URL pré-assinada e a API, na confirmação, confere o arquivo real e o move de {@code uploads/}
 * (que expira) para {@code arquivos/}. Recusado, o envio é apagado.
 */
@Service
public class ArquivoAdmService {

    public static final long TAMANHO_MAXIMO = 200L * 1024 * 1024;
    static final Duration VALIDADE_UPLOAD = Duration.ofMinutes(15);

    private static final Pattern UUID_FORMATO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final ArquivoApoioRepository arquivos;
    private final ConteudoTurmaService conteudoTurmaService;
    private final StorageService storage;
    private final Clock clock;

    public ArquivoAdmService(
            ArquivoApoioRepository arquivos,
            ConteudoTurmaService conteudoTurmaService,
            StorageService storage,
            Clock clock) {
        this.arquivos = arquivos;
        this.conteudoTurmaService = conteudoTurmaService;
        this.storage = storage;
        this.clock = clock;
    }

    public List<ArquivoApoio> listar(DonoArquivo dono) {
        exigirDono(dono);
        return switch (dono.tipo()) {
            case AULA -> arquivos.findByAulaIdOrderById(dono.id());
            case MODULO -> arquivos.findByModuloIdOrderById(dono.id());
        };
    }

    /** URL para o navegador enviar o arquivo, com o mesmo Content-Type informado aqui. */
    public EnvioArquivo iniciarEnvio(DonoArquivo dono, String contentType, long tamanho) {
        exigirDono(dono);
        FormatoArquivo formato =
                FormatoArquivo.doContentType(contentType).orElseThrow(ArquivoAdmService::formatoRecusado);
        if (tamanho > TAMANHO_MAXIMO) {
            throw tamanhoExcedido();
        }
        String chave = prefixoUpload(dono) + UUID.randomUUID();
        URL url = storage.urlUpload(chave, formato.contentType(), VALIDADE_UPLOAD);
        return new EnvioArquivo(
                chave, url, formato.contentType(), clock.instant().plus(VALIDADE_UPLOAD));
    }

    /**
     * Confere o arquivo enviado (chave gerada para o mesmo dono, tamanho, conteúdo do formato), move para
     * {@code arquivos/} e registra. Qualquer recusa apaga o envio.
     */
    public ArquivoApoio confirmar(DonoArquivo dono, String chave, String titulo, String nomeArquivo) {
        exigirDono(dono);
        String prefixo = prefixoUpload(dono);
        if (chave == null
                || !chave.startsWith(prefixo)
                || !UUID_FORMATO.matcher(chave.substring(prefixo.length())).matches()) {
            throw new ArquivoInvalidoException("Envio de arquivo inválido. Comece um novo envio.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new ArquivoInvalidoException("O arquivo não foi enviado. Comece um novo envio."));
        FormatoArquivo formato;
        String destino = "arquivos/%s/%s".formatted(dono.pasta(), chave.substring(prefixo.length()));
        try {
            formato = FormatoArquivo.doContentType(info.contentType()).orElseThrow(ArquivoAdmService::formatoRecusado);
            if (info.tamanho() <= 0) {
                throw new ArquivoInvalidoException("O arquivo está vazio.");
            }
            if (info.tamanho() > TAMANHO_MAXIMO) {
                throw tamanhoExcedido();
            }
            if (!formato.conteudoConfere(storage.baixarInicio(chave, FormatoArquivo.BYTES_CONFERENCIA))) {
                throw new ArquivoInvalidoException("O conteúdo do arquivo não confere com o tipo informado.");
            }
            storage.copiar(chave, destino);
        } finally {
            storage.excluir(chave);
        }
        Instant agora = clock.instant();
        String nome = NomeArquivo.de(nomeArquivo, formato);
        ArquivoApoio arquivo = switch (dono.tipo()) {
            case AULA -> ArquivoApoio.daAula(dono.id(), titulo, nome, destino, formato, info.tamanho(), agora);
            case MODULO -> ArquivoApoio.doModulo(dono.id(), titulo, nome, destino, formato, info.tamanho(), agora);
        };
        try {
            return arquivos.save(arquivo);
        } catch (RuntimeException e) {
            storage.excluir(destino);
            throw e;
        }
    }

    /** Remove o registro e o arquivo do bucket. */
    public void excluir(Long arquivoId) {
        ArquivoApoio arquivo = arquivos.findById(arquivoId).orElseThrow(ArquivoAdmService::naoEncontrado);
        arquivos.delete(arquivo);
        storage.excluir(arquivo.getChave());
    }

    private void exigirDono(DonoArquivo dono) {
        boolean existe = switch (dono.tipo()) {
            case AULA -> conteudoTurmaService.aulaExiste(dono.id());
            case MODULO -> conteudoTurmaService.moduloExiste(dono.id());
        };
        if (!existe) {
            throw new NaoEncontradoException(
                    dono.tipo() == DonoArquivo.Tipo.AULA ? "Aula não encontrada." : "Módulo não encontrado.");
        }
    }

    private static String prefixoUpload(DonoArquivo dono) {
        return "uploads/arquivos/%s/".formatted(dono.pasta());
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Arquivo não encontrado.");
    }

    private static ArquivoInvalidoException formatoRecusado() {
        return new ArquivoInvalidoException("Envie um arquivo ZIP, PDF ou TXT.");
    }

    private static ArquivoInvalidoException tamanhoExcedido() {
        return new ArquivoInvalidoException("O arquivo pode ter no máximo 200 MB.");
    }

    public record EnvioArquivo(String chave, URL url, String contentType, Instant expiraEm) {}
}

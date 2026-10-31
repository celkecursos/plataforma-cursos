package br.com.celke.cursos.arquivo.application;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloContexto;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Arquivos de apoio na área do aluno, sempre pela política de acesso: o da aula segue o acesso à aula
 * (aula gratuita inclusive); o do módulo exige matrícula vigente na turma. O download é por URL
 * pré-assinada curta, que só sai depois da conferência.
 */
@Service
public class ArquivoAlunoService {

    static final Duration VALIDADE_DOWNLOAD = Duration.ofMinutes(5);

    private final ArquivoApoioRepository arquivos;
    private final AcessoAulaService acessoAulaService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final StorageService storage;
    private final Clock clock;

    public ArquivoAlunoService(
            ArquivoApoioRepository arquivos,
            AcessoAulaService acessoAulaService,
            ConteudoTurmaService conteudoTurmaService,
            StorageService storage,
            Clock clock) {
        this.arquivos = arquivos;
        this.acessoAulaService = acessoAulaService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.storage = storage;
        this.clock = clock;
    }

    public List<ArquivoApoio> daAula(Long usuarioId, Long aulaId) {
        acessoAulaService.exigirAcesso(usuarioId, aulaId);
        return arquivos.findByAulaIdOrderById(aulaId);
    }

    public List<ArquivoApoio> doModulo(Long usuarioId, Long moduloId) {
        exigirAcessoModulo(usuarioId, moduloId);
        return arquivos.findByModuloIdOrderById(moduloId);
    }

    /** Confere o acesso ao dono do arquivo e devolve a URL de download, válida por 5 minutos. */
    public Download download(Long usuarioId, Long arquivoId) {
        ArquivoApoio arquivo = arquivos.findById(arquivoId).orElseThrow(ArquivoAdmService::naoEncontrado);
        if (arquivo.getAulaId() != null) {
            acessoAulaService.exigirAcesso(usuarioId, arquivo.getAulaId());
        } else {
            exigirAcessoModulo(usuarioId, arquivo.getModuloId());
        }
        URL url = storage.urlDownload(arquivo.getChave(), VALIDADE_DOWNLOAD, arquivo.getNomeArquivo());
        return new Download(url, clock.instant().plus(VALIDADE_DOWNLOAD));
    }

    private void exigirAcessoModulo(Long usuarioId, Long moduloId) {
        ModuloContexto modulo = conteudoTurmaService
                .moduloAtivo(moduloId)
                .orElseThrow(() -> new NaoEncontradoException("Módulo não encontrado."));
        acessoAulaService.exigirAcessoTurma(usuarioId, modulo.turmaId());
    }

    public record Download(URL url, Instant expiraEm) {}
}

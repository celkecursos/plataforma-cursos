# Upload de vídeo e conversão para HLS

**Fase:** 4
**Branch:** `feature/upload-video`

## Prompt

Vamos criar o módulo `video` do backend: o envio do vídeo da aula direto do navegador para o Garage,
em partes, e a conversão para HLS pelo worker com o ffmpeg. Siga o `backend/CLAUDE.md` e o padrão das
skills de teste. Não faça commit.

O que esta etapa entrega:

- **Vídeo da aula** (migration `V8`): um por aula (chave estrangeira `RESTRICT`: aula com vídeo
  responde 409 na exclusão), status `AGUARDANDO_ENVIO`, `PENDENTE`, `PROCESSANDO`, `PRONTO` ou `ERRO`,
  chaves do original e do HLS, tamanhos, duração, resolução, variantes, tempo de processamento e erro.
  O status é o do último envio; o HLS publicado só troca quando a conversão do envio novo termina.
- **Envio em partes** (`POST /api/adm/aulas/{id}/video/envio`): MP4, MOV, MKV ou WebM até 5 GB,
  validados antes de assinar; partes de 16 MiB (maiores se passar de 10.000 partes) com uma URL
  pré-assinada por parte em `uploads/videos/<aula>/<uuid>`. Um envio anterior ainda aberto é abortado.
- **Conclusão** (`POST .../envio/conclusao` com o número e o ETag de cada parte): junta as partes,
  confere o tamanho real e a assinatura do início do arquivo (`ftyp` para MP4/MOV, EBML para MKV/WebM).
  Arquivo recusado é apagado e o vídeo fica com erro; aceito vai para a fila na mesma transação.
- **Worker** (`TranscodificacaoManipulador`, job `video.transcodificar`): baixa o original, analisa com
  o `ffprobe` e converte com o ffmpeg numa passada só, via `ProcessBuilder` com lista de argumentos e
  `nice -n 10`: H.264 CRF 24 `veryfast`, `maxrate` 2800k (720p) e 5000k (1080p), AAC 128k, segmentos de
  6 s, sem aumentar a resolução de origem; gera a thumbnail, envia tudo para `videos/<aula>/<job>/` e só
  então apaga o original e o HLS anterior.
- **Fila**: `FilaService.renovar` estende o lease (o worker renova a cada minuto durante a conversão);
  `ManipuladorJob.desistir` avisa o manipulador quando o job vai para `ERRO`, para o vídeo ficar com
  erro. Worker que cai deixa o lease vencer e o job volta; `stop_grace_period: 30s` no worker.
- **Rotas do ADM**: `GET /api/adm/aulas/{id}/video` (status, tamanho antes e depois, redução, variantes,
  segundos de processamento por minuto de vídeo, thumbnail), `POST .../reprocessamento` (só com erro e
  com o original no bucket) e `DELETE` (fora do processamento, apaga os arquivos).
- O `verify` passa a exigir o `ffmpeg` no PATH; o pipeline instala o ffmpeg no runner.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado. O vídeo de amostra é
binário: gere-o com o comando da seção de amostra.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/StorageService.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Acesso ao bucket da aplicação. O navegador envia e baixa arquivos direto do S3 por URLs
 * pré-assinadas; a API nunca recebe o conteúdo dos arquivos grandes.
 */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public StorageService(S3Client s3, S3Presigner presigner, ArmazenamentoProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.bucket();
    }

    /** URL para o navegador enviar um arquivo pequeno com um único PUT. */
    public URL urlUpload(String chave, String contentType, Duration validade) {
        return presigner
                .presignPutObject(p -> p.signatureDuration(validade)
                        .putObjectRequest(o -> o.bucket(bucket).key(chave).contentType(contentType)))
                .url();
    }

    /** URL para baixar um arquivo. */
    public URL urlDownload(String chave, Duration validade) {
        return presigner
                .presignGetObject(p -> p.signatureDuration(validade)
                        .getObjectRequest(o -> o.bucket(bucket).key(chave)))
                .url();
    }

    /** Inicia um upload em partes e devolve o id do upload. */
    public String iniciarMultipart(String chave, String contentType) {
        return s3.createMultipartUpload(r -> r.bucket(bucket).key(chave).contentType(contentType))
                .uploadId();
    }

    /** URL para o navegador enviar uma parte (a partir de 1). Toda parte, menos a última, tem ao menos 5 MB. */
    public URL urlParte(String chave, String uploadId, int numeroParte, Duration validade) {
        return presigner
                .presignUploadPart(p -> p.signatureDuration(validade)
                        .uploadPartRequest(u ->
                                u.bucket(bucket).key(chave).uploadId(uploadId).partNumber(numeroParte)))
                .url();
    }

    /** Junta as partes enviadas; o ETag de cada parte vem da resposta do PUT da parte. */
    public void concluirMultipart(String chave, String uploadId, List<ParteEnviada> partes) {
        List<CompletedPart> concluidas = partes.stream()
                .sorted(Comparator.comparingInt(ParteEnviada::numero))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.numero())
                        .eTag(p.etag())
                        .build())
                .toList();
        s3.completeMultipartUpload(
                r -> r.bucket(bucket).key(chave).uploadId(uploadId).multipartUpload(m -> m.parts(concluidas)));
    }

    public void abortarMultipart(String chave, String uploadId) {
        s3.abortMultipartUpload(r -> r.bucket(bucket).key(chave).uploadId(uploadId));
    }

    /** Tamanho e tipo do arquivo, ou vazio se ele não existe. */
    public Optional<InfoArquivo> info(String chave) {
        try {
            HeadObjectResponse head = s3.headObject(r -> r.bucket(bucket).key(chave));
            return Optional.of(new InfoArquivo(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Conteúdo de um arquivo pequeno (imagem a processar); use {@link #info} antes para conferir o tamanho. */
    public byte[] baixar(String chave) {
        return s3.getObjectAsBytes(r -> r.bucket(bucket).key(chave)).asByteArray();
    }

    /** Primeiros bytes de um arquivo (conferência da assinatura de um vídeo sem baixá-lo inteiro). */
    public byte[] baixarInicio(String chave, int bytes) {
        return s3.getObjectAsBytes(r -> r.bucket(bucket).key(chave).range("bytes=0-" + (bytes - 1)))
                .asByteArray();
    }

    /** Baixa um arquivo grande direto para o disco; o destino não pode existir. */
    public void baixarParaArquivo(String chave, Path destino) {
        s3.getObject(r -> r.bucket(bucket).key(chave), ResponseTransformer.toFile(destino));
    }

    /** Envia um arquivo do disco (ex.: segmentos HLS gerados pelo worker). */
    public void gravarArquivo(String chave, Path origem, String contentType) {
        s3.putObject(r -> r.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromFile(origem));
    }

    /** Exclui todos os arquivos sob um prefixo (terminado em {@code /}), em lotes de até mil. */
    public void excluirPrefixo(String prefixo) {
        if (prefixo == null || !prefixo.endsWith("/") || prefixo.length() < 2) {
            throw new IllegalArgumentException("Prefixo inválido para exclusão: " + prefixo);
        }
        s3.listObjectsV2Paginator(r -> r.bucket(bucket).prefix(prefixo)).stream()
                .filter(pagina -> !pagina.contents().isEmpty())
                .forEach(pagina -> s3.deleteObjects(r -> r.bucket(bucket)
                        .delete(d -> d.objects(pagina.contents().stream()
                                        .map(o -> ObjectIdentifier.builder()
                                                .key(o.key())
                                                .build())
                                        .toList())
                                .quiet(true))));
    }

    /** Grava um arquivo gerado pela própria aplicação (ex.: imagem processada). */
    public void gravar(String chave, byte[] conteudo, String contentType) {
        s3.putObject(r -> r.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromBytes(conteudo));
    }

    public void excluir(String chave) {
        s3.deleteObject(r -> r.bucket(bucket).key(chave));
    }

    public record ParteEnviada(int numero, String etag) {}

    public record InfoArquivo(long tamanho, String contentType) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/Job.java`:

`````java
package br.com.celke.cursos.shared.fila;

/**
 * Job reservado para processamento.
 *
 * @param payload JSON com os dados do job, lido pelo manipulador do tipo
 * @param tentativas tentativas feitas, contando a atual
 */
public record Job(long id, String tipo, String payload, int tentativas, int maxTentativas) {

    /** Tentativa atual é a última: se falhar, o job vai para ERRO. */
    public boolean ultimaTentativa() {
        return tentativas >= maxTentativas;
    }

    /** Passou do limite: acontece quando o worker cai no meio do processamento várias vezes. */
    public boolean excedeuTentativas() {
        return tentativas > maxTentativas;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ManipuladorJob.java`:

`````java
package br.com.celke.cursos.shared.fila;

/**
 * Processa os jobs de um tipo. Exceção lançada = falha com nova tentativa (até o limite);
 * {@link ErroPermanenteException} = falha sem nova tentativa.
 */
public interface ManipuladorJob {

    String tipo();

    void processar(Job job) throws Exception;

    /**
     * O job foi para ERRO e não será tentado de novo (erro permanente, tentativas esgotadas ou worker
     * caindo no meio repetidas vezes). Permite registrar a falha no que o job processava.
     */
    default void desistir(Job job, String motivo) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/ProcessadorFila.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reserva um job, entrega ao manipulador do tipo e registra o resultado na fila. */
@Component
public class ProcessadorFila {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessadorFila.class);

    private final FilaService fila;
    private final Map<String, ManipuladorJob> manipuladores;
    private final Clock clock;

    public ProcessadorFila(FilaService fila, List<ManipuladorJob> manipuladores, Clock clock) {
        this.fila = fila;
        this.manipuladores =
                manipuladores.stream().collect(Collectors.toUnmodifiableMap(ManipuladorJob::tipo, Function.identity()));
        this.clock = clock;
    }

    /** Processa um job; devolve {@code false} quando não havia job disponível. */
    public boolean processarProximo() {
        var reservado = fila.reservar(manipuladores.keySet(), clock.instant());
        if (reservado.isEmpty()) {
            return false;
        }
        Job job = reservado.get();
        ManipuladorJob manipulador = manipuladores.get(job.tipo());
        if (job.excedeuTentativas()) {
            desistir(manipulador, job, "Tentativas esgotadas (o processamento foi interrompido).");
            return true;
        }
        try {
            manipulador.processar(job);
            fila.concluir(job, clock.instant());
        } catch (ErroPermanenteException e) {
            LOG.error("Job {} ({}) falhou sem nova tentativa: {}", job.id(), job.tipo(), e.getMessage());
            desistir(manipulador, job, e.getMessage());
        } catch (Exception e) {
            LOG.warn("Job {} ({}) falhou na tentativa {}: {}", job.id(), job.tipo(), job.tentativas(), e.toString());
            fila.falhar(job, e.toString(), clock.instant());
            if (job.ultimaTentativa()) {
                avisar(manipulador, job, e.toString());
            }
        }
        return true;
    }

    private void desistir(ManipuladorJob manipulador, Job job, String motivo) {
        fila.falharDefinitivo(job, motivo, clock.instant());
        avisar(manipulador, job, motivo);
    }

    /** O estado da fila já foi gravado: uma falha ao avisar o manipulador só vai para o log. */
    private static void avisar(ManipuladorJob manipulador, Job job, String motivo) {
        try {
            manipulador.desistir(job, motivo);
        } catch (RuntimeException e) {
            LOG.error("Job {} ({}): falha ao registrar a desistência", job.id(), job.tipo(), e);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/fila/FilaService.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fila de jobs no PostgreSQL. A reserva usa {@code FOR UPDATE SKIP LOCKED}: vários workers consomem
 * ao mesmo tempo sem pegar o mesmo job. Todo método recebe o instante atual, para as regras de tempo
 * serem testáveis.
 */
@Service
@EnableConfigurationProperties(FilaProperties.class)
public class FilaService {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final FilaProperties props;
    private final Backoff backoff;

    public FilaService(JdbcClient jdbc, JsonMapper jsonMapper, FilaProperties props) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.backoff = new Backoff(props.backoffBase(), props.backoffMaximo());
    }

    /** Coloca um job na fila; o payload é gravado como JSON. Devolve o id do job. */
    public long enfileirar(String tipo, Object payload, Instant agora) {
        return jdbc.sql("""
                        INSERT INTO jobs (tipo, payload, status, max_tentativas, disponivel_em, criado_em, atualizado_em)
                        VALUES (:tipo, CAST(:payload AS JSONB), 'PENDENTE', :max, :agora, :agora, :agora)
                        RETURNING id
                        """)
                .param("tipo", tipo)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("max", props.maxTentativas())
                .param("agora", utc(agora))
                .query(Long.class)
                .single();
    }

    /**
     * Reserva o próximo job disponível de um dos tipos: pendente e no horário, ou em processamento
     * com o lease vencido (o worker anterior caiu).
     */
    @Transactional
    public Optional<Job> reservar(Collection<String> tipos, Instant agora) {
        if (tipos.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        UPDATE jobs
                           SET status = 'PROCESSANDO', tentativas = tentativas + 1,
                               reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = (SELECT id FROM jobs
                                      WHERE tipo IN (:tipos)
                                        AND ((status = 'PENDENTE' AND disponivel_em <= :agora)
                                          OR (status = 'PROCESSANDO' AND reservado_ate < :agora))
                                      ORDER BY disponivel_em, id
                                      LIMIT 1
                                      FOR UPDATE SKIP LOCKED)
                        RETURNING id, tipo, CAST(payload AS TEXT) AS payload, tentativas, max_tentativas
                        """)
                .param("tipos", tipos)
                .param("agora", utc(agora))
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .query((rs, n) -> new Job(
                        rs.getLong("id"),
                        rs.getString("tipo"),
                        rs.getString("payload"),
                        rs.getInt("tentativas"),
                        rs.getInt("max_tentativas")))
                .optional();
    }

    public void concluir(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'CONCLUIDO', reservado_ate = NULL, ultimo_erro = NULL,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """).param("id", job.id()).param("agora", utc(agora)).update();
    }

    /**
     * Estende o lease de um job em processamento: um job longo (como uma transcodificação) chama a
     * cada minuto para não ser pego por outro worker. Se o worker cair, o lease vence e o job volta.
     */
    public void renovar(Job job, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET reservado_ate = :reservadoAte, atualizado_em = :agora
                         WHERE id = :id AND status = 'PROCESSANDO'
                        """)
                .param("id", job.id())
                .param("reservadoAte", utc(agora.plus(props.lease())))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha com nova tentativa depois do backoff; sem tentativas restantes, o job vai para ERRO. */
    public void falhar(Job job, String erro, Instant agora) {
        if (job.ultimaTentativa()) {
            falharDefinitivo(job, erro, agora);
            return;
        }
        jdbc.sql("""
                        UPDATE jobs SET status = 'PENDENTE', reservado_ate = NULL, ultimo_erro = :erro,
                                        disponivel_em = :disponivelEm, atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("disponivelEm", utc(agora.plus(backoff.atraso(job.tentativas()))))
                .param("agora", utc(agora))
                .update();
    }

    /** Falha sem nova tentativa (erro permanente ou tentativas esgotadas). */
    public void falharDefinitivo(Job job, String erro, Instant agora) {
        jdbc.sql("""
                        UPDATE jobs SET status = 'ERRO', reservado_ate = NULL, ultimo_erro = :erro,
                                        atualizado_em = :agora
                         WHERE id = :id
                        """)
                .param("id", job.id())
                .param("erro", erro)
                .param("agora", utc(agora))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/ConteudoTurmaService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas ao conteúdo exportadas para os outros módulos (matrícula, vídeo, progresso...), que não
 * acessam os repositórios do curso. Devolve só dados prontos, sem entidades com carregamento tardio.
 * O conteúdo visível é sempre o ativo: módulo e aula {@code ATIVO}, na ordem.
 */
@Service
public class ConteudoTurmaService {

    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final CapaCursoService capaCursoService;

    public ConteudoTurmaService(
            TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas, CapaCursoService capaCursoService) {
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
        this.capaCursoService = capaCursoService;
    }

    @Transactional(readOnly = true)
    public Optional<TurmaResumo> turma(Long turmaId) {
        return turmas.comCurso(List.of(turmaId)).stream().findFirst().map(this::resumo);
    }

    /** Turmas indexadas pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, TurmaResumo> turmas(Collection<Long> turmaIds) {
        return turmas.comCurso(turmaIds).stream()
                .map(this::resumo)
                .collect(Collectors.toMap(TurmaResumo::turmaId, Function.identity()));
    }

    /** Módulos ativos da turma, cada um com as aulas ativas, na ordem. */
    @Transactional(readOnly = true)
    public List<ModuloConteudo> conteudoAtivo(Long turmaId) {
        List<Modulo> ativos = modulos.findByTurmaIdAndStatusOrderByOrdem(turmaId, StatusConteudo.ATIVO);
        Map<Long, List<Aula>> aulasPorModulo = aulas
                .findByModuloIdInAndStatusOrderByOrdem(
                        ativos.stream().map(Modulo::getId).toList(), StatusConteudo.ATIVO)
                .stream()
                .collect(Collectors.groupingBy(aula -> aula.getModulo().getId()));
        return ativos.stream()
                .map(modulo -> new ModuloConteudo(
                        modulo.getId(),
                        modulo.getTitulo(),
                        aulasPorModulo.getOrDefault(modulo.getId(), List.of()).stream()
                                .map(aula -> new AulaConteudo(aula.getId(), aula.getTitulo(), aula.isGratuita()))
                                .toList()))
                .toList();
    }

    /** A aula existe, em qualquer status (gestão do vídeo e dos arquivos pelo ADM). */
    @Transactional(readOnly = true)
    public boolean aulaExiste(Long aulaId) {
        return aulas.existsById(aulaId);
    }

    /** Aula ativa de módulo ativo, com o que a política de acesso precisa; vazio se não existe ou está inativa. */
    @Transactional(readOnly = true)
    public Optional<AulaContexto> aulaAtiva(Long aulaId) {
        return aulas.comHierarquia(aulaId)
                .filter(aula -> aula.getStatus() == StatusConteudo.ATIVO
                        && aula.getModulo().getStatus() == StatusConteudo.ATIVO)
                .map(aula -> {
                    Turma turma = aula.getModulo().getTurma();
                    return new AulaContexto(
                            aula.getId(),
                            aula.getTitulo(),
                            aula.getDescricao(),
                            aula.isGratuita(),
                            turma.getId(),
                            turma.getStatus(),
                            turma.getCurso().getStatus());
                });
    }

    private TurmaResumo resumo(Turma turma) {
        Curso curso = turma.getCurso();
        return new TurmaResumo(
                turma.getId(),
                turma.getNome(),
                turma.getStatus(),
                curso.getId(),
                curso.getTitulo(),
                curso.getSlug(),
                curso.getStatus(),
                capaCursoService.url(curso).map(Object::toString).orElse(null));
    }

    /** Turma com os dados do curso; {@code capaUrl} é temporária (vale uma hora). */
    public record TurmaResumo(
            Long turmaId,
            String turmaNome,
            StatusTurma turmaStatus,
            Long cursoId,
            String cursoTitulo,
            String cursoSlug,
            StatusCurso cursoStatus,
            String capaUrl) {}

    public record ModuloConteudo(Long id, String titulo, List<AulaConteudo> aulas) {}

    public record AulaConteudo(Long id, String titulo, boolean gratuita) {}

    public record AulaContexto(
            Long id,
            String titulo,
            String descricao,
            boolean gratuita,
            Long turmaId,
            StatusTurma turmaStatus,
            StatusCurso cursoStatus) {

        /** Aula de degustação: gratuita, de turma ativa e de curso publicado (rascunho não vaza). */
        public boolean aberta() {
            return gratuita && turmaStatus == StatusTurma.ATIVA && cursoStatus == StatusCurso.PUBLICADO;
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/AulaAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.Ordenacao;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão das aulas de um módulo pelo ADM. Toda mudança de ordem trava o módulo, para duas
 * operações simultâneas não gerarem posições repetidas.
 */
@Service
public class AulaAdmService {

    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final Clock clock;

    public AulaAdmService(ModuloRepository modulos, AulaRepository aulas, Clock clock) {
        this.modulos = modulos;
        this.aulas = aulas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Aula> listar(Long moduloId) {
        if (!modulos.existsById(moduloId)) {
            throw ModuloAdmService.naoEncontrado();
        }
        return aulas.findByModuloIdOrderByOrdem(moduloId);
    }

    /** A aula nova entra no fim do módulo. */
    @Transactional
    public Aula criar(Long moduloId, DadosAula dados) {
        Modulo modulo = modulos.travar(moduloId).orElseThrow(ModuloAdmService::naoEncontrado);
        int ordem = Ordenacao.proxima(aulas.findByModuloIdOrderByOrdem(moduloId));
        return aulas.save(
                new Aula(modulo, dados.titulo(), dados.descricao(), dados.gratuita(), ordem, clock.instant()));
    }

    @Transactional
    public Aula atualizar(Long id, DadosAula dados) {
        Aula aula = buscar(id);
        aula.atualizar(dados.titulo(), dados.descricao(), dados.gratuita(), clock.instant());
        return aula;
    }

    @Transactional
    public Aula alterarStatus(Long id, StatusConteudo status) {
        Aula aula = buscar(id);
        aula.alterarStatus(status, clock.instant());
        return aula;
    }

    @Transactional
    public List<Aula> reordenar(Long moduloId, List<Long> ids) {
        modulos.travar(moduloId).orElseThrow(ModuloAdmService::naoEncontrado);
        List<Aula> atuais = aulas.findByModuloIdOrderByOrdem(moduloId);
        Ordenacao.reordenar(atuais, ids);
        return atuais.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
    }

    /** Exclui a aula (só sem vídeo) e refaz a sequência das que ficam. */
    @Transactional
    public void excluir(Long id) {
        Aula aula = buscar(id);
        Long moduloId = aula.getModulo().getId();
        modulos.travar(moduloId);
        try {
            aulas.delete(aula);
            aulas.flush();
        } catch (DataIntegrityViolationException e) {
            // A chave estrangeira do vídeo impede a exclusão
            throw new ConflitoException("A aula tem vídeo. Remova o vídeo ou inative a aula.");
        }
        Ordenacao.compactar(aulas.findByModuloIdOrderByOrdem(moduloId));
    }

    private Aula buscar(Long id) {
        return aulas.findById(id).orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
    }
}
`````

`backend/src/main/resources/application.yaml`:

`````yaml
spring:
  application:
    name: plataforma-cursos
  threads:
    virtual:
      enabled: true
  # Sem usuário embutido: a autenticação é feita pelos usuários do banco
  autoconfigure:
    exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
  # Sem valor padrão: sem as credenciais a aplicação não sobe (o compose exige as variáveis)
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
  web:
    # Mensagens de validação em português
    locale: pt_BR
    locale-resolver: fixed
  mail:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SMTP_AUTH:true}
      mail.smtp.starttls.enable: ${SMTP_STARTTLS:true}
      mail.smtp.starttls.required: ${SMTP_STARTTLS:true}
      mail.smtp.connectiontimeout: 10000
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000

logging:
  # Horário de Brasília nos logs: o @timestamp do JSON (ECS) e o texto do console do desenvolvimento
  structured:
    json:
      customizer: br.com.celke.cursos.shared.config.FusoHorarioLogs
  pattern:
    dateformat: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Sao_Paulo"

server:
  shutdown: graceful
  # A API roda atrás do Caddy. Com "native", o Tomcat só aceita X-Forwarded-* vindo de proxy interno
  # (rede privada) e lê o X-Forwarded-For da direita para a esquerda: IP forjado pelo cliente é ignorado
  forward-headers-strategy: native
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    # O SMTP fora do ar não deve tirar a API do ar: o envio é pela fila, com novas tentativas
    mail:
      enabled: false

# Swagger: ligado só no desenvolvimento e na homologação (DOCS_ENABLED=true)
springdoc:
  api-docs:
    enabled: ${DOCS_ENABLED:false}
  swagger-ui:
    enabled: ${DOCS_ENABLED:false}

app:
  auth:
    # Segredo do JWT em Base64, com pelo menos 32 bytes (openssl rand -base64 48)
    jwt-segredo: ${JWT_SEGREDO:}
    emissor: plataforma-cursos
    validade-acesso: PT10M
    validade-refresh: P14D
    cookie-seguro: true
    # Origens do front que podem renovar e encerrar a sessão (separadas por vírgula)
    origens-permitidas: ${AUTH_ORIGENS:}
  conta:
    # Área do aluno: base dos links enviados por e-mail
    url-aluno: ${FRONT_ALUNO_URL:}
    validade-confirmacao: PT48H
    validade-recuperacao: PT1H
    emails-por-hora: 3
  limites:
    # Rotas de autenticação e conta: requisições por IP a cada minuto
    requisicoes-por-ip-por-minuto: 30
    # Falhas de login seguidas que bloqueiam o par e-mail + IP, e o e-mail vindo de qualquer IP
    falhas-por-email-e-ip: 5
    falhas-por-email: 20
    # Contadores de falha zeram depois de uma hora sem novas falhas
    janela-falhas: PT1H
    bloqueio: PT15M
  armazenamento:
    # Endereço usado pela aplicação (rede interna)
    endpoint: ${S3_ENDPOINT:}
    # Endereço que vai nas URLs pré-assinadas (o que o navegador acessa)
    endpoint-publico: ${S3_ENDPOINT_PUBLICO:}
    regiao: ${S3_REGIAO:garage}
    bucket: ${S3_BUCKET:}
    access-key: ${S3_ACCESS_KEY:}
    secret-key: ${S3_SECRET_KEY:}
    cors-origens: ${S3_CORS_ORIGENS:}
    dias-uploads-abandonados: 2
  email:
    remetente: ${EMAIL_REMETENTE:}
    nome-remetente: Plataforma de Cursos
  fila:
    max-tentativas: 5
    lease: PT10M
    backoff-base: PT30S
    backoff-maximo: PT1H
    intervalo: PT2S
    consumidor-ativo: false
  video:
    tamanho-maximo: 5GB
    # Validade das URLs de envio das partes (um vídeo grande numa conexão lenta)
    validade-envio: PT6H
    ffmpeg: ffmpeg
    ffprobe: ffprobe
    # veryfast: na VPS (1,5 vCPU para o worker) o ganho de compressão dos presets lentos não compensa o tempo
    preset: veryfast
    crf: 24
    diretorio-trabalho: ${java.io.tmpdir}/plataforma-cursos-videos
`````

`backend/src/main/resources/application-worker.yaml`:

`````yaml
# Worker: processa a fila (transcodificação de vídeo, e-mails) e não atende HTTP
spring:
  main:
    web-application-type: none
    # Sem servidor HTTP e com threads virtuais, nada segura a JVM: mantém o processo vivo
    keep-alive: true
  # As migrations são aplicadas pela API
  flyway:
    enabled: false

app:
  fila:
    consumidor-ativo: true
    # Arquivo regravado a cada 10 s; o healthcheck do Docker confere se mudou no último minuto
    arquivo-batimento: /tmp/worker-batimento
  video:
    # O ffmpeg roda com prioridade baixa: a API e o banco, no mesmo servidor, continuam respondendo
    prefixo-comando: nice,-n,10
`````

`infra/compose.yml`:

`````yaml
# Ambiente local. Uso, na raiz do repositório:
#   ./infra/scripts/gerar-env.sh          (uma vez: cria infra/.env com segredos aleatórios)
#   docker compose -f infra/compose.yml up -d --build --wait
name: plataforma-cursos

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO no infra/.env}
  # Origens aceitas no refresh e no logout: o Swagger da própria API e o front de desenvolvimento
  AUTH_ORIGENS: ${AUTH_ORIGENS:-http://localhost:${API_PORTA:-8080},http://localhost:5173}
  # Área do aluno: base dos links de confirmação de e-mail e de recuperação de senha
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:-http://localhost:5173}
  S3_ENDPOINT: http://garage:3900
  # Endereço que o navegador usa nas URLs pré-assinadas
  S3_ENDPOINT_PUBLICO: http://localhost:${S3_PORTA:-3900}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:-}
  # No desenvolvimento os e-mails ficam no Mailpit (http://localhost:8025)
  SMTP_HOST: mailpit
  SMTP_PORT: "1025"
  SMTP_AUTH: "false"
  SMTP_STARTTLS: "false"
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE no infra/.env}
  # Logs em JSON (ECS), com o id de correlação de cada requisição
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs

services:
  postgres:
    image: postgres:18.6-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB no infra/.env}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER no infra/.env}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no infra/.env}
    volumes:
      # A partir da versão 18 os dados ficam em /var/lib/postgresql/18/docker
      - postgres:/var/lib/postgresql
    ports:
      - "127.0.0.1:${POSTGRES_PORTA:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20

  garage:
    image: dxflrs/garage:v2.4.1
    # Na primeira subida cria o layout de nó único, o bucket e a chave da aplicação
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET no infra/.env}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN no infra/.env}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET no infra/.env}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY no infra/.env}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY no infra/.env}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    ports:
      - "127.0.0.1:${S3_PORTA:-3900}:3900"
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20

  mailpit:
    image: axllent/mailpit:v1.31.6
    ports:
      - "127.0.0.1:${MAILPIT_PORTA:-8025}:8025"   # painel web
    healthcheck:
      test: ["CMD", "/mailpit", "readyz"]
      interval: 5s
      timeout: 3s
      retries: 20

  api:
    build:
      context: ../backend
      target: api
    environment:
      <<: *app
      DOCS_ENABLED: "true"
    ports:
      - "127.0.0.1:${API_PORTA:-8080}:8080"
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
      mailpit:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20

  worker:
    build:
      context: ../backend
      target: worker
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 30s
      retries: 3

volumes:
  postgres:
  garage:
`````

`infra/compose.prod.yml`:

`````yaml
# Stack de um ambiente dos servidores (produção ou homologação): API, worker, PostgreSQL e Garage.
# Imagens publicadas no GHCR com a tag do commit (TAG); nenhuma porta publicada. O Caddy (projeto
# próprio, em caddy/) chega à API e ao Garage pela rede plataforma-cursos-<AMBIENTE>, pelos apelidos
# <AMBIENTE>-api e <AMBIENTE>-garage. Todas as variáveis vêm do .env do ambiente.
#
#   Produção:    docker compose -p plataforma-cursos-producao -f compose.prod.yml up -d --wait
#   Homologação: docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml up -d --wait

# Configuração compartilhada pela API e pelo worker
x-app: &app
  DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:?defina POSTGRES_DB}
  DB_USERNAME: ${POSTGRES_USER:?defina POSTGRES_USER}
  DB_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
  JWT_SEGREDO: ${JWT_SEGREDO:?defina JWT_SEGREDO}
  AUTH_ORIGENS: ${AUTH_ORIGENS:?defina AUTH_ORIGENS}
  FRONT_ALUNO_URL: ${FRONT_ALUNO_URL:?defina FRONT_ALUNO_URL}
  S3_ENDPOINT: http://garage:3900
  S3_ENDPOINT_PUBLICO: ${S3_ENDPOINT_PUBLICO:?defina S3_ENDPOINT_PUBLICO}
  S3_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
  S3_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
  S3_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
  S3_CORS_ORIGENS: ${S3_CORS_ORIGENS:?defina S3_CORS_ORIGENS}
  SMTP_HOST: ${SMTP_HOST:?defina SMTP_HOST}
  SMTP_PORT: ${SMTP_PORT:-587}
  SMTP_USERNAME: ${SMTP_USERNAME:-}
  SMTP_PASSWORD: ${SMTP_PASSWORD:-}
  SMTP_AUTH: ${SMTP_AUTH:-true}
  SMTP_STARTTLS: ${SMTP_STARTTLS:-true}
  EMAIL_REMETENTE: ${EMAIL_REMETENTE:?defina EMAIL_REMETENTE}
  LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs

services:
  api:
    image: ${IMAGEM_API:-ghcr.io/celkecursos/plataforma-cursos-api}:${TAG:?defina TAG}
    restart: unless-stopped
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx512m
    depends_on:
      postgres:
        condition: service_healthy
      garage:
        condition: service_healthy
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-api
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/readiness"]
      interval: 5s
      timeout: 3s
      start_period: 30s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  worker:
    image: ${IMAGEM_WORKER:-ghcr.io/celkecursos/plataforma-cursos-worker}:${TAG:?defina TAG}
    restart: unless-stopped
    # No deploy, o job em andamento tem 30 s para terminar; uma conversão interrompida volta para a fila
    # quando o lease vence (o worker o renova a cada minuto enquanto converte)
    stop_grace_period: 30s
    environment:
      <<: *app
      JAVA_TOOL_OPTIONS: -Xmx768m
    depends_on:
      # As migrations são aplicadas pela API; o worker sobe depois dela
      api:
        condition: service_healthy
    healthcheck:
      # O worker regrava este arquivo a cada 10 s (não tem servidor HTTP para health check)
      test: ["CMD-SHELL", "find /tmp/worker-batimento -mmin -1 | grep -q ."]
      interval: 10s
      timeout: 3s
      start_period: 30s
      retries: 3
    deploy:
      resources:
        limits:
          # A transcodificação (ffmpeg) não pode tomar a CPU toda da VPS
          cpus: "1.5"
          memory: 2G

  postgres:
    image: postgres:18.6-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:?defina POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER:?defina POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD}
    volumes:
      - postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 1G

  garage:
    image: dxflrs/garage:v2.4.1
    restart: unless-stopped
    command: ["/garage", "server", "--single-node", "--default-bucket"]
    environment:
      GARAGE_RPC_SECRET: ${GARAGE_RPC_SECRET:?defina GARAGE_RPC_SECRET}
      GARAGE_ADMIN_TOKEN: ${GARAGE_ADMIN_TOKEN:?defina GARAGE_ADMIN_TOKEN}
      GARAGE_DEFAULT_BUCKET: ${S3_BUCKET:?defina S3_BUCKET}
      GARAGE_DEFAULT_ACCESS_KEY: ${S3_ACCESS_KEY:?defina S3_ACCESS_KEY}
      GARAGE_DEFAULT_SECRET_KEY: ${S3_SECRET_KEY:?defina S3_SECRET_KEY}
    volumes:
      - ./garage/garage.toml:/etc/garage.toml:ro
      - garage:/var/lib/garage
    networks:
      default:
        aliases:
          - ${AMBIENTE:?defina AMBIENTE}-garage
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: 5s
      timeout: 5s
      retries: 20
    deploy:
      resources:
        limits:
          memory: 768M

networks:
  default:
    name: plataforma-cursos-${AMBIENTE:?defina AMBIENTE}

volumes:
  postgres:
  garage:
`````

`.github/workflows/pipeline.yml`:

`````yaml
# Testes, build das imagens e e2e em todo PR para homolog e main. Depois do merge (push), refaz tudo
# com o código exato do commit, publica no GHCR as imagens que passaram no e2e (tag = SHA) e implanta:
# push na homolog → homologação; push na main → produção. Os deploys só rodam com a variável do
# repositório DEPLOY_ATIVO=true (criada quando a VPS estiver pronta); sem ela, são pulados.
name: Pipeline

on:
  pull_request:
    branches: [homolog, main]
  push:
    branches: [homolog, main]
  workflow_dispatch:

concurrency:
  group: pipeline-${{ github.ref }}
  # Só PRs cancelam execuções antigas: um deploy nunca é interrompido no meio
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

permissions:
  contents: read

env:
  COMPOSE_E2E: docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env

jobs:
  testes:
    name: Testes (unitários e integração)
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      # A transcodificação de vídeo é testada com o ffmpeg de verdade
      - name: Instalar ffmpeg
        run: sudo apt-get update -q && sudo apt-get install -y -q --no-install-recommends ffmpeg

      # Unitários, integração (Testcontainers), cobertura mínima e formatação
      - name: Verificar
        run: ./mvnw -B verify

      - name: Relatórios dos testes
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: relatorios-testes
          path: |
            backend/target/surefire-reports
            backend/target/failsafe-reports

      - name: Relatório de cobertura
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: cobertura
          path: backend/target/site/jacoco
          if-no-files-found: ignore

  e2e:
    name: e2e (stack completa)
    needs: testes
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - uses: docker/setup-buildx-action@v4

      # As mesmas imagens que vão para os servidores, com cache de camadas entre execuções
      - name: Imagem da API
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: api
          tags: plataforma-cursos-api:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=api
          cache-to: type=gha,mode=max,scope=api

      - name: Imagem do worker
        uses: docker/build-push-action@v7
        with:
          context: backend
          target: worker
          tags: plataforma-cursos-worker:e2e
          load: true
          # Liga o pacote do GHCR ao repositório (mesmas permissões)
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=worker
          cache-to: type=gha,mode=max,scope=worker

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker; do
            docker tag "plataforma-cursos-$imagem:e2e" "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
            docker push "$REGISTRO/plataforma-cursos-$imagem:$GITHUB_SHA"
          done

      - name: Logs da stack
        if: failure()
        run: $COMPOSE_E2E logs --no-color > stack-e2e.log

      - name: Relatórios do e2e
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: e2e-falha
          path: |
            stack-e2e.log
            backend/target/failsafe-reports

      - name: Derrubar a stack de e2e
        if: always()
        run: $COMPOSE_E2E down -v

  deploy-homolog:
    name: Homologação
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/homolog' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: homolog
    secrets: inherit

  deploy-producao:
    name: Produção
    needs: e2e
    if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.DEPLOY_ATIVO == 'true'
    uses: ./.github/workflows/deploy.yml
    with:
      ambiente: producao
    secrets: inherit
`````

`backend/CLAUDE.md`:

`````markdown
# Backend — guia do projeto Java

API e worker da Plataforma de Cursos: Java 25, Spring Boot 4.1, PostgreSQL 18, Flyway.
Os comandos abaixo rodam **dentro de `backend/`**.

## Definição de pronto

Toda tarefa só está concluída quando passa:

```bash
./mvnw spotless:apply      # formata (palantir-java-format)
./mvnw verify              # unitários + integração + cobertura mínima + spotless:check
./mvnw verify -Pe2e        # e2e, com a stack de e2e no ar (como subir: skill criar-teste-e2e)
```

O `verify` precisa do Docker rodando (Testcontainers) e do `ffmpeg`/`ffprobe` no PATH (a
transcodificação de vídeo é testada de verdade; o pipeline instala o ffmpeg no runner).

## Estrutura

```
src/main/java/br/com/celke/cursos/
  shared/          # config, security, erros, storage S3, fila, e-mail
  <modulo>/        # auth, usuario, curso, matricula, video, progresso, arquivo, forum, certificado, perfil
    api/           # controllers e DTOs (record)
    application/   # casos de uso
    domain/        # entidades, enums, regras
    infra/         # repositórios, integrações
src/main/resources/
  application.yaml            # padrão (perfil api)
  application-worker.yaml     # worker: sem servidor HTTP
  db/migration/               # Flyway
src/test/java/                # unitários (*Test) e integração (*IT)
src/e2e/java/                 # e2e caixa-preta (*E2E), perfil Maven e2e
Dockerfile                    # targets api e worker (com ffmpeg)
```

## Padrões de código

- **Idioma:** domínio em português (`Curso`, `Matricula`, `criarCurso`), sufixos técnicos em inglês (`Controller`, `Service`, `Repository`, `Policy`, `Test`, `IT`).
- **Controllers** só lidam com HTTP. Toda regra fica em `application/` ou `domain/`.
- **Um módulo não acessa o repositório de outro** — usa o serviço de `application/` exportado pelo outro módulo.
- **Rotas são privadas por padrão.** Rota pública é declarada em `shared/security/SecurityConfig` com o método HTTP; restrição por papel com `@PreAuthorize`.
- **Acesso a conteúdo do curso** (aula, vídeo, arquivo, fórum, progresso, certificado) passa **sempre** pela `AcessoAulaPolicy`.
- **Erros** no formato `ProblemDetail` (RFC 9457) pelo handler global; erro de validação traz a lista `erros` com `campo` e `mensagem`.
- **Data e hora** vêm de um `java.time.Clock` injetado — nunca `LocalDateTime.now()` direto em regra de negócio.
- **Status** são enums, nunca números soltos.
- **DTOs** são `record` com Bean Validation; toda rota documentada no OpenAPI.
- **Configuração:** variável nova → `application.yaml` com `${VARIAVEL}`, validação em `@ConfigurationProperties` quando a aplicação tiver propriedades próprias, e `infra/.env.example`.
- **Migrations** Flyway compatíveis com a versão anterior (expandir → depois contrair).

## Testes

Três níveis. **Antes de criar ou alterar um teste, leia o padrão do nível** — ele vale mesmo
quando a skill não é usada:

| Nível | O que cobre | Onde | Padrão | Skill |
|---|---|---|---|---|
| Unitário | regra de negócio, cálculo, política, validação de DTO — sem Spring, sem banco | `src/test/java/**/*Test.java` | `.claude/skills/criar-teste-unitario/padrao.md` | `/criar-teste-unitario <modulo>` |
| Integração | rotas, segurança, persistência, S3, fila, e-mail — Spring + Testcontainers | `src/test/java/**/*IT.java` | `.claude/skills/criar-teste-integracao/padrao.md` | `/criar-teste-integracao <modulo>` |
| e2e | jornadas que atravessam os containers (Caddy, worker, Garage, e-mail) — caixa-preta | `src/e2e/java/**/*E2E.java` | `.claude/skills/criar-teste-e2e/padrao.md` | `/criar-teste-e2e <jornada>` |

Funcionalidade nova vem com os níveis que se aplicam **no mesmo PR**.
`````

### 2. Migration

`backend/src/main/resources/db/migration/V8__criar_videos.sql`:

`````sql
-- Vídeo de uma aula. O original vai para uploads/ e, depois de convertido em HLS pelo worker, é apagado;
-- o HLS publicado fica em videos/<aula>/<job>/. O status descreve o último envio: um vídeo pronto continua
-- publicado (playlist_chave) enquanto um envio novo é processado.
CREATE TABLE videos (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aula_id                BIGINT        NOT NULL REFERENCES aulas (id) ON DELETE RESTRICT,
    status                 VARCHAR(20)   NOT NULL,
    -- Envio em andamento ou aguardando processamento
    upload_chave           VARCHAR(200),
    upload_id              VARCHAR(200),
    nome_original          VARCHAR(255),
    tamanho_original       BIGINT,
    -- Resultado da última conversão bem-sucedida
    playlist_chave         VARCHAR(200),
    thumbnail_chave        VARCHAR(200),
    tamanho_final          BIGINT,
    duracao_segundos       INT,
    largura                INT,
    altura                 INT,
    variantes              VARCHAR(50),
    tempo_processamento_ms BIGINT,
    processado_em          TIMESTAMPTZ,
    erro                   TEXT,
    criado_em              TIMESTAMPTZ   NOT NULL,
    atualizado_em          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_videos_aula UNIQUE (aula_id),
    CONSTRAINT ck_videos_status
        CHECK (status IN ('AGUARDANDO_ENVIO', 'PENDENTE', 'PROCESSANDO', 'PRONTO', 'ERRO')),
    CONSTRAINT ck_videos_tamanhos CHECK (tamanho_original IS NULL OR tamanho_original > 0)
);
`````

### 3. Domínio do vídeo

`backend/src/main/java/br/com/celke/cursos/video/domain/StatusVideo.java`:

`````java
package br.com.celke.cursos.video.domain;

/** Situação do último envio de vídeo da aula. */
public enum StatusVideo {
    /** URLs de envio geradas; o navegador ainda está enviando as partes. */
    AGUARDANDO_ENVIO,
    /** Arquivo conferido e job na fila. */
    PENDENTE,
    PROCESSANDO,
    PRONTO,
    ERRO;

    /** Na fila ou com o worker: não aceita novo envio, reprocessamento nem exclusão. */
    public boolean emProcessamento() {
        return this == PENDENTE || this == PROCESSANDO;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/Video.java`:

`````java
package br.com.celke.cursos.video.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Vídeo de uma aula (no máximo um). Guarda só o id da aula, sem relacionamento JPA com o módulo
 * {@code curso}. O status é o do último envio; o HLS publicado ({@code playlistChave}) só muda quando
 * uma nova conversão termina, então um vídeo pronto continua no ar enquanto o substituto é processado.
 */
@Entity
@Table(name = "videos")
public class Video {

    /** Tamanho máximo da mensagem de erro guardada (a saída do ffmpeg pode ser longa). */
    static final int TAMANHO_ERRO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aula_id", nullable = false, updatable = false)
    private Long aulaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVideo status;

    @Column(name = "upload_chave", length = 200)
    private String uploadChave;

    @Column(name = "upload_id", length = 200)
    private String uploadId;

    @Column(name = "nome_original", length = 255)
    private String nomeOriginal;

    @Column(name = "tamanho_original")
    private Long tamanhoOriginal;

    @Column(name = "playlist_chave", length = 200)
    private String playlistChave;

    @Column(name = "thumbnail_chave", length = 200)
    private String thumbnailChave;

    @Column(name = "tamanho_final")
    private Long tamanhoFinal;

    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;

    private Integer largura;

    private Integer altura;

    /** Alturas geradas, da menor para a maior (ex.: {@code 720p,1080p}). */
    @Column(length = 50)
    private String variantes;

    @Column(name = "tempo_processamento_ms")
    private Long tempoProcessamentoMs;

    @Column(name = "processado_em")
    private Instant processadoEm;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Video() {}

    public Video(Long aulaId, Instant agora) {
        this.aulaId = aulaId;
        this.status = StatusVideo.AGUARDANDO_ENVIO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Novo envio: guarda o upload em partes; o HLS publicado, se houver, continua no ar. */
    public void iniciarEnvio(String chave, String uploadId, String nomeOriginal, long tamanho, Instant agora) {
        exigirForaDoProcessamento();
        this.status = StatusVideo.AGUARDANDO_ENVIO;
        this.uploadChave = chave;
        this.uploadId = uploadId;
        this.nomeOriginal = nomeOriginal;
        this.tamanhoOriginal = tamanho;
        this.erro = null;
        this.atualizadoEm = agora;
    }

    /** Arquivo conferido: vai para a fila. O upload em partes já foi concluído. */
    public void enfileirar(long tamanhoReal, Instant agora) {
        if (status != StatusVideo.AGUARDANDO_ENVIO) {
            throw new IllegalStateException("Só um envio em andamento pode ir para a fila.");
        }
        this.status = StatusVideo.PENDENTE;
        this.uploadId = null;
        this.tamanhoOriginal = tamanhoReal;
        this.atualizadoEm = agora;
    }

    /** Volta para a fila a partir do original que ainda está no bucket. */
    public void reprocessar(Instant agora) {
        if (status != StatusVideo.ERRO) {
            throw new IllegalStateException("Só um vídeo com erro pode ser reprocessado.");
        }
        this.status = StatusVideo.PENDENTE;
        this.erro = null;
        this.atualizadoEm = agora;
    }

    public void iniciarProcessamento(Instant agora) {
        this.status = StatusVideo.PROCESSANDO;
        this.atualizadoEm = agora;
    }

    /**
     * Conversão concluída: publica o HLS novo e esquece o original (apagado do bucket em seguida).
     * Devolve a chave da playlist anterior, cujos arquivos devem ser apagados.
     */
    public String concluir(ResultadoTranscodificacao resultado, Instant agora) {
        String anterior = playlistChave;
        this.status = StatusVideo.PRONTO;
        this.playlistChave = resultado.playlistChave();
        this.thumbnailChave = resultado.thumbnailChave();
        this.tamanhoFinal = resultado.tamanhoFinal();
        this.duracaoSegundos = resultado.duracaoSegundos();
        this.largura = resultado.largura();
        this.altura = resultado.altura();
        this.variantes = resultado.variantes();
        this.tempoProcessamentoMs = resultado.tempoProcessamentoMs();
        this.processadoEm = agora;
        this.uploadChave = null;
        this.erro = null;
        this.atualizadoEm = agora;
        return anterior;
    }

    /** Falha no envio ou na conversão; o original continua no bucket enquanto existir (para reprocessar). */
    public void falhar(String mensagem, Instant agora) {
        this.status = StatusVideo.ERRO;
        this.erro = mensagem == null || mensagem.length() <= TAMANHO_ERRO
                ? mensagem
                : mensagem.substring(mensagem.length() - TAMANHO_ERRO);
        this.atualizadoEm = agora;
    }

    /** O original foi recusado e apagado: sem ele, só um envio novo resolve. */
    public void descartarOriginal(Instant agora) {
        this.uploadChave = null;
        this.uploadId = null;
        this.atualizadoEm = agora;
    }

    public void exigirForaDoProcessamento() {
        if (status.emProcessamento()) {
            throw new IllegalStateException("O vídeo está em processamento.");
        }
    }

    /** Redução de tamanho do original para o HLS, em percentual (negativo se cresceu); vazio sem os dois tamanhos. */
    public Double reducaoPercentual() {
        if (tamanhoOriginal == null || tamanhoFinal == null || status != StatusVideo.PRONTO) {
            return null;
        }
        return Math.round((1 - (double) tamanhoFinal / tamanhoOriginal) * 1000) / 10.0;
    }

    /** Segundos de processamento por minuto de vídeo (medida do capítulo de limites da VPS). */
    public Double segundosPorMinutoDeVideo() {
        if (tempoProcessamentoMs == null || duracaoSegundos == null || duracaoSegundos == 0) {
            return null;
        }
        return Math.round(tempoProcessamentoMs / 1000.0 / (duracaoSegundos / 60.0) * 10) / 10.0;
    }

    public Long getId() {
        return id;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public StatusVideo getStatus() {
        return status;
    }

    public String getUploadChave() {
        return uploadChave;
    }

    public String getUploadId() {
        return uploadId;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public Long getTamanhoOriginal() {
        return tamanhoOriginal;
    }

    public String getPlaylistChave() {
        return playlistChave;
    }

    public String getThumbnailChave() {
        return thumbnailChave;
    }

    public Long getTamanhoFinal() {
        return tamanhoFinal;
    }

    public Integer getDuracaoSegundos() {
        return duracaoSegundos;
    }

    public Integer getLargura() {
        return largura;
    }

    public Integer getAltura() {
        return altura;
    }

    public String getVariantes() {
        return variantes;
    }

    public Long getTempoProcessamentoMs() {
        return tempoProcessamentoMs;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    public String getErro() {
        return erro;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/ResultadoTranscodificacao.java`:

`````java
package br.com.celke.cursos.video.domain;

/**
 * O que a conversão gerou e já está no bucket.
 *
 * @param tamanhoFinal soma dos arquivos HLS (playlists e segmentos), em bytes
 * @param variantes alturas geradas, da menor para a maior (ex.: {@code 720p,1080p})
 */
public record ResultadoTranscodificacao(
        String playlistChave,
        String thumbnailChave,
        long tamanhoFinal,
        int duracaoSegundos,
        int largura,
        int altura,
        String variantes,
        long tempoProcessamentoMs) {}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/FormatoVideo.java`:

`````java
package br.com.celke.cursos.video.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos no envio, cada um com a assinatura que o conteúdo real precisa ter. A extensão e o
 * {@code Content-Type} informados pelo navegador não bastam: o início do arquivo é conferido depois
 * do envio, e o ffprobe ainda analisa o arquivo antes da conversão.
 */
public enum FormatoVideo {
    MP4("video/mp4", Familia.ISO),
    MOV("video/quicktime", Familia.ISO),
    MKV("video/x-matroska", Familia.EBML),
    WEBM("video/webm", Familia.EBML);

    /** Bytes lidos do início do arquivo para conferir a assinatura. */
    public static final int BYTES_ASSINATURA = 12;

    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] FTYP = {'f', 't', 'y', 'p'};

    private final String contentType;
    private final Familia familia;

    FormatoVideo(String contentType, Familia familia) {
        this.contentType = contentType;
        this.familia = familia;
    }

    public static Optional<FormatoVideo> doContentType(String contentType) {
        return Arrays.stream(values())
                .filter(f -> f.contentType.equals(contentType))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    /**
     * Confere o início do arquivo: MP4 e MOV têm a caixa {@code ftyp} no byte 4; MKV e WebM começam com
     * o cabeçalho EBML.
     */
    public boolean assinaturaConfere(byte[] inicio) {
        return switch (familia) {
            case ISO -> inicio.length >= 8 && Arrays.equals(inicio, 4, 8, FTYP, 0, 4);
            case EBML -> inicio.length >= 4 && Arrays.equals(inicio, 0, 4, EBML, 0, 4);
        };
    }

    private enum Familia {
        ISO,
        EBML
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/DivisaoPartes.java`:

`````java
package br.com.celke.cursos.video.domain;

/**
 * Divisão do arquivo em partes para o upload multipart. O S3 aceita até 10.000 partes, e toda parte,
 * menos a última, precisa ter pelo menos 5 MiB.
 *
 * @param tamanhoParte bytes de cada parte (a última pode ser menor)
 * @param quantidade número de partes
 */
public record DivisaoPartes(long tamanhoParte, int quantidade) {

    public static final long PARTE_PADRAO = 16L * 1024 * 1024;
    public static final int MAXIMO_PARTES = 10_000;
    private static final long MIB = 1024L * 1024;

    /** Partes de 16 MiB; arquivo que passaria de 10.000 partes usa partes maiores (múltiplos de 1 MiB). */
    public static DivisaoPartes de(long tamanho) {
        if (tamanho <= 0) {
            throw new IllegalArgumentException("O tamanho precisa ser positivo.");
        }
        long parte = PARTE_PADRAO;
        if (divisaoParaCima(tamanho, parte) > MAXIMO_PARTES) {
            parte = divisaoParaCima(divisaoParaCima(tamanho, MAXIMO_PARTES), MIB) * MIB;
        }
        return new DivisaoPartes(parte, (int) divisaoParaCima(tamanho, parte));
    }

    private static long divisaoParaCima(long a, long b) {
        return (a + b - 1) / b;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/domain/Variante.java`:

`````java
package br.com.celke.cursos.video.domain;

import java.util.List;

/**
 * Uma resolução do HLS, com o teto de taxa de bits do CRF ({@code maxrate}) e o buffer do controle de
 * taxa ({@code bufsize}, o dobro).
 */
public record Variante(int altura, int maxrateKbps) {

    /** Alturas-alvo da plataforma. */
    public static final List<Variante> PADRAO = List.of(new Variante(720, 2800), new Variante(1080, 5000));

    /** Teto para origem menor que 720p: proporcional à altura, com mínimo de 600 kbps. */
    static final int KBPS_POR_LINHA = 4;

    public String nome() {
        return altura + "p";
    }

    public int bufsizeKbps() {
        return maxrateKbps * 2;
    }

    /**
     * Variantes para uma origem: as alturas-alvo que não passam da altura de origem (nunca aumenta a
     * resolução). Origem abaixo de 720p gera uma variante só, na própria altura (arredondada para par,
     * exigência do H.264 com yuv420p).
     */
    public static List<Variante> paraOrigem(int alturaOrigem) {
        if (alturaOrigem < 2) {
            throw new IllegalArgumentException("Altura de origem inválida: " + alturaOrigem);
        }
        List<Variante> cabem =
                PADRAO.stream().filter(v -> v.altura() <= alturaOrigem).toList();
        if (!cabem.isEmpty()) {
            return cabem;
        }
        int altura = alturaOrigem - alturaOrigem % 2;
        return List.of(new Variante(altura, Math.max(600, altura * KBPS_POR_LINHA)));
    }
}
`````

### 4. ffmpeg e repositório

`backend/src/main/java/br/com/celke/cursos/video/infra/VideoRepository.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.Video;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByAulaId(Long aulaId);

    /** Vídeo travado até o fim da transação: envio, fila e worker não mudam o status ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.aulaId = :aulaId")
    Optional<Video> travarPorAula(Long aulaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.id = :id")
    Optional<Video> travar(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/ComandosFfmpeg.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.video.domain.Variante;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Argumentos do ffprobe e do ffmpeg, sempre como lista: cada item vira um argumento do processo, sem
 * shell no meio, então um nome de arquivo nunca é interpretado como comando.
 */
public final class ComandosFfmpeg {

    /** Duração dos segmentos HLS, em segundos. */
    static final int SEGUNDOS_SEGMENTO = 6;

    static final String MASTER = "master.m3u8";
    static final String THUMBNAIL = "thumbnail.jpg";
    static final int ALTURA_THUMBNAIL = 720;

    private final String ffmpeg;
    private final String ffprobe;
    private final String preset;
    private final int crf;

    public ComandosFfmpeg(String ffmpeg, String ffprobe, String preset, int crf) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.preset = preset;
        this.crf = crf;
    }

    /** Formato e faixas do arquivo em JSON. */
    public List<String> analisar(Path entrada) {
        return List.of(
                ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", entrada.toString());
    }

    /**
     * HLS com uma variante por altura numa passada só (o vídeo é decodificado uma vez): H.264 com CRF e
     * teto de taxa por variante, áudio AAC 128k, segmentos de 6 s com quadro-chave forçado no início de
     * cada um. Roda no diretório de saída: gera {@code master.m3u8} e {@code <altura>p/index.m3u8} com os
     * segmentos.
     */
    public List<String> converterHls(Path entrada, List<Variante> variantes, boolean temAudio) {
        int n = variantes.size();
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin", "-y", "-loglevel", "error"));
        args.addAll(List.of("-i", entrada.toString()));
        args.addAll(List.of("-filter_complex", filtroEscala(variantes)));
        for (int i = 0; i < n; i++) {
            args.addAll(List.of("-map", "[s" + i + "]"));
            if (temAudio) {
                args.addAll(List.of("-map", "0:a:0"));
            }
        }
        args.addAll(List.of(
                "-c:v",
                "libx264",
                "-preset",
                preset,
                "-crf",
                String.valueOf(crf),
                "-profile:v",
                "high",
                "-pix_fmt",
                "yuv420p"));
        for (int i = 0; i < n; i++) {
            Variante v = variantes.get(i);
            args.addAll(List.of("-maxrate:v:" + i, v.maxrateKbps() + "k", "-bufsize:v:" + i, v.bufsizeKbps() + "k"));
        }
        args.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*" + SEGUNDOS_SEGMENTO + ")"));
        if (temAudio) {
            args.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "48000"));
        }
        args.addAll(List.of(
                "-f",
                "hls",
                "-hls_time",
                String.valueOf(SEGUNDOS_SEGMENTO),
                "-hls_playlist_type",
                "vod",
                "-hls_flags",
                "independent_segments",
                "-hls_segment_filename",
                "%v/seg_%04d.ts",
                "-master_pl_name",
                MASTER,
                "-var_stream_map",
                mapaVariantes(variantes, temAudio),
                "%v/index.m3u8"));
        return args;
    }

    /** Um quadro em JPEG, a 10% da duração (no máximo aos 10 s), com até 720 linhas. */
    public List<String> thumbnail(Path entrada, double duracao, int alturaOrigem) {
        double instante = Math.min(duracao * 0.1, 10);
        int altura = Math.min(ALTURA_THUMBNAIL, alturaOrigem - alturaOrigem % 2);
        return List.of(
                ffmpeg,
                "-hide_banner",
                "-nostdin",
                "-y",
                "-loglevel",
                "error",
                "-ss",
                String.format(Locale.ROOT, "%.3f", instante),
                "-i",
                entrada.toString(),
                "-frames:v",
                "1",
                "-vf",
                "scale=-2:" + altura,
                "-q:v",
                "3",
                THUMBNAIL);
    }

    /** {@code [0:v]split=2[v0][v1];[v0]scale=-2:720[s0];[v1]scale=-2:1080[s1]} */
    static String filtroEscala(List<Variante> variantes) {
        int n = variantes.size();
        String saidas = IntStream.range(0, n).mapToObj(i -> "[v" + i + "]").collect(Collectors.joining());
        String escalas = IntStream.range(0, n)
                .mapToObj(i ->
                        "[v%d]scale=-2:%d[s%d]".formatted(i, variantes.get(i).altura(), i))
                .collect(Collectors.joining(";"));
        return "[0:v]split=" + n + saidas + ";" + escalas;
    }

    /** {@code v:0,a:0,name:720p v:1,a:1,name:1080p} (sem a faixa de áudio quando a origem não tem). */
    static String mapaVariantes(List<Variante> variantes, boolean temAudio) {
        return IntStream.range(0, variantes.size())
                .mapToObj(i -> (temAudio ? "v:%d,a:%d,name:%s" : "v:%d,name:%3$s")
                        .formatted(i, i, variantes.get(i).nome()))
                .collect(Collectors.joining(" "));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/AnaliseVideo.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * O que a conversão precisa saber do original, lido da saída JSON do ffprobe. Arquivo sem faixa de
 * vídeo ou sem duração é erro permanente: tentar de novo não muda o arquivo.
 *
 * @param duracao duração em segundos
 */
public record AnaliseVideo(double duracao, int largura, int altura, boolean temAudio) {

    public static AnaliseVideo de(SaidaFfprobe saida) {
        List<Faixa> faixas = saida == null || saida.streams() == null ? List.of() : saida.streams();
        Faixa video = faixas.stream()
                .filter(f -> "video".equals(f.tipo()) && f.largura() != null && f.altura() != null)
                .filter(f -> f.largura() > 0 && f.altura() > 0)
                .findFirst()
                .orElseThrow(() -> new ErroPermanenteException("O arquivo não tem uma faixa de vídeo."));
        boolean temAudio = faixas.stream().anyMatch(f -> "audio".equals(f.tipo()));
        return new AnaliseVideo(duracao(saida), video.largura(), video.altura(), temAudio);
    }

    public int duracaoSegundos() {
        return (int) Math.round(duracao);
    }

    private static double duracao(SaidaFfprobe saida) {
        try {
            double duracao = Double.parseDouble(saida.format().duration());
            if (duracao > 0 && Double.isFinite(duracao)) {
                return duracao;
            }
        } catch (NullPointerException | NumberFormatException e) {
            // cai na mensagem abaixo
        }
        throw new ErroPermanenteException("Não foi possível ler a duração do vídeo.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaidaFfprobe(List<Faixa> streams, Formato format) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Faixa(
            @JsonProperty("codec_type") String tipo,
            @JsonProperty("width") Integer largura,
            @JsonProperty("height") Integer altura) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Formato(String duration) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/FalhaFfmpegException.java`:

`````java
package br.com.celke.cursos.video.infra;

/** O ffmpeg ou o ffprobe terminou com erro ou passou do tempo limite (o job tenta de novo). */
public class FalhaFfmpegException extends RuntimeException {

    public FalhaFfmpegException(String mensagem) {
        super(mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/Ffmpeg.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.video.application.VideoProperties;
import br.com.celke.cursos.video.domain.Variante;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executa o ffprobe e o ffmpeg com {@link ProcessBuilder} (lista de argumentos, sem shell). A saída de
 * erro vai para um arquivo, e o fim dela entra na mensagem quando o processo falha. Enquanto espera,
 * chama {@code aCadaMinuto} (o worker renova o lease do job, para uma conversão longa não parecer
 * travada).
 */
@Component
public class Ffmpeg {

    static final Duration INTERVALO = Duration.ofMinutes(1);
    static final Duration LIMITE_ANALISE = Duration.ofMinutes(2);
    /** Fim da saída de erro guardado na mensagem de falha. */
    static final int CARACTERES_ERRO = 1500;

    static final String ARQUIVO_ERRO = "ffmpeg.log";

    private final ComandosFfmpeg comandos;
    private final List<String> prefixo;
    private final JsonMapper jsonMapper;

    public Ffmpeg(VideoProperties props, JsonMapper jsonMapper) {
        this.comandos = new ComandosFfmpeg(props.ffmpeg(), props.ffprobe(), props.preset(), props.crf());
        this.prefixo = props.prefixoComando();
        this.jsonMapper = jsonMapper;
    }

    /** Analisa o original; arquivo que o ffprobe não reconhece é erro permanente. */
    public AnaliseVideo analisar(Path entrada, Path trabalho) {
        Path json = trabalho.resolve("ffprobe.json");
        int codigo = executar(comandos.analisar(entrada), trabalho, json, LIMITE_ANALISE, () -> {});
        if (codigo != 0) {
            throw new ErroPermanenteException(
                    "O arquivo não é um vídeo válido: " + fimDoErro(trabalho, "ffprobe.json"));
        }
        try {
            return AnaliseVideo.de(jsonMapper.readValue(json.toFile(), AnaliseVideo.SaidaFfprobe.class));
        } catch (ErroPermanenteException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ErroPermanenteException("Não foi possível analisar o vídeo.");
        }
    }

    /**
     * Gera o HLS e a thumbnail em {@code saida}. O tempo limite é generoso (vinte vezes a duração, mais
     * dez minutos): só pega o ffmpeg travado.
     */
    public void converter(
            Path entrada, Path saida, List<Variante> variantes, AnaliseVideo analise, Runnable aCadaMinuto) {
        criarDiretorios(saida, variantes);
        Duration limite = Duration.ofMinutes(10).plusSeconds((long) Math.ceil(analise.duracao() * 20));
        exigirSucesso(
                executar(
                        comandos.converterHls(entrada, variantes, analise.temAudio()),
                        saida,
                        null,
                        limite,
                        aCadaMinuto),
                saida,
                "conversão para HLS");
        exigirSucesso(
                executar(
                        comandos.thumbnail(entrada, analise.duracao(), analise.altura()),
                        saida,
                        null,
                        LIMITE_ANALISE,
                        aCadaMinuto),
                saida,
                "thumbnail");
        // A saída de erro não é publicada com o HLS
        apagar(saida.resolve(ARQUIVO_ERRO));
    }

    /**
     * Roda o comando no diretório informado; a saída padrão vai para {@code saidaPadrao} (ou é
     * descartada) e a de erro para {@code ffmpeg.log} (ou {@code <saidaPadrao>.err}). Devolve o código
     * de saída.
     */
    int executar(List<String> args, Path diretorio, Path saidaPadrao, Duration limite, Runnable aCadaMinuto) {
        List<String> comando = new ArrayList<>(prefixo);
        comando.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(comando)
                .directory(diretorio.toFile())
                .redirectError(arquivoErro(diretorio, saidaPadrao).toFile())
                .redirectOutput(
                        saidaPadrao == null
                                ? ProcessBuilder.Redirect.DISCARD
                                : ProcessBuilder.Redirect.to(saidaPadrao.toFile()));
        Process processo;
        try {
            processo = builder.start();
        } catch (IOException e) {
            throw new FalhaFfmpegException("Não foi possível executar " + args.getFirst() + ": " + e.getMessage());
        }
        long fim = System.nanoTime() + limite.toNanos();
        try {
            while (!processo.waitFor(Math.min(INTERVALO.toMillis(), restante(fim)), TimeUnit.MILLISECONDS)) {
                if (restante(fim) <= 0) {
                    processo.destroyForcibly();
                    throw new FalhaFfmpegException(args.getFirst() + " passou do tempo limite de " + limite + ".");
                }
                aCadaMinuto.run();
            }
            return processo.exitValue();
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new FalhaFfmpegException(args.getFirst() + " foi interrompido.");
        }
    }

    private void exigirSucesso(int codigo, Path diretorio, String etapa) {
        if (codigo != 0) {
            throw new FalhaFfmpegException(
                    "Falha na %s (código %d): %s".formatted(etapa, codigo, fimDoErro(diretorio, null)));
        }
    }

    private static String fimDoErro(Path diretorio, String saidaPadrao) {
        Path arquivo = arquivoErro(diretorio, saidaPadrao == null ? null : diretorio.resolve(saidaPadrao));
        try {
            String texto = Files.readString(arquivo, StandardCharsets.UTF_8).strip();
            return texto.length() <= CARACTERES_ERRO ? texto : texto.substring(texto.length() - CARACTERES_ERRO);
        } catch (IOException e) {
            return "(sem saída de erro)";
        }
    }

    private static Path arquivoErro(Path diretorio, Path saidaPadrao) {
        return saidaPadrao == null
                ? diretorio.resolve(ARQUIVO_ERRO)
                : saidaPadrao.resolveSibling(saidaPadrao.getFileName() + ".err");
    }

    private static long restante(long fim) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(fim - System.nanoTime()));
    }

    private static void criarDiretorios(Path saida, List<Variante> variantes) {
        try {
            for (Variante v : variantes) {
                Files.createDirectories(saida.resolve(v.nome()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
`````

### 5. Casos de uso e worker

`backend/src/main/java/br/com/celke/cursos/video/application/VideoProperties.java`:

`````java
package br.com.celke.cursos.video.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do envio e da conversão de vídeos.
 *
 * @param tamanhoMaximo maior arquivo aceito no envio
 * @param validadeEnvio validade das URLs de envio das partes
 * @param ffmpeg executável do ffmpeg (no PATH ou caminho completo)
 * @param ffprobe executável do ffprobe
 * @param prefixoComando programa e argumentos colocados antes do ffmpeg (no worker, {@code nice -n 10}
 *     para a conversão ter prioridade baixa)
 * @param preset preset do x264: mais lento comprime melhor e gasta mais CPU
 * @param crf qualidade constante do x264 (menor = melhor e maior)
 * @param diretorioTrabalho onde o worker baixa o original e gera o HLS (apagado no fim de cada job)
 */
@Validated
@ConfigurationProperties("app.video")
public record VideoProperties(
        @NotNull DataSize tamanhoMaximo,
        @NotNull Duration validadeEnvio,
        @NotBlank String ffmpeg,
        @NotBlank String ffprobe,
        List<String> prefixoComando,
        @NotBlank String preset,
        @Min(18) @Max(30) int crf,
        @NotNull Path diretorioTrabalho) {

    public VideoProperties {
        prefixoComando = prefixoComando == null
                ? List.of()
                : prefixoComando.stream()
                        .map(String::strip)
                        .filter(p -> !p.isEmpty())
                        .toList();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/VideoInvalidoException.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Envio de vídeo recusado (formato, tamanho ou conteúdo). */
public class VideoInvalidoException extends RegraNegocioException {

    public VideoInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/PayloadTranscodificacao.java`:

`````java
package br.com.celke.cursos.video.application;

/**
 * Dados do job de transcodificação. A chave do original identifica o envio: se o vídeo recebeu outro
 * envio depois deste job, ele é descartado.
 */
public record PayloadTranscodificacao(long videoId, String uploadChave) {}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/VideoAdmService.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado do vídeo de cada aula no banco. Toda mudança de status trava a linha do vídeo, para o ADM e o
 * worker não se atropelarem. Não fala com o armazenamento: isso fica no {@link EnvioVideoService} e no
 * manipulador do job.
 */
@Service
public class VideoAdmService {

    public static final String TIPO_JOB = "video.transcodificar";

    private final VideoRepository videos;
    private final ConteudoTurmaService conteudoTurmaService;
    private final FilaService fila;
    private final Clock clock;

    public VideoAdmService(
            VideoRepository videos, ConteudoTurmaService conteudoTurmaService, FilaService fila, Clock clock) {
        this.videos = videos;
        this.conteudoTurmaService = conteudoTurmaService;
        this.fila = fila;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Video buscarPorAula(Long aulaId) {
        exigirAula(aulaId);
        return videos.findByAulaId(aulaId).orElseThrow(VideoAdmService::semVideo);
    }

    /** Aula existe e o vídeo dela (se houver) não está na fila nem com o worker. */
    @Transactional(readOnly = true)
    public void exigirEnvioPermitido(Long aulaId) {
        exigirAula(aulaId);
        videos.findByAulaId(aulaId).ifPresent(VideoAdmService::exigirForaDoProcessamento);
    }

    /**
     * Registra um novo envio na aula (cria o vídeo no primeiro). Devolve o envio anterior ainda não
     * concluído, cujo upload em partes deve ser abortado.
     */
    @Transactional
    public EnvioRegistrado registrarEnvio(Long aulaId, String chave, String uploadId, String nome, long tamanho) {
        exigirAula(aulaId);
        Instant agora = clock.instant();
        Video video = videos.travarPorAula(aulaId).orElseGet(() -> new Video(aulaId, agora));
        exigirForaDoProcessamento(video);
        Optional<EnvioAnterior> anterior =
                video.getStatus() == StatusVideo.AGUARDANDO_ENVIO && video.getUploadId() != null
                        ? Optional.of(new EnvioAnterior(video.getUploadChave(), video.getUploadId()))
                        : Optional.empty();
        video.iniciarEnvio(chave, uploadId, nome, tamanho, agora);
        try {
            // Dois primeiros envios ao mesmo tempo: a restrição única da aula recusa o segundo
            return new EnvioRegistrado(videos.saveAndFlush(video), anterior);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Outro envio para esta aula começou ao mesmo tempo. Tente de novo.");
        }
    }

    /** Envio em andamento da aula (o navegador terminou de enviar as partes e pede a conclusão). */
    @Transactional(readOnly = true)
    public Video envioEmAndamento(Long aulaId) {
        return videos.findByAulaId(aulaId)
                .filter(v -> v.getStatus() == StatusVideo.AGUARDANDO_ENVIO && v.getUploadId() != null)
                .orElseThrow(() -> {
                    exigirAula(aulaId);
                    return new ConflitoException("Nenhum envio em andamento para esta aula. Comece um novo envio.");
                });
    }

    /** Arquivo conferido: grava o tamanho real e coloca o job na fila, na mesma transação. */
    @Transactional
    public Video enfileirar(Long videoId, long tamanhoReal) {
        Video video = travar(videoId);
        if (video.getStatus() != StatusVideo.AGUARDANDO_ENVIO) {
            throw new ConflitoException("Nenhum envio em andamento para esta aula. Comece um novo envio.");
        }
        Instant agora = clock.instant();
        video.enfileirar(tamanhoReal, agora);
        fila.enfileirar(TIPO_JOB, new PayloadTranscodificacao(video.getId(), video.getUploadChave()), agora);
        return video;
    }

    /** Envio recusado na conferência: o original já foi apagado. */
    @Transactional
    public void recusarEnvio(Long videoId, String motivo) {
        Video video = travar(videoId);
        video.falhar(motivo, clock.instant());
        video.descartarOriginal(clock.instant());
    }

    /** Vídeo com erro volta para a fila a partir do original. */
    @Transactional
    public Video reprocessar(Long aulaId) {
        exigirAula(aulaId);
        Video video = videos.travarPorAula(aulaId).orElseThrow(VideoAdmService::semVideo);
        if (video.getStatus() != StatusVideo.ERRO) {
            throw new ConflitoException("Só um vídeo com erro pode ser reprocessado.");
        }
        if (video.getUploadChave() == null) {
            throw originalIndisponivel();
        }
        Instant agora = clock.instant();
        video.reprocessar(agora);
        fila.enfileirar(TIPO_JOB, new PayloadTranscodificacao(video.getId(), video.getUploadChave()), agora);
        return video;
    }

    /** O original do vídeo com erro sumiu do bucket (expirou): só um envio novo resolve. */
    @Transactional
    public void descartarOriginal(Long videoId) {
        travar(videoId).descartarOriginal(clock.instant());
    }

    /** Remove o vídeo da aula; devolve o que precisa ser apagado do bucket. */
    @Transactional
    public Video excluir(Long aulaId) {
        exigirAula(aulaId);
        Video video = videos.travarPorAula(aulaId).orElseThrow(VideoAdmService::semVideo);
        exigirForaDoProcessamento(video);
        videos.delete(video);
        return video;
    }

    // ---- Worker ----

    /**
     * Começa a processar o envio do job; vazio se o job ficou velho (vídeo excluído, outro envio feito
     * depois ou status que não é de fila).
     */
    @Transactional
    public Optional<Video> iniciarProcessamento(PayloadTranscodificacao payload) {
        return videos.travar(payload.videoId())
                .filter(v ->
                        v.getStatus().emProcessamento() && payload.uploadChave().equals(v.getUploadChave()))
                .map(v -> {
                    v.iniciarProcessamento(clock.instant());
                    return v;
                });
    }

    /** Publica o resultado; devolve a playlist anterior (a apagar), se havia. */
    @Transactional
    public Optional<String> concluir(Long videoId, ResultadoTranscodificacao resultado) {
        return Optional.ofNullable(travar(videoId).concluir(resultado, clock.instant()));
    }

    /** Falha definitiva do job; ignora o vídeo que já recebeu outro envio ou foi excluído. */
    @Transactional
    public void falharProcessamento(PayloadTranscodificacao payload, String motivo) {
        videos.travar(payload.videoId())
                .filter(v ->
                        v.getStatus().emProcessamento() && payload.uploadChave().equals(v.getUploadChave()))
                .ifPresent(v -> v.falhar(motivo, clock.instant()));
    }

    private Video travar(Long videoId) {
        return videos.travar(videoId).orElseThrow(VideoAdmService::semVideo);
    }

    private void exigirAula(Long aulaId) {
        if (!conteudoTurmaService.aulaExiste(aulaId)) {
            throw new NaoEncontradoException("Aula não encontrada.");
        }
    }

    private static void exigirForaDoProcessamento(Video video) {
        if (video.getStatus().emProcessamento()) {
            throw emProcessamento();
        }
    }

    static NaoEncontradoException semVideo() {
        return new NaoEncontradoException("A aula não tem vídeo.");
    }

    static ConflitoException emProcessamento() {
        return new ConflitoException("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
    }

    static ConflitoException originalIndisponivel() {
        return new ConflitoException("O arquivo original não está mais disponível. Envie o vídeo de novo.");
    }

    /** Envio anterior ainda não concluído (upload em partes aberto no bucket). */
    public record EnvioAnterior(String chave, String uploadId) {}

    public record EnvioRegistrado(Video video, Optional<EnvioAnterior> anterior) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/EnvioVideoService.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.domain.DivisaoPartes;
import br.com.celke.cursos.video.domain.FormatoVideo;
import br.com.celke.cursos.video.domain.Video;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Envio do vídeo da aula direto do navegador para o bucket, em partes, por URLs pré-assinadas: a API
 * nunca recebe o arquivo. Na conclusão, a API confere o tamanho e a assinatura reais do arquivo antes
 * de colocá-lo na fila de conversão.
 */
@Service
@EnableConfigurationProperties(VideoProperties.class)
public class EnvioVideoService {

    static final Duration VALIDADE_THUMBNAIL = Duration.ofHours(1);

    private final VideoAdmService videoAdmService;
    private final StorageService storage;
    private final VideoProperties props;
    private final Clock clock;

    public EnvioVideoService(
            VideoAdmService videoAdmService, StorageService storage, VideoProperties props, Clock clock) {
        this.videoAdmService = videoAdmService;
        this.storage = storage;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Valida tipo e tamanho antes de assinar, abre o upload em partes e devolve uma URL por parte. Um
     * envio anterior ainda aberto na mesma aula é abortado.
     */
    public EnvioIniciado iniciar(Long aulaId, String nomeArquivo, String contentType, long tamanho) {
        FormatoVideo formato = FormatoVideo.doContentType(contentType)
                .orElseThrow(() -> new VideoInvalidoException("Envie um vídeo MP4, MOV, MKV ou WebM."));
        if (tamanho > props.tamanhoMaximo().toBytes()) {
            throw new VideoInvalidoException(
                    "O vídeo pode ter no máximo %s.".formatted(legivel(props.tamanhoMaximo())));
        }
        // Confere a aula e o status antes de abrir um upload no bucket
        videoAdmService.exigirEnvioPermitido(aulaId);
        String chave = "uploads/videos/%d/%s".formatted(aulaId, UUID.randomUUID());
        String uploadId = storage.iniciarMultipart(chave, formato.contentType());
        VideoAdmService.EnvioRegistrado registro;
        try {
            registro = videoAdmService.registrarEnvio(aulaId, chave, uploadId, nomeArquivo.strip(), tamanho);
        } catch (RuntimeException e) {
            storage.abortarMultipart(chave, uploadId);
            throw e;
        }
        registro.anterior().ifPresent(a -> abortarSemFalhar(a.chave(), a.uploadId()));
        DivisaoPartes divisao = DivisaoPartes.de(tamanho);
        List<UrlParte> partes = IntStream.rangeClosed(1, divisao.quantidade())
                .mapToObj(n -> new UrlParte(n, storage.urlParte(chave, uploadId, n, props.validadeEnvio())))
                .toList();
        return new EnvioIniciado(
                registro.video(),
                divisao.tamanhoParte(),
                partes,
                clock.instant().plus(props.validadeEnvio()));
    }

    /**
     * Junta as partes e confere o arquivo real: tamanho igual ao informado e início com a assinatura do
     * formato. Arquivo recusado é apagado e o vídeo fica com erro. Aceito, vai para a fila.
     */
    public Video concluir(Long aulaId, List<ParteEnviada> partes) {
        Video video = videoAdmService.envioEmAndamento(aulaId);
        Set<Integer> numeros = partes.stream().map(ParteEnviada::numero).collect(Collectors.toSet());
        if (numeros.size() != partes.size()) {
            throw new VideoInvalidoException("Cada parte pode ser informada uma vez só.");
        }
        String chave = video.getUploadChave();
        try {
            storage.concluirMultipart(chave, video.getUploadId(), partes);
        } catch (SdkException e) {
            throw new VideoInvalidoException("O envio não foi concluído. Confira se todas as partes foram enviadas.");
        }
        var info = storage.info(chave)
                .orElseThrow(() -> new VideoInvalidoException("O vídeo não foi enviado. Comece um novo envio."));
        if (info.tamanho() != video.getTamanhoOriginal()) {
            recusar(video, chave, "O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.");
        }
        Optional<FormatoVideo> formato = FormatoVideo.doContentType(info.contentType());
        if (formato.isEmpty()
                || !formato.get().assinaturaConfere(storage.baixarInicio(chave, FormatoVideo.BYTES_ASSINATURA))) {
            recusar(video, chave, "O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
        }
        return videoAdmService.enfileirar(video.getId(), info.tamanho());
    }

    /** Volta para a fila um vídeo com erro, se o original ainda está no bucket. */
    public Video reprocessar(Long aulaId) {
        Video video = videoAdmService.buscarPorAula(aulaId);
        if (video.getUploadChave() != null
                && storage.info(video.getUploadChave()).isEmpty()) {
            videoAdmService.descartarOriginal(video.getId());
            throw VideoAdmService.originalIndisponivel();
        }
        return videoAdmService.reprocessar(aulaId);
    }

    /** Remove o vídeo da aula e os arquivos dele no bucket (original, envio aberto, HLS e thumbnail). */
    public void excluir(Long aulaId) {
        Video video = videoAdmService.excluir(aulaId);
        if (video.getUploadId() != null) {
            abortarSemFalhar(video.getUploadChave(), video.getUploadId());
        } else if (video.getUploadChave() != null) {
            storage.excluir(video.getUploadChave());
        }
        if (video.getPlaylistChave() != null) {
            storage.excluirPrefixo(prefixoHls(video.getPlaylistChave()));
        }
    }

    /** URL temporária da thumbnail, ou vazio sem vídeo pronto. */
    public Optional<URL> urlThumbnail(Video video) {
        return Optional.ofNullable(video.getThumbnailChave())
                .map(chave -> storage.urlDownload(chave, VALIDADE_THUMBNAIL));
    }

    /** {@code videos/<aula>/<job>/master.m3u8} → {@code videos/<aula>/<job>/} */
    static String prefixoHls(String playlistChave) {
        return playlistChave.substring(0, playlistChave.lastIndexOf('/') + 1);
    }

    private void recusar(Video video, String chave, String motivo) {
        storage.excluir(chave);
        videoAdmService.recusarEnvio(video.getId(), motivo);
        throw new VideoInvalidoException(motivo);
    }

    private void abortarSemFalhar(String chave, String uploadId) {
        try {
            storage.abortarMultipart(chave, uploadId);
        } catch (SdkException e) {
            // Upload já concluído ou abortado: a regra de expiração do bucket limpa o que sobrar
        }
    }

    static String legivel(DataSize tamanho) {
        long gb = DataSize.ofGigabytes(1).toBytes();
        return tamanho.toBytes() % gb == 0 ? tamanho.toGigabytes() + " GB" : tamanho.toMegabytes() + " MB";
    }

    public record UrlParte(int numero, URL url) {}

    public record EnvioIniciado(Video video, long tamanhoParte, List<UrlParte> partes, Instant expiraEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/video/application/TranscodificacaoManipulador.java`:

`````java
package br.com.celke.cursos.video.application;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.shared.fila.Job;
import br.com.celke.cursos.shared.fila.ManipuladorJob;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.Variante;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.AnaliseVideo;
import br.com.celke.cursos.video.infra.Ffmpeg;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte o original em HLS (roda no worker): baixa do bucket, analisa com o ffprobe, gera as
 * variantes e a thumbnail com o ffmpeg, envia tudo para {@code videos/<aula>/<job>/}, publica e só
 * então apaga o original e o HLS anterior. Rodar de novo o mesmo job (o worker caiu no meio) refaz o
 * prefixo do job do zero. Falha comum volta para a fila; a desistência deixa o vídeo com erro.
 */
@Component
public class TranscodificacaoManipulador implements ManipuladorJob {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodificacaoManipulador.class);

    private final VideoAdmService videoAdmService;
    private final StorageService storage;
    private final Ffmpeg ffmpeg;
    private final FilaService fila;
    private final JsonMapper jsonMapper;
    private final VideoProperties props;
    private final Clock clock;

    public TranscodificacaoManipulador(
            VideoAdmService videoAdmService,
            StorageService storage,
            Ffmpeg ffmpeg,
            FilaService fila,
            JsonMapper jsonMapper,
            VideoProperties props,
            Clock clock) {
        this.videoAdmService = videoAdmService;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.fila = fila;
        this.jsonMapper = jsonMapper;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public String tipo() {
        return VideoAdmService.TIPO_JOB;
    }

    @Override
    public void processar(Job job) throws IOException {
        PayloadTranscodificacao payload = payload(job);
        Optional<Video> iniciado = videoAdmService.iniciarProcessamento(payload);
        if (iniciado.isEmpty()) {
            LOG.info("Job {}: o vídeo {} mudou depois deste envio; nada a fazer", job.id(), payload.videoId());
            return;
        }
        Video video = iniciado.get();
        Files.createDirectories(props.diretorioTrabalho());
        Path trabalho = Files.createTempDirectory(props.diretorioTrabalho(), "video-" + video.getId() + "-");
        try {
            Path entrada = trabalho.resolve("original");
            try {
                storage.baixarParaArquivo(payload.uploadChave(), entrada);
            } catch (NoSuchKeyException e) {
                throw new ErroPermanenteException(
                        "O arquivo original não está mais disponível. Envie o vídeo de novo.");
            }
            Runnable renovar = () -> fila.renovar(job, clock.instant());
            renovar.run();

            AnaliseVideo analise = ffmpeg.analisar(entrada, trabalho);
            List<Variante> variantes = Variante.paraOrigem(analise.altura());
            Path saida = Files.createDirectory(trabalho.resolve("hls"));
            long inicio = System.nanoTime();
            ffmpeg.converter(entrada, saida, variantes, analise, renovar);
            long tempoMs = (System.nanoTime() - inicio) / 1_000_000;

            String prefixo = "videos/%d/%d/".formatted(video.getAulaId(), job.id());
            storage.excluirPrefixo(prefixo);
            long tamanhoFinal = enviar(saida, prefixo);
            renovar.run();

            var resultado = new ResultadoTranscodificacao(
                    prefixo + "master.m3u8",
                    prefixo + "thumbnail.jpg",
                    tamanhoFinal,
                    analise.duracaoSegundos(),
                    analise.largura(),
                    analise.altura(),
                    variantes.stream().map(Variante::nome).collect(Collectors.joining(",")),
                    tempoMs);
            Optional<String> anterior = videoAdmService.concluir(video.getId(), resultado);
            storage.excluir(payload.uploadChave());
            anterior.map(EnvioVideoService::prefixoHls)
                    .filter(p -> !p.equals(prefixo))
                    .ifPresent(storage::excluirPrefixo);
            LOG.info(
                    "Vídeo {} convertido: {} s de vídeo em {} ms, {} → {} bytes ({})",
                    video.getId(),
                    analise.duracaoSegundos(),
                    tempoMs,
                    video.getTamanhoOriginal(),
                    tamanhoFinal,
                    resultado.variantes());
        } finally {
            apagarDiretorio(trabalho);
        }
    }

    @Override
    public void desistir(Job job, String motivo) {
        videoAdmService.falharProcessamento(payload(job), motivo);
    }

    /** Envia os arquivos gerados; devolve a soma do HLS (playlists e segmentos), sem a thumbnail. */
    private long enviar(Path saida, String prefixo) throws IOException {
        long total = 0;
        try (Stream<Path> arquivos = Files.walk(saida)) {
            for (Path arquivo : arquivos.filter(Files::isRegularFile).toList()) {
                String relativo = saida.relativize(arquivo).toString().replace('\\', '/');
                String tipo = contentType(relativo);
                storage.gravarArquivo(prefixo + relativo, arquivo, tipo);
                if (!tipo.startsWith("image/")) {
                    total += Files.size(arquivo);
                }
            }
        }
        return total;
    }

    static String contentType(String arquivo) {
        if (arquivo.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (arquivo.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (arquivo.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private PayloadTranscodificacao payload(Job job) {
        return jsonMapper.readValue(job.payload(), PayloadTranscodificacao.class);
    }

    private static void apagarDiretorio(Path diretorio) {
        try (Stream<Path> caminhos = Files.walk(diretorio)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(caminho -> {
                try {
                    Files.deleteIfExists(caminho);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Não foi possível apagar o diretório de trabalho {}: {}", diretorio, e.toString());
        }
    }
}
`````

### 6. API

`backend/src/main/java/br/com/celke/cursos/video/api/VideoRequests.java`:

`````java
package br.com.celke.cursos.video.api;

import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.domain.DivisaoPartes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas de vídeo do ADM. */
public final class VideoRequests {

    private VideoRequests() {}

    /** O tipo e o tamanho são conferidos de novo no arquivo real, depois do envio. */
    public record IniciarEnvio(
            @NotBlank @Size(max = 255) String nomeArquivo,
            @NotBlank String contentType,
            @Positive long tamanho) {}

    /** O ETag de cada parte vem no cabeçalho {@code ETag} da resposta do PUT da parte. */
    public record ConcluirEnvio(
            @NotEmpty @Size(max = DivisaoPartes.MAXIMO_PARTES)
            List<@Valid @NotNull Parte> partes) {

        public List<ParteEnviada> enviadas() {
            return partes.stream()
                    .map(p -> new ParteEnviada(p.numero(), p.etag()))
                    .toList();
        }
    }

    public record Parte(
            @Min(1) @Max(DivisaoPartes.MAXIMO_PARTES) int numero,
            @NotBlank @Size(max = 200) String etag) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/video/api/VideoResponses.java`:

`````java
package br.com.celke.cursos.video.api;

import br.com.celke.cursos.video.application.EnvioVideoService.EnvioIniciado;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de vídeo do ADM. */
public final class VideoResponses {

    private VideoResponses() {}

    /**
     * Vídeo da aula na gestão. {@code thumbnailUrl} é temporária (vale uma hora). Os dados da conversão
     * são os da última que deu certo; {@code status} e {@code erro} são os do último envio.
     */
    public record VideoAdm(
            Long aulaId,
            StatusVideo status,
            boolean publicado,
            String nomeOriginal,
            Long tamanhoOriginal,
            Long tamanhoFinal,
            Double reducaoPercentual,
            Integer duracaoSegundos,
            Integer largura,
            Integer altura,
            List<String> variantes,
            Double segundosProcessamentoPorMinuto,
            String thumbnailUrl,
            String erro,
            Instant processadoEm,
            Instant atualizadoEm) {

        public static VideoAdm de(Video video, Optional<URL> thumbnail) {
            return new VideoAdm(
                    video.getAulaId(),
                    video.getStatus(),
                    video.getPlaylistChave() != null,
                    video.getNomeOriginal(),
                    video.getTamanhoOriginal(),
                    video.getTamanhoFinal(),
                    video.reducaoPercentual(),
                    video.getDuracaoSegundos(),
                    video.getLargura(),
                    video.getAltura(),
                    video.getVariantes() == null
                            ? List.of()
                            : List.of(video.getVariantes().split(",")),
                    video.segundosPorMinutoDeVideo(),
                    thumbnail.map(URL::toString).orElse(null),
                    video.getErro(),
                    video.getProcessadoEm(),
                    video.getAtualizadoEm());
        }
    }

    /**
     * Onde enviar cada parte: PUT do pedaço do arquivo ({@code tamanhoParte} bytes; a última parte leva
     * o resto) na {@code url} da parte, guardando o cabeçalho {@code ETag} da resposta para a conclusão.
     */
    public record EnvioVideoResponse(long tamanhoParte, List<ParteUrl> partes, Instant expiraEm) {

        public static EnvioVideoResponse de(EnvioIniciado envio) {
            return new EnvioVideoResponse(
                    envio.tamanhoParte(),
                    envio.partes().stream()
                            .map(p -> new ParteUrl(p.numero(), p.url().toString()))
                            .toList(),
                    envio.expiraEm());
        }
    }

    public record ParteUrl(int numero, String url) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/video/api/VideoAdmController.java`:

`````java
package br.com.celke.cursos.video.api;

import br.com.celke.cursos.video.api.VideoResponses.EnvioVideoResponse;
import br.com.celke.cursos.video.api.VideoResponses.VideoAdm;
import br.com.celke.cursos.video.application.EnvioVideoService;
import br.com.celke.cursos.video.application.VideoAdmService;
import br.com.celke.cursos.video.domain.Video;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/aulas/{aulaId}/video")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Vídeos")
public class VideoAdmController {

    private final VideoAdmService videoAdmService;
    private final EnvioVideoService envioVideoService;

    public VideoAdmController(VideoAdmService videoAdmService, EnvioVideoService envioVideoService) {
        this.videoAdmService = videoAdmService;
        this.envioVideoService = envioVideoService;
    }

    @GetMapping
    @Operation(
            summary = "Vídeo da aula",
            description = "Status do processamento, tamanho antes e depois, duração, variantes e thumbnail.")
    public VideoAdm buscar(@PathVariable Long aulaId) {
        return resposta(videoAdmService.buscarPorAula(aulaId));
    }

    @PostMapping("/envio")
    @Operation(
            summary = "Iniciar o envio do vídeo",
            description = "MP4, MOV, MKV ou WebM. Devolve uma URL pré-assinada por parte, para o navegador enviar"
                    + " direto ao armazenamento. Substitui o vídeo atual quando a conversão do novo terminar.")
    public EnvioVideoResponse iniciarEnvio(
            @PathVariable Long aulaId, @Valid @RequestBody VideoRequests.IniciarEnvio request) {
        return EnvioVideoResponse.de(
                envioVideoService.iniciar(aulaId, request.nomeArquivo(), request.contentType(), request.tamanho()));
    }

    @PostMapping("/envio/conclusao")
    @Operation(
            summary = "Concluir o envio",
            description = "Junta as partes, confere o tamanho e o tipo real do arquivo e coloca o vídeo na fila.")
    public ResponseEntity<VideoAdm> concluirEnvio(
            @PathVariable Long aulaId, @Valid @RequestBody VideoRequests.ConcluirEnvio request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(resposta(envioVideoService.concluir(aulaId, request.enviadas())));
    }

    @PostMapping("/reprocessamento")
    @Operation(summary = "Reprocessar", description = "Só para vídeo com erro cujo original ainda está guardado.")
    public ResponseEntity<VideoAdm> reprocessar(@PathVariable Long aulaId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resposta(envioVideoService.reprocessar(aulaId)));
    }

    @DeleteMapping
    @Operation(
            summary = "Remover o vídeo",
            description = "Apaga o vídeo e os arquivos; não vale durante o processamento.")
    public ResponseEntity<Void> excluir(@PathVariable Long aulaId) {
        envioVideoService.excluir(aulaId);
        return ResponseEntity.noContent().build();
    }

    private VideoAdm resposta(Video video) {
        return VideoAdm.de(video, envioVideoService.urlThumbnail(video));
    }
}
`````

### 7. Fábrica de teste

`backend/src/test/java/br/com/celke/cursos/support/fixtures/VideoFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.Video;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de vídeos para testes unitários (sem banco: o id é preenchido aqui). */
public final class VideoFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");
    public static final String UPLOAD = "uploads/videos/9/original";
    public static final String PLAYLIST = "videos/9/1/master.m3u8";

    private VideoFixture() {}

    /** Envio aberto (upload em partes {@code upload-1}) de um arquivo de 1000 bytes na aula 9. */
    public static Video aguardandoEnvio(long id) {
        Video video = new Video(9L, CRIADO_EM);
        ReflectionTestUtils.setField(video, "id", id);
        video.iniciarEnvio(UPLOAD, "upload-1", "aula.mp4", 1000, CRIADO_EM);
        return video;
    }

    public static Video pendente(long id) {
        Video video = aguardandoEnvio(id);
        video.enfileirar(1000, CRIADO_EM);
        return video;
    }

    public static Video comErro(long id) {
        Video video = pendente(id);
        video.falhar("falhou", CRIADO_EM);
        return video;
    }

    /** Convertido, com o HLS em {@link #PLAYLIST}. */
    public static Video pronto(long id) {
        Video video = pendente(id);
        video.iniciarProcessamento(CRIADO_EM);
        video.concluir(
                new ResultadoTranscodificacao(PLAYLIST, "videos/9/1/thumbnail.jpg", 500, 3, 1280, 720, "720p", 1000),
                CRIADO_EM);
        return video;
    }
}
`````

### 8. Vídeo de amostra

`backend/src/test/resources/amostras/video-3s.mp4` (3 s, 1280 × 720, com áudio; usado pela integração
e pelo e2e). Na pasta `backend/`:

```bash
mkdir -p src/test/resources/amostras
ffmpeg -y -f lavfi -i "testsrc=size=1280x720:rate=24" -f lavfi -i "sine=frequency=440:sample_rate=48000" \
  -t 3 -c:v libx264 -preset veryfast -crf 35 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart \
  src/test/resources/amostras/video-3s.mp4
```

### 9. Testes unitários

`backend/src/test/java/br/com/celke/cursos/shared/fila/ProcessadorFilaTest.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessadorFilaTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private FilaService fila;

    @Mock
    private ManipuladorJob manipulador;

    private ProcessadorFila processador;

    @BeforeEach
    void criar() {
        when(manipulador.tipo()).thenReturn("EMAIL");
        processador = new ProcessadorFila(fila, List.of(manipulador), Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Job job(int tentativas) {
        return new Job(7, "EMAIL", "{}", tentativas, 5);
    }

    @Test
    @DisplayName("sem job disponível devolve false e não chama o manipulador")
    void filaVazia() throws Exception {
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.empty());

        assertThat(processador.processarProximo()).isFalse();
        verify(manipulador, never()).processar(any());
    }

    @Test
    @DisplayName("job processado com sucesso é concluído")
    void sucesso() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("falha comum agenda nova tentativa com o erro")
    void falhaComum() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("SMTP fora do ar")).when(manipulador).processar(job);

        assertThat(processador.processarProximo()).isTrue();

        verify(fila).falhar(job, "java.lang.IllegalStateException: SMTP fora do ar", AGORA);
        verify(fila, never()).concluir(any(), any());
    }

    @Test
    @DisplayName("erro permanente vai direto para ERRO, sem nova tentativa")
    void erroPermanente() throws Exception {
        Job job = job(1);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new ErroPermanenteException("Nome de template inválido."))
                .when(manipulador)
                .processar(job);

        processador.processarProximo();

        verify(fila).falharDefinitivo(job, "Nome de template inválido.", AGORA);
        verify(fila, never()).falhar(any(), anyString(), any());
    }

    @Test
    @DisplayName("job que passou do limite de tentativas vai para ERRO sem ser processado")
    void tentativasExcedidas() throws Exception {
        Job job = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        assertThat(processador.processarProximo()).isTrue();

        verify(manipulador, never()).processar(any());
        verify(fila)
                .falharDefinitivo(eq(job), eq("Tentativas esgotadas (o processamento foi interrompido)."), eq(AGORA));
    }

    @Test
    @DisplayName("falha comum antes da última tentativa não avisa o manipulador")
    void falhaComumNaoDesiste() throws Exception {
        Job job = job(4);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("fora do ar")).when(manipulador).processar(job);

        processador.processarProximo();

        verify(manipulador, never()).desistir(any(), anyString());
    }

    @Test
    @DisplayName("falha comum na última tentativa avisa o manipulador da desistência")
    void falhaNaUltimaTentativaDesiste() throws Exception {
        Job job = job(5);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("fora do ar")).when(manipulador).processar(job);

        processador.processarProximo();

        verify(fila).falhar(job, "java.lang.IllegalStateException: fora do ar", AGORA);
        verify(manipulador).desistir(job, "java.lang.IllegalStateException: fora do ar");
    }

    @Test
    @DisplayName("erro permanente e tentativas excedidas avisam o manipulador")
    void desistencias() throws Exception {
        Job permanente = job(1);
        Job excedido = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA))
                .thenReturn(Optional.of(permanente))
                .thenReturn(Optional.of(excedido));
        doThrow(new ErroPermanenteException("Arquivo inválido."))
                .when(manipulador)
                .processar(permanente);

        processador.processarProximo();
        processador.processarProximo();

        verify(manipulador).desistir(permanente, "Arquivo inválido.");
        verify(manipulador).desistir(excedido, "Tentativas esgotadas (o processamento foi interrompido).");
    }

    @Test
    @DisplayName("falha do manipulador ao registrar a desistência não desfaz o estado da fila")
    void falhaAoDesistir() throws Exception {
        Job job = job(6);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("banco fora do ar")).when(manipulador).desistir(eq(job), anyString());

        assertThat(processador.processarProximo()).isTrue();

        verify(fila).falharDefinitivo(eq(job), anyString(), eq(AGORA));
    }

    @Test
    @DisplayName("job na última tentativa permitida ainda é processado")
    void ultimaTentativaPermitida() throws Exception {
        Job job = job(5);
        when(fila.reservar(Set.of("EMAIL"), AGORA)).thenReturn(Optional.of(job));

        processador.processarProximo();

        verify(manipulador).processar(job);
        verify(fila).concluir(job, AGORA);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/domain/FormatoVideoTest.java`:

`````java
package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatoVideoTest {

    private static byte[] hex(String valor) {
        return HexFormat.of().parseHex(valor);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "video/mp4,         MP4",
        "video/quicktime,   MOV",
        "video/x-matroska,  MKV",
        "video/webm,        WEBM",
    })
    @DisplayName("reconhece os tipos aceitos")
    void tiposAceitos(String contentType, FormatoVideo esperado) {
        assertThat(FormatoVideo.doContentType(contentType)).contains(esperado);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"video/avi", "image/png", "VIDEO/MP4", "''"})
    @DisplayName("recusa tipos fora da lista (inclusive com outra caixa)")
    void tiposRecusados(String contentType) {
        assertThat(FormatoVideo.doContentType(contentType)).isEmpty();
    }

    @Test
    @DisplayName("recusa tipo nulo")
    void tipoNulo() {
        assertThat(FormatoVideo.doContentType(null)).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
        "MP4,  000000206674797069736f6d, true",
        "MOV,  0000001466747970717420, true",
        "MP4,  1a45dfa39f4286810142f781, false",
        "MKV,  1a45dfa39f4286810142f781, true",
        "WEBM, 1a45dfa3, true",
        "WEBM, 000000206674797069736f6d, false",
        "MP4,  3c68746d6c3e3c7363726970, false",
        "MP4,  00000020667479, false",
        "MKV,  1a45df, false",
    })
    @DisplayName("confere a assinatura real do início do arquivo")
    void assinatura(FormatoVideo formato, String inicio, boolean esperado) {
        assertThat(formato.assinaturaConfere(hex(inicio))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("texto com extensão de vídeo não passa como MP4")
    void textoComExtensaoFalsa() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThat(FormatoVideo.MP4.assinaturaConfere(html)).isFalse();
        assertThat(FormatoVideo.WEBM.assinaturaConfere(html)).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/domain/DivisaoPartesTest.java`:

`````java
package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DivisaoPartesTest {

    private static final long MIB = 1024L * 1024;

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "1 byte,                          1,           16777216, 1",
        "exatamente uma parte,            16777216,    16777216, 1",
        "um byte além de uma parte,       16777217,    16777216, 2",
        "100 MiB,                         104857600,   16777216, 7",
        "limite de 10.000 partes de 16 MiB, 167772160000, 16777216, 10000",
        "um byte além do limite,          167772160001, 17825792, 9412",
        "5 GiB,                           5368709120,  16777216, 320",
        "500 GiB,                         536870912000, 54525952, 9847",
    })
    @DisplayName("partes de 16 MiB até 10.000 partes; acima disso, partes maiores em múltiplos de 1 MiB")
    void divisao(String caso, long tamanho, long parte, int quantidade) {
        DivisaoPartes divisao = DivisaoPartes.de(tamanho);

        assertThat(divisao.tamanhoParte()).isEqualTo(parte);
        assertThat(divisao.quantidade()).isEqualTo(quantidade);
        assertThat(divisao.quantidade()).isLessThanOrEqualTo(DivisaoPartes.MAXIMO_PARTES);
        assertThat(divisao.tamanhoParte() % MIB).isZero();
        assertThat(divisao.tamanhoParte() * divisao.quantidade()).isGreaterThanOrEqualTo(tamanho);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("tamanho zero ou negativo é recusado")
    void tamanhoInvalido(long tamanho) {
        assertThatThrownBy(() -> DivisaoPartes.de(tamanho))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O tamanho precisa ser positivo.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/domain/VarianteTest.java`:

`````java
package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VarianteTest {

    @ParameterizedTest(name = "origem {0} linhas → {1}")
    @CsvSource({
        "2160, 720p 1080p",
        "1081, 720p 1080p",
        "1080, 720p 1080p",
        "1079, 720p",
        "720,  720p",
        "719,  718p",
        "480,  480p",
        "361,  360p",
        "2,    2p",
    })
    @DisplayName("nunca aumenta a resolução: só as alturas-alvo que cabem, ou a própria altura (par)")
    void variantesPorOrigem(int alturaOrigem, String esperadas) {
        assertThat(Variante.paraOrigem(alturaOrigem).stream().map(Variante::nome))
                .containsExactly(esperadas.split(" "));
    }

    @Test
    @DisplayName("tetos de taxa: 2800k em 720p e 5000k em 1080p, com buffer do dobro")
    void tetosPadrao() {
        assertThat(Variante.paraOrigem(1080)).containsExactly(new Variante(720, 2800), new Variante(1080, 5000));
        assertThat(new Variante(1080, 5000).bufsizeKbps()).isEqualTo(10_000);
    }

    @ParameterizedTest(name = "{0} linhas → {1} kbps")
    @CsvSource({"480, 1920", "360, 1440", "150, 600", "100, 600"})
    @DisplayName("abaixo de 720p, o teto é proporcional à altura, com mínimo de 600 kbps")
    void tetoProporcional(int altura, int kbps) {
        assertThat(Variante.paraOrigem(altura)).isEqualTo(List.of(new Variante(altura, kbps)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 0, -720})
    @DisplayName("altura de origem inválida é recusada")
    void alturaInvalida(int altura) {
        assertThatThrownBy(() -> Variante.paraOrigem(altura))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Altura de origem inválida: " + altura);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/domain/VideoTest.java`:

`````java
package br.com.celke.cursos.video.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VideoTest {

    private static final Instant T0 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static Video aguardandoEnvio() {
        Video video = new Video(9L, T0);
        video.iniciarEnvio("uploads/videos/9/a", "upload-a", "aula.mp4", 1000, T0);
        return video;
    }

    private static Video pronto(long tamanhoOriginal, long tamanhoFinal, int duracao, long tempoMs) {
        Video video = new Video(9L, T0);
        video.iniciarEnvio("uploads/videos/9/a", "upload-a", "aula.mp4", tamanhoOriginal, T0);
        video.enfileirar(tamanhoOriginal, T0);
        video.iniciarProcessamento(T0);
        video.concluir(resultado("videos/9/1/master.m3u8", tamanhoFinal, duracao, tempoMs), T1);
        return video;
    }

    private static ResultadoTranscodificacao resultado(String playlist, long tamanhoFinal, int duracao, long tempoMs) {
        return new ResultadoTranscodificacao(
                playlist, "videos/9/1/thumbnail.jpg", tamanhoFinal, duracao, 1920, 1080, "720p,1080p", tempoMs);
    }

    @Test
    @DisplayName("vídeo novo aguarda o envio")
    void novo() {
        Video video = new Video(9L, T0);

        assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
        assertThat(video.getAulaId()).isEqualTo(9L);
        assertThat(video.getAtualizadoEm()).isEqualTo(T0);
    }

    @Nested
    class Ciclo {

        @Test
        @DisplayName("envio → fila → processamento → pronto: publica o HLS e esquece o original")
        void cicloCompleto() {
            Video video = aguardandoEnvio();
            assertThat(video.getUploadId()).isEqualTo("upload-a");

            video.enfileirar(1234, T0);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getUploadId()).isNull();
            assertThat(video.getTamanhoOriginal()).isEqualTo(1234);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/a");

            video.iniciarProcessamento(T0);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PROCESSANDO);

            String anterior = video.concluir(resultado("videos/9/1/master.m3u8", 600, 30, 15_000), T1);
            assertThat(anterior).isNull();
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getThumbnailChave()).isEqualTo("videos/9/1/thumbnail.jpg");
            assertThat(video.getTamanhoFinal()).isEqualTo(600);
            assertThat(video.getDuracaoSegundos()).isEqualTo(30);
            assertThat(video.getLargura()).isEqualTo(1920);
            assertThat(video.getAltura()).isEqualTo(1080);
            assertThat(video.getVariantes()).isEqualTo("720p,1080p");
            assertThat(video.getTempoProcessamentoMs()).isEqualTo(15_000);
            assertThat(video.getProcessadoEm()).isEqualTo(T1);
            assertThat(video.getUploadChave()).isNull();
        }

        @Test
        @DisplayName("novo envio num vídeo pronto mantém o HLS publicado até a nova conversão, que devolve o anterior")
        void substituicao() {
            Video video = pronto(1000, 500, 60, 1000);

            video.iniciarEnvio("uploads/videos/9/b", "upload-b", "nova.mov", 2000, T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
            assertThat(video.getPlaylistChave()).as("continua no ar").isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getNomeOriginal()).isEqualTo("nova.mov");

            video.enfileirar(2000, T1);
            video.iniciarProcessamento(T1);
            assertThat(video.concluir(resultado("videos/9/2/master.m3u8", 900, 60, 1000), T1))
                    .isEqualTo("videos/9/1/master.m3u8");
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/2/master.m3u8");
        }

        @Test
        @DisplayName("erro guarda a mensagem; reprocessar volta para a fila e limpa o erro")
        void erroEReprocessamento() {
            Video video = aguardandoEnvio();
            video.enfileirar(1000, T0);
            video.falhar("ffmpeg falhou", T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(video.getErro()).isEqualTo("ffmpeg falhou");
            assertThat(video.getUploadChave())
                    .as("original guardado para reprocessar")
                    .isNotNull();

            video.reprocessar(T1);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getErro()).isNull();
        }

        @Test
        @DisplayName("envio novo depois de um erro limpa a mensagem")
        void envioDepoisDoErro() {
            Video video = aguardandoEnvio();
            video.falhar("recusado", T0);

            video.iniciarEnvio("uploads/videos/9/c", "upload-c", "c.mp4", 10, T1);

            assertThat(video.getErro()).isNull();
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
        }

        @Test
        @DisplayName("descartar o original apaga a referência ao upload")
        void descartarOriginal() {
            Video video = aguardandoEnvio();

            video.descartarOriginal(T1);

            assertThat(video.getUploadChave()).isNull();
            assertThat(video.getUploadId()).isNull();
        }

        @Test
        @DisplayName("mensagem de erro longa guarda só o fim (onde o ffmpeg diz o motivo)")
        void erroLongo() {
            Video video = aguardandoEnvio();
            String longa = "a".repeat(Video.TAMANHO_ERRO) + "motivo";

            video.falhar(longa, T0);

            assertThat(video.getErro()).hasSize(Video.TAMANHO_ERRO).endsWith("motivo");
        }
    }

    @Nested
    class TransicoesProibidas {

        @Test
        @DisplayName("não inicia envio com o vídeo na fila ou com o worker")
        void envioDuranteProcessamento() {
            Video video = aguardandoEnvio();
            video.enfileirar(1000, T0);

            assertThatThrownBy(() -> video.iniciarEnvio("x", "y", "z.mp4", 1, T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("O vídeo está em processamento.");
            video.iniciarProcessamento(T0);
            assertThatThrownBy(video::exigirForaDoProcessamento).isInstanceOf(IllegalStateException.class);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/a");
        }

        @Test
        @DisplayName("só um envio em andamento vai para a fila")
        void enfileirarForaDoEnvio() {
            Video video = pronto(1000, 500, 60, 1000);

            assertThatThrownBy(() -> video.enfileirar(10, T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Só um envio em andamento pode ir para a fila.");
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }

        @Test
        @DisplayName("só vídeo com erro é reprocessado")
        void reprocessarSemErro() {
            Video video = pronto(1000, 500, 60, 1000);

            assertThatThrownBy(() -> video.reprocessar(T1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Só um vídeo com erro pode ser reprocessado.");
            assertThat(video.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }
    }

    @Nested
    class Medidas {

        @ParameterizedTest(name = "{0} → {1} bytes: {2}%")
        @CsvSource({"1000, 250, 75.0", "1000, 1000, 0.0", "1000, 1500, -50.0", "3, 1, 66.7", "3, 2, 33.3"})
        @DisplayName("redução do original para o HLS, com uma casa decimal")
        void reducao(long original, long fim, double esperado) {
            assertThat(pronto(original, fim, 60, 1000).reducaoPercentual()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem conversão concluída não há redução")
        void semReducao() {
            assertThat(aguardandoEnvio().reducaoPercentual()).isNull();
        }

        @ParameterizedTest(name = "{1} ms para {0} s → {2} s/min")
        @CsvSource({"60, 30000, 30.0", "120, 30000, 15.0", "3, 1500, 30.0", "90, 1000, 0.7"})
        @DisplayName("segundos de processamento por minuto de vídeo")
        void tempoPorMinuto(int duracao, long tempoMs, double esperado) {
            assertThat(pronto(1000, 500, duracao, tempoMs).segundosPorMinutoDeVideo())
                    .isEqualTo(esperado);
        }

        @Test
        @DisplayName("sem duração (ou zero) não há medida de tempo")
        void semTempo() {
            assertThat(aguardandoEnvio().segundosPorMinutoDeVideo()).isNull();
            assertThat(pronto(1000, 500, 0, 1000).segundosPorMinutoDeVideo()).isNull();
        }
    }

    @Test
    @DisplayName("só PENDENTE e PROCESSANDO contam como em processamento")
    void emProcessamento() {
        assertThat(StatusVideo.values())
                .filteredOn(StatusVideo::emProcessamento)
                .containsExactly(StatusVideo.PENDENTE, StatusVideo.PROCESSANDO);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/infra/ComandosFfmpegTest.java`:

`````java
package br.com.celke.cursos.video.infra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.video.domain.Variante;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ComandosFfmpegTest {

    private static final Path ENTRADA = Path.of("/tmp/trabalho/original");
    private final ComandosFfmpeg comandos = new ComandosFfmpeg("ffmpeg", "ffprobe", "veryfast", 24);
    private final List<Variante> duas = Variante.paraOrigem(1080);

    /** Valor do argumento logo depois da opção. */
    private static String valor(List<String> args, String opcao) {
        return args.get(args.indexOf(opcao) + 1);
    }

    @Test
    @DisplayName("ffprobe devolve formato e faixas em JSON")
    void analisar() {
        assertThat(comandos.analisar(ENTRADA))
                .containsExactly(
                        "ffprobe",
                        "-v",
                        "error",
                        "-print_format",
                        "json",
                        "-show_format",
                        "-show_streams",
                        ENTRADA.toString());
    }

    @Nested
    class ConverterHls {

        @Test
        @DisplayName("H.264 com CRF e preset configurados, teto de taxa por variante e segmentos de 6 s")
        void parametros() {
            List<String> args = comandos.converterHls(ENTRADA, duas, true);

            assertThat(args.getFirst()).isEqualTo("ffmpeg");
            assertThat(valor(args, "-i")).isEqualTo(ENTRADA.toString());
            assertThat(valor(args, "-c:v")).isEqualTo("libx264");
            assertThat(valor(args, "-preset")).isEqualTo("veryfast");
            assertThat(valor(args, "-crf")).isEqualTo("24");
            assertThat(valor(args, "-maxrate:v:0")).isEqualTo("2800k");
            assertThat(valor(args, "-bufsize:v:0")).isEqualTo("5600k");
            assertThat(valor(args, "-maxrate:v:1")).isEqualTo("5000k");
            assertThat(valor(args, "-bufsize:v:1")).isEqualTo("10000k");
            assertThat(valor(args, "-hls_time")).isEqualTo("6");
            assertThat(valor(args, "-force_key_frames")).isEqualTo("expr:gte(t,n_forced*6)");
            assertThat(valor(args, "-hls_playlist_type")).isEqualTo("vod");
            assertThat(valor(args, "-master_pl_name")).isEqualTo("master.m3u8");
            assertThat(valor(args, "-hls_segment_filename")).isEqualTo("%v/seg_%04d.ts");
            assertThat(args.getLast()).isEqualTo("%v/index.m3u8");
            assertThat(args).contains("-nostdin");
        }

        @Test
        @DisplayName("com áudio: AAC 128k estéreo, uma faixa de áudio por variante")
        void comAudio() {
            List<String> args = comandos.converterHls(ENTRADA, duas, true);

            assertThat(valor(args, "-c:a")).isEqualTo("aac");
            assertThat(valor(args, "-b:a")).isEqualTo("128k");
            assertThat(valor(args, "-ac")).isEqualTo("2");
            assertThat(args.stream().filter("0:a:0"::equals)).hasSize(2);
            assertThat(valor(args, "-var_stream_map")).isEqualTo("v:0,a:0,name:720p v:1,a:1,name:1080p");
            assertThat(valor(args, "-filter_complex"))
                    .isEqualTo("[0:v]split=2[v0][v1];[v0]scale=-2:720[s0];[v1]scale=-2:1080[s1]");
        }

        @Test
        @DisplayName("sem áudio: nenhuma opção de áudio e o mapa só com vídeo")
        void semAudio() {
            List<String> args = comandos.converterHls(ENTRADA, List.of(new Variante(480, 1920)), false);

            assertThat(args).doesNotContain("-c:a", "0:a:0");
            assertThat(valor(args, "-var_stream_map")).isEqualTo("v:0,name:480p");
            assertThat(valor(args, "-filter_complex")).isEqualTo("[0:v]split=1[v0];[v0]scale=-2:480[s0]");
            assertThat(valor(args, "-map")).isEqualTo("[s0]");
        }

        @Test
        @DisplayName("nome de arquivo com caracteres de shell vira um argumento só, sem interpretação")
        void semShell() {
            Path perigoso = Path.of("/tmp/a; rm -rf ~ $(whoami).mp4");

            List<String> args = comandos.converterHls(perigoso, duas, true);

            assertThat(valor(args, "-i")).isEqualTo(perigoso.toString());
            assertThat(args).noneMatch(a -> a.equals("sh") || a.equals("-c") || a.equals("bash"));
        }
    }

    @Nested
    class Thumbnail {

        @Test
        @DisplayName("quadro a 10% da duração, com até 720 linhas")
        void dezPorCento() {
            List<String> args = comandos.thumbnail(ENTRADA, 30, 1080);

            assertThat(valor(args, "-ss")).isEqualTo("3.000");
            assertThat(valor(args, "-vf")).isEqualTo("scale=-2:720");
            assertThat(valor(args, "-frames:v")).isEqualTo("1");
            assertThat(args.getLast()).isEqualTo("thumbnail.jpg");
        }

        @Test
        @DisplayName("vídeo longo: no máximo aos 10 s; origem pequena não é ampliada (altura par)")
        void limites() {
            List<String> args = comandos.thumbnail(ENTRADA, 3600, 361);

            assertThat(valor(args, "-ss")).isEqualTo("10.000");
            assertThat(valor(args, "-vf")).isEqualTo("scale=-2:360");
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/infra/AnaliseVideoTest.java`:

`````java
package br.com.celke.cursos.video.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.fila.ErroPermanenteException;
import br.com.celke.cursos.video.infra.AnaliseVideo.Faixa;
import br.com.celke.cursos.video.infra.AnaliseVideo.Formato;
import br.com.celke.cursos.video.infra.AnaliseVideo.SaidaFfprobe;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class AnaliseVideoTest {

    private static final Faixa VIDEO = new Faixa("video", 1920, 1080);
    private static final Faixa AUDIO = new Faixa("audio", null, null);

    @Test
    @DisplayName("lê a saída real do ffprobe: dimensões da faixa de vídeo, duração e áudio")
    void saidaDoFfprobe() {
        String json = """
                {"streams":[{"index":0,"codec_type":"video","width":1280,"height":720,"r_frame_rate":"24/1"},
                            {"index":1,"codec_type":"audio","sample_rate":"48000"}],
                 "format":{"filename":"original","duration":"3.000000","size":"43649"}}
                """;
        SaidaFfprobe saida = JsonMapper.builder().build().readValue(json, SaidaFfprobe.class);

        assertThat(AnaliseVideo.de(saida)).isEqualTo(new AnaliseVideo(3.0, 1280, 720, true));
    }

    @Test
    @DisplayName("sem faixa de áudio")
    void semAudio() {
        assertThat(AnaliseVideo.de(new SaidaFfprobe(List.of(VIDEO), new Formato("10.5"))))
                .isEqualTo(new AnaliseVideo(10.5, 1920, 1080, false));
    }

    @Test
    @DisplayName("usa a primeira faixa de vídeo com dimensões (capa embutida sem dimensões é ignorada)")
    void primeiraFaixaValida() {
        var saida = new SaidaFfprobe(
                List.of(AUDIO, new Faixa("video", null, null), new Faixa("video", 640, 360), VIDEO), new Formato("1"));

        assertThat(AnaliseVideo.de(saida).altura()).isEqualTo(360);
    }

    @ParameterizedTest(name = "{0} s → {1}")
    @CsvSource({"2.4, 2", "2.5, 3", "3599.6, 3600"})
    @DisplayName("duração em segundos inteiros, arredondada")
    void duracaoSegundos(double duracao, int esperado) {
        assertThat(new AnaliseVideo(duracao, 1, 1, false).duracaoSegundos()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("arquivo sem faixa de vídeo (só áudio, vazio ou sem resposta) é erro permanente")
    void semVideo() {
        for (SaidaFfprobe saida : List.of(
                new SaidaFfprobe(List.of(AUDIO), new Formato("3")),
                new SaidaFfprobe(List.of(new Faixa("video", 0, 720)), new Formato("3")),
                new SaidaFfprobe(null, new Formato("3")))) {
            assertThatThrownBy(() -> AnaliseVideo.de(saida))
                    .isInstanceOf(ErroPermanenteException.class)
                    .hasMessage("O arquivo não tem uma faixa de vídeo.");
        }
        assertThatThrownBy(() -> AnaliseVideo.de(null)).isInstanceOf(ErroPermanenteException.class);
    }

    @ParameterizedTest(name = "duração \"{0}\"")
    @NullSource
    @ValueSource(strings = {"N/A", "0", "-1", "NaN", "Infinity"})
    @DisplayName("duração ausente, zero ou inválida é erro permanente")
    void duracaoInvalida(String duracao) {
        var saida = new SaidaFfprobe(List.of(VIDEO), new Formato(duracao));

        assertThatThrownBy(() -> AnaliseVideo.de(saida))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Não foi possível ler a duração do vídeo.");
    }

    @Test
    @DisplayName("sem o bloco de formato é erro permanente")
    void semFormato() {
        assertThatThrownBy(() -> AnaliseVideo.de(new SaidaFfprobe(List.of(VIDEO), null)))
                .isInstanceOf(ErroPermanenteException.class)
                .hasMessage("Não foi possível ler a duração do vídeo.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/api/VideoRequestsTest.java`:

`````java
package br.com.celke.cursos.video.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.video.api.VideoRequests.ConcluirEnvio;
import br.com.celke.cursos.video.api.VideoRequests.IniciarEnvio;
import br.com.celke.cursos.video.api.VideoRequests.Parte;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("payload válido passa")
        void valido() {
            assertThat(camposInvalidos(new IniciarEnvio("aula 1.mp4", "video/mp4", 1)))
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        @DisplayName("tamanho zero ou negativo é recusado")
        void tamanho(long tamanho) {
            assertThat(camposInvalidos(new IniciarEnvio("a.mp4", "video/mp4", tamanho)))
                    .containsExactly("tamanho");
        }

        @Test
        @DisplayName("nome e tipo obrigatórios; nome com até 255 caracteres")
        void obrigatorios() {
            assertThat(camposInvalidos(new IniciarEnvio(" ", null, 10)))
                    .containsExactlyInAnyOrder("nomeArquivo", "contentType");
            assertThat(camposInvalidos(new IniciarEnvio("a".repeat(256), "video/mp4", 10)))
                    .containsExactly("nomeArquivo");
            assertThat(camposInvalidos(new IniciarEnvio("a".repeat(255), "video/mp4", 10)))
                    .isEmpty();
        }
    }

    @Nested
    class Concluir {

        @Test
        @DisplayName("payload válido passa e vira a lista de partes enviadas")
        void valido() {
            var dto = new ConcluirEnvio(List.of(new Parte(1, "\"abc\""), new Parte(2, "\"def\"")));

            assertThat(camposInvalidos(dto)).isEmpty();
            assertThat(dto.enviadas()).containsExactly(new ParteEnviada(1, "\"abc\""), new ParteEnviada(2, "\"def\""));
        }

        @Test
        @DisplayName("lista vazia ou nula é recusada")
        void listaVazia() {
            assertThat(camposInvalidos(new ConcluirEnvio(List.of()))).containsExactly("partes");
            assertThat(camposInvalidos(new ConcluirEnvio(null))).containsExactly("partes");
        }

        @Test
        @DisplayName("mais de 10.000 partes é recusado")
        void partesDemais() {
            List<Parte> partes = new ArrayList<>(Collections.nCopies(10_001, new Parte(1, "e")));

            assertThat(camposInvalidos(new ConcluirEnvio(partes))).containsExactly("partes");
        }

        @Test
        @DisplayName("cada parte: número de 1 a 10.000 e ETag obrigatório")
        void parte() {
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(0, "e")))))
                    .containsExactly("partes[0].numero");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(10_001, "e")))))
                    .containsExactly("partes[0].numero");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(10_000, " ")))))
                    .containsExactly("partes[0].etag");
            assertThat(camposInvalidos(new ConcluirEnvio(List.of(new Parte(1, "e".repeat(201))))))
                    .containsExactly("partes[0].etag");
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/application/EnvioVideoServiceTest.java`:

`````java
package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.UPLOAD;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.comErro;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioAnterior;
import br.com.celke.cursos.video.application.VideoAdmService.EnvioRegistrado;
import br.com.celke.cursos.video.domain.Video;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class EnvioVideoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Duration VALIDADE = Duration.ofHours(6);
    private static final byte[] MP4 = HexFormat.of().parseHex("000000206674797069736f6d");
    private static final List<ParteEnviada> PARTES = List.of(new ParteEnviada(1, "e1"));

    @Mock
    private VideoAdmService videoAdmService;

    @Mock
    private StorageService storage;

    private EnvioVideoService service;

    @BeforeEach
    void criar() {
        var props = new VideoProperties(
                DataSize.ofGigabytes(5), VALIDADE, "ffmpeg", "ffprobe", null, "veryfast", 24, Path.of("/tmp/videos"));
        service = new EnvioVideoService(videoAdmService, storage, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static URL url(String caminho) throws Exception {
        return URI.create("http://media.localhost/" + caminho).toURL();
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("abre o upload em partes, registra o envio e devolve uma URL por parte")
        void inicia() throws Exception {
            Video video = aguardandoEnvio(3);
            when(storage.iniciarMultipart(startsWith("uploads/videos/9/"), eq("video/mp4")))
                    .thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(
                            eq(9L),
                            startsWith("uploads/videos/9/"),
                            eq("upload-x"),
                            eq("aula.mp4"),
                            eq(40L * 1024 * 1024)))
                    .thenReturn(new EnvioRegistrado(video, Optional.empty()));
            when(storage.urlParte(startsWith("uploads/videos/9/"), eq("upload-x"), anyInt(), eq(VALIDADE)))
                    .thenAnswer(i -> url("parte-" + i.getArgument(2)));

            var envio = service.iniciar(9L, " aula.mp4 ", "video/mp4", 40L * 1024 * 1024);

            verify(videoAdmService).exigirEnvioPermitido(9L);
            assertThat(envio.tamanhoParte()).isEqualTo(16L * 1024 * 1024);
            assertThat(envio.partes())
                    .extracting(EnvioVideoService.UrlParte::numero)
                    .containsExactly(1, 2, 3);
            assertThat(envio.partes().getLast().url().toString()).endsWith("parte-3");
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(VALIDADE));
            assertThat(envio.video()).isSameAs(video);
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio anterior ainda aberto é abortado; falha ao abortar não atrapalha")
        void abortaAnterior() throws Exception {
            when(storage.iniciarMultipart(anyString(), eq("video/webm"))).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), eq("a.webm"), eq(10L)))
                    .thenReturn(new EnvioRegistrado(
                            aguardandoEnvio(3), Optional.of(new EnvioAnterior("velha", "upload-velho"))));
            doThrow(S3Exception.builder().message("NoSuchUpload").build())
                    .when(storage)
                    .abortarMultipart("velha", "upload-velho");
            when(storage.urlParte(anyString(), eq("upload-x"), eq(1), eq(VALIDADE)))
                    .thenReturn(url("p1"));

            assertThat(service.iniciar(9L, "a.webm", "video/webm", 10).partes()).hasSize(1);
            verify(storage).abortarMultipart("velha", "upload-velho");
        }

        @Test
        @DisplayName("tipo fora da lista é recusado antes de qualquer coisa")
        void tipoRecusado() {
            assertThatThrownBy(() -> service.iniciar(9L, "a.avi", "video/x-msvideo", 10))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Envie um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
            verify(videoAdmService, never()).exigirEnvioPermitido(any());
        }

        @Test
        @DisplayName("acima do tamanho máximo é recusado; exatamente no limite passa")
        void tamanhoMaximo() throws Exception {
            long limite = DataSize.ofGigabytes(5).toBytes();
            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", limite + 1))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo pode ter no máximo 5 GB.");
            verify(storage, never()).iniciarMultipart(anyString(), anyString());

            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("u");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("u"), eq("a.mp4"), eq(limite)))
                    .thenReturn(new EnvioRegistrado(aguardandoEnvio(3), Optional.empty()));
            when(storage.urlParte(anyString(), eq("u"), anyInt(), eq(VALIDADE))).thenReturn(url("p"));
            assertThat(service.iniciar(9L, "a.mp4", "video/mp4", limite).partes())
                    .hasSize(320);
        }

        @Test
        @DisplayName("aula inexistente ou vídeo em processamento: nenhum upload é aberto")
        void bloqueado() {
            doThrow(VideoAdmService.emProcessamento()).when(videoAdmService).exigirEnvioPermitido(9L);

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage, never()).iniciarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("registro recusado (corrida) aborta o upload recém-aberto")
        void registroRecusado() {
            when(storage.iniciarMultipart(anyString(), anyString())).thenReturn("upload-x");
            when(videoAdmService.registrarEnvio(eq(9L), anyString(), eq("upload-x"), anyString(), anyLong()))
                    .thenThrow(VideoAdmService.emProcessamento());

            assertThatThrownBy(() -> service.iniciar(9L, "a.mp4", "video/mp4", 10))
                    .isInstanceOf(ConflitoException.class);
            verify(storage).abortarMultipart(startsWith("uploads/videos/9/"), eq("upload-x"));
        }
    }

    @Nested
    class Concluir {

        @Test
        @DisplayName("junta as partes, confere tamanho e assinatura e coloca na fila")
        void conclui() {
            Video video = aguardandoEnvio(3);
            Video naFila = aguardandoEnvio(3);
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn(MP4);
            when(videoAdmService.enfileirar(3L, 1000)).thenReturn(naFila);

            assertThat(service.concluir(9L, PARTES)).isSameAs(naFila);
            verify(storage).concluirMultipart(UPLOAD, "upload-1", PARTES);
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("parte repetida é recusada antes de juntar")
        void parteRepetida() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));

            assertThatThrownBy(() -> service.concluir(9L, List.of(new ParteEnviada(1, "a"), new ParteEnviada(1, "b"))))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("Cada parte pode ser informada uma vez só.");
            verify(storage, never()).concluirMultipart(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("falha ao juntar (parte faltando ou ETag errado) mantém o envio para nova tentativa")
        void falhaAoJuntar() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            doThrow(S3Exception.builder().message("InvalidPart").build())
                    .when(storage)
                    .concluirMultipart(UPLOAD, "upload-1", PARTES);

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O envio não foi concluído. Confira se todas as partes foram enviadas.");
            verify(videoAdmService, never()).recusarEnvio(any(), anyString());
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("arquivo que sumiu depois de juntado")
        void arquivoSumiu() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O vídeo não foi enviado. Comece um novo envio.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tamanho real diferente do informado: apaga o arquivo e deixa o vídeo com erro")
        void tamanhoDiferente() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1001, "video/mp4")));

            String motivo = "O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.";
            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage(motivo);
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, motivo);
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("conteúdo sem a assinatura do formato (extensão falsa) é apagado e recusado")
        void assinaturaFalsa() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(storage.baixarInicio(UPLOAD, 12)).thenReturn("<html><body>".getBytes());

            assertThatThrownBy(() -> service.concluir(9L, PARTES))
                    .isInstanceOf(VideoInvalidoException.class)
                    .hasMessage("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(storage).excluir(UPLOAD);
            verify(videoAdmService).recusarEnvio(3L, "O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
            verify(videoAdmService, never()).enfileirar(any(), anyLong());
        }

        @Test
        @DisplayName("tipo gravado no bucket fora da lista também é recusado")
        void tipoGravadoInvalido() {
            when(videoAdmService.envioEmAndamento(9L)).thenReturn(aguardandoEnvio(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "text/html")));

            assertThatThrownBy(() -> service.concluir(9L, PARTES)).isInstanceOf(VideoInvalidoException.class);
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).baixarInicio(anyString(), anyInt());
        }
    }

    @Nested
    class Reprocessar {

        @Test
        @DisplayName("com o original no bucket, volta para a fila")
        void reprocessa() {
            Video video = comErro(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(1000, "video/mp4")));
            when(videoAdmService.reprocessar(9L)).thenReturn(video);

            assertThat(service.reprocessar(9L)).isSameAs(video);
        }

        @Test
        @DisplayName("original que expirou no bucket: esquece a referência e pede um envio novo")
        void originalExpirou() {
            when(videoAdmService.buscarPorAula(9L)).thenReturn(comErro(3));
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O arquivo original não está mais disponível. Envie o vídeo de novo.");
            verify(videoAdmService).descartarOriginal(3L);
            verify(videoAdmService, never()).reprocessar(any());
        }

        @Test
        @DisplayName("sem original (já apagado) a regra fica com o VideoAdmService")
        void semOriginal() {
            Video video = pronto(3);
            when(videoAdmService.buscarPorAula(9L)).thenReturn(video);
            when(videoAdmService.reprocessar(9L))
                    .thenThrow(new ConflitoException("Só um vídeo com erro pode ser reprocessado."));

            assertThatThrownBy(() -> service.reprocessar(9L)).isInstanceOf(ConflitoException.class);
            verify(storage, never()).info(anyString());
        }
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("vídeo pronto: apaga o HLS inteiro pelo prefixo")
        void videoPronto() {
            when(videoAdmService.excluir(9L)).thenReturn(pronto(3));

            service.excluir(9L);

            verify(storage).excluirPrefixo("videos/9/1/");
            verify(storage, never()).excluir(anyString());
            verify(storage, never()).abortarMultipart(anyString(), anyString());
        }

        @Test
        @DisplayName("envio aberto é abortado; original com erro é apagado")
        void envioEOriginal() {
            when(videoAdmService.excluir(9L)).thenReturn(aguardandoEnvio(3)).thenReturn(comErro(3));

            service.excluir(9L);
            service.excluir(9L);

            verify(storage).abortarMultipart(UPLOAD, "upload-1");
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).excluirPrefixo(anyString());
        }
    }

    @Test
    @DisplayName("thumbnail: URL temporária só com vídeo convertido")
    void thumbnail() throws Exception {
        when(storage.urlDownload("videos/9/1/thumbnail.jpg", Duration.ofHours(1)))
                .thenReturn(url("thumb"));

        assertThat(service.urlThumbnail(pronto(3))).contains(url("thumb"));
        assertThat(service.urlThumbnail(aguardandoEnvio(3))).isEmpty();
    }

    @Test
    @DisplayName("tamanho máximo legível em GB ou MB")
    void legivel() {
        assertThat(EnvioVideoService.legivel(DataSize.ofGigabytes(5))).isEqualTo("5 GB");
        assertThat(EnvioVideoService.legivel(DataSize.ofMegabytes(500))).isEqualTo("500 MB");
        assertThat(EnvioVideoService.prefixoHls("videos/9/1/master.m3u8")).isEqualTo("videos/9/1/");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/application/VideoAdmServiceTest.java`:

`````java
package br.com.celke.cursos.video.application;

import static br.com.celke.cursos.support.fixtures.VideoFixture.PLAYLIST;
import static br.com.celke.cursos.support.fixtures.VideoFixture.UPLOAD;
import static br.com.celke.cursos.support.fixtures.VideoFixture.aguardandoEnvio;
import static br.com.celke.cursos.support.fixtures.VideoFixture.comErro;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pendente;
import static br.com.celke.cursos.support.fixtures.VideoFixture.pronto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.fila.FilaService;
import br.com.celke.cursos.video.domain.ResultadoTranscodificacao;
import br.com.celke.cursos.video.domain.StatusVideo;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VideoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String TIPO = "video.transcodificar";

    @Mock
    private VideoRepository videos;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private FilaService fila;

    private VideoAdmService service;

    @BeforeEach
    void criar() {
        service = new VideoAdmService(videos, conteudoTurmaService, fila, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void aulaExiste() {
        when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true);
    }

    @Nested
    class Buscar {

        @Test
        @DisplayName("aula inexistente responde aula não encontrada")
        void aulaInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.buscarPorAula(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
        }

        @Test
        @DisplayName("aula sem vídeo responde a aula não tem vídeo")
        void semVideo() {
            aulaExiste();
            when(videos.findByAulaId(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarPorAula(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }

        @Test
        @DisplayName("envio é permitido sem vídeo ou com vídeo fora do processamento, recusado durante")
        void envioPermitido() {
            aulaExiste();
            when(videos.findByAulaId(9L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.of(pendente(3)));

            service.exigirEnvioPermitido(9L);
            service.exigirEnvioPermitido(9L);
            assertThatThrownBy(() -> service.exigirEnvioPermitido(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
        }
    }

    @Nested
    class RegistrarEnvio {

        @Test
        @DisplayName("primeiro envio cria o vídeo da aula, sem envio anterior")
        void primeiro() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/x", "u-x", "a.mp4", 500);

            assertThat(registro.anterior()).isEmpty();
            Video video = registro.video();
            assertThat(video.getAulaId()).isEqualTo(9L);
            assertThat(video.getStatus()).isEqualTo(StatusVideo.AGUARDANDO_ENVIO);
            assertThat(video.getUploadChave()).isEqualTo("uploads/videos/9/x");
            assertThat(video.getUploadId()).isEqualTo("u-x");
            assertThat(video.getTamanhoOriginal()).isEqualTo(500);
        }

        @Test
        @DisplayName("envio aberto anterior é devolvido para ser abortado")
        void comEnvioAnterior() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(aguardandoEnvio(3)));
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/y", "u-y", "b.mp4", 10);

            assertThat(registro.anterior()).contains(new VideoAdmService.EnvioAnterior(UPLOAD, "upload-1"));
            assertThat(registro.video().getUploadId()).isEqualTo("u-y");
        }

        @Test
        @DisplayName("vídeo pronto recebe envio novo sem envio anterior a abortar")
        void substituiPronto() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pronto(3)));
            when(videos.saveAndFlush(any(Video.class))).thenAnswer(i -> i.getArgument(0));

            var registro = service.registrarEnvio(9L, "uploads/videos/9/y", "u-y", "b.mp4", 10);

            assertThat(registro.anterior()).isEmpty();
            assertThat(registro.video().getPlaylistChave()).isEqualTo(PLAYLIST);
        }

        @Test
        @DisplayName("vídeo em processamento recusa envio novo")
        void emProcessamento() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
            verify(videos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("dois primeiros envios ao mesmo tempo: a restrição única recusa o segundo com 409")
        void corrida() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());
            when(videos.saveAndFlush(any(Video.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_videos_aula"));

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Outro envio para esta aula começou ao mesmo tempo. Tente de novo.");
        }

        @Test
        @DisplayName("aula inexistente não registra nada")
        void aulaInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.registrarEnvio(9L, "k", "u", "a.mp4", 10))
                    .isInstanceOf(NaoEncontradoException.class);
            verify(videos, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Conclusao {

        @Test
        @DisplayName("envio em andamento é devolvido; sem envio, 409; aula inexistente, 404")
        void envioEmAndamento() {
            Video aberto = aguardandoEnvio(3);
            when(videos.findByAulaId(9L))
                    .thenReturn(Optional.of(aberto))
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.empty());
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true).thenReturn(false);

            assertThat(service.envioEmAndamento(9L)).isSameAs(aberto);
            assertThatThrownBy(() -> service.envioEmAndamento(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Nenhum envio em andamento para esta aula. Comece um novo envio.");
            assertThatThrownBy(() -> service.envioEmAndamento(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
        }

        @Test
        @DisplayName("enfileirar grava o tamanho real e coloca o job com o vídeo e a chave do original")
        void enfileirar() {
            when(videos.travar(3L)).thenReturn(Optional.of(aguardandoEnvio(3)));

            Video video = service.enfileirar(3L, 999);

            assertThat(video.getStatus()).isEqualTo(StatusVideo.PENDENTE);
            assertThat(video.getTamanhoOriginal()).isEqualTo(999);
            verify(fila).enfileirar(TIPO, new PayloadTranscodificacao(3L, UPLOAD), AGORA);
        }

        @Test
        @DisplayName("enfileirar fora de um envio em andamento é recusado sem criar job")
        void enfileirarForaDoEnvio() {
            when(videos.travar(3L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.enfileirar(3L, 999)).isInstanceOf(ConflitoException.class);
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("envio recusado: vídeo com erro e sem original")
        void recusar() {
            Video video = aguardandoEnvio(3);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            service.recusarEnvio(3L, "O arquivo não é um vídeo.");

            assertThat(video.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(video.getErro()).isEqualTo("O arquivo não é um vídeo.");
            assertThat(video.getUploadChave()).isNull();
        }

        @Test
        @DisplayName("vídeo removido no meio responde a aula não tem vídeo")
        void videoSumiu() {
            when(videos.travar(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enfileirar(3L, 1)).isInstanceOf(NaoEncontradoException.class);
        }
    }

    @Nested
    class Reprocessar {

        @Test
        @DisplayName("vídeo com erro e original guardado volta para a fila")
        void reprocessa() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(comErro(3)));

            assertThat(service.reprocessar(9L).getStatus()).isEqualTo(StatusVideo.PENDENTE);
            verify(fila).enfileirar(TIPO, new PayloadTranscodificacao(3L, UPLOAD), AGORA);
        }

        @Test
        @DisplayName("só vídeo com erro")
        void semErro() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pronto(3)));

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Só um vídeo com erro pode ser reprocessado.");
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("sem o original, pede um envio novo")
        void semOriginal() {
            Video video = comErro(3);
            video.descartarOriginal(AGORA);
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(video));

            assertThatThrownBy(() -> service.reprocessar(9L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O arquivo original não está mais disponível. Envie o vídeo de novo.");
            verify(fila, never()).enfileirar(anyString(), any(), any());
        }

        @Test
        @DisplayName("descartar o original esquece a chave")
        void descartar() {
            Video video = comErro(3);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            service.descartarOriginal(3L);

            assertThat(video.getUploadChave()).isNull();
        }
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("exclui o vídeo fora do processamento e devolve o que apagar")
        void exclui() {
            Video video = pronto(3);
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(video));

            assertThat(service.excluir(9L)).isSameAs(video);
            verify(videos).delete(video);
        }

        @Test
        @DisplayName("vídeo em processamento não é excluído")
        void emProcessamento() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.of(pendente(3)));

            assertThatThrownBy(() -> service.excluir(9L)).isInstanceOf(ConflitoException.class);
            verify(videos, never()).delete(any());
        }

        @Test
        @DisplayName("aula sem vídeo responde 404")
        void semVideo() {
            aulaExiste();
            when(videos.travarPorAula(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.excluir(9L))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("A aula não tem vídeo.");
        }
    }

    @Nested
    class Worker {

        private final PayloadTranscodificacao payload = new PayloadTranscodificacao(3L, UPLOAD);

        @Test
        @DisplayName("inicia o processamento do envio do job")
        void inicia() {
            when(videos.travar(3L)).thenReturn(Optional.of(pendente(3)));

            assertThat(service.iniciarProcessamento(payload))
                    .hasValueSatisfying(v -> assertThat(v.getStatus()).isEqualTo(StatusVideo.PROCESSANDO));
        }

        @Test
        @DisplayName("job velho (outro envio, vídeo pronto, com erro ou excluído) não processa")
        void jobVelho() {
            Video outroEnvio = pendente(3);
            outroEnvio.falhar("x", AGORA);
            outroEnvio.iniciarEnvio("uploads/videos/9/outro", "u2", "b.mp4", 5, AGORA);
            outroEnvio.enfileirar(5, AGORA);
            when(videos.travar(3L))
                    .thenReturn(Optional.of(outroEnvio))
                    .thenReturn(Optional.of(pronto(3)))
                    .thenReturn(Optional.of(comErro(3)))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 4; i++) {
                assertThat(service.iniciarProcessamento(payload)).isEmpty();
            }
        }

        @Test
        @DisplayName("concluir publica e devolve a playlist anterior")
        void concluir() {
            Video video = pronto(3);
            video.iniciarEnvio("uploads/videos/9/novo", "u", "n.mp4", 10, AGORA);
            video.enfileirar(10, AGORA);
            video.iniciarProcessamento(AGORA);
            when(videos.travar(3L)).thenReturn(Optional.of(video));

            var anterior = service.concluir(
                    3L, new ResultadoTranscodificacao("videos/9/2/master.m3u8", "t", 1, 1, 1, 1, "720p", 1));

            assertThat(anterior).contains(PLAYLIST);
            assertThat(video.getPlaylistChave()).isEqualTo("videos/9/2/master.m3u8");
        }

        @Test
        @DisplayName("desistência deixa com erro só o vídeo do mesmo envio")
        void falhar() {
            Video doJob = pendente(3);
            Video pronto = pronto(3);
            when(videos.travar(3L)).thenReturn(Optional.of(doJob)).thenReturn(Optional.of(pronto));

            service.falharProcessamento(payload, "ffmpeg falhou");
            service.falharProcessamento(payload, "ffmpeg falhou");

            assertThat(doJob.getStatus()).isEqualTo(StatusVideo.ERRO);
            assertThat(doJob.getErro()).isEqualTo("ffmpeg falhou");
            assertThat(pronto.getStatus()).isEqualTo(StatusVideo.PRONTO);
        }
    }
}
`````

### 10. Testes de integração

`backend/src/test/java/br/com/celke/cursos/shared/fila/FilaServiceIT.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: enfileirar, reservar, concluir e falhar no PostgreSQL real — backoff, tentativas
 * esgotadas, lease vencido e reserva concorrente sem duplicidade.
 * Não cobre: cálculo do backoff (BackoffTest) e despacho para manipuladores (ProcessadorFilaTest).
 * Dado criado: jobs com tipo it-<stamp> (cada teste usa um tipo próprio).
 */
class FilaServiceIT extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-10T12:00:00Z");

    @Autowired
    private FilaService fila;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("reserva o job, entrega o payload e conclui")
    void cicloCompleto() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of("para", "ana@teste.celke.uno"), T0);

        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        assertThat(job.id()).isEqualTo(id);
        assertThat(job.tentativas()).isEqualTo(1);
        assertThat(job.maxTentativas()).isEqualTo(5);
        assertThat(job.payload()).isEqualTo("{\"para\": \"ana@teste.celke.uno\"}");
        assertThat(status(id)).isEqualTo(StatusJob.PROCESSANDO);

        fila.concluir(job, T0);

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("só reserva jobs dos tipos pedidos")
    void filtraPorTipo() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(stamp()), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0)).isPresent();
    }

    @Test
    @DisplayName("não reserva job agendado para o futuro")
    void respeitaHorario() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(tipo), T0.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("falha volta o job para a fila depois do backoff, com o erro registrado")
    void falhaComBackoff() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falhar(job, "SMTP fora do ar", T0);

        assertThat(status(id)).isEqualTo(StatusJob.PENDENTE);
        assertThat(ultimoErro(id)).isEqualTo("SMTP fora do ar");
        // backoff-base de 30 s: um segundo antes não reserva, no instante exato reserva
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(29))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(30)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("na última tentativa a falha leva o job para ERRO")
    void tentativasEsgotadas() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Instant agora = T0;
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            Job job = fila.reservar(Set.of(tipo), agora).orElseThrow();
            assertThat(job.tentativas()).isEqualTo(tentativa);
            fila.falhar(job, "falha " + tentativa, agora);
            agora = agora.plus(Duration.ofDays(1));
        }

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(ultimoErro(id)).isEqualTo("falha 5");
        assertThat(fila.reservar(Set.of(tipo), agora)).isEmpty();
    }

    @Test
    @DisplayName("falha definitiva leva o job direto para ERRO")
    void falhaDefinitiva() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falharDefinitivo(job, "template inexistente", T0);

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("job com lease vencido volta a ser reservado (o worker anterior caiu)")
    void leaseVencido() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);
        fila.reservar(Set.of(tipo), T0).orElseThrow();

        // lease de 10 min: ainda com o worker anterior no instante exato, livre um segundo depois
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("renovar o lease mantém o job com o worker; sem renovação ele volta para a fila")
    void renovacaoDoLease() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        // Renovado aos 9 min: o lease vai até os 19 min
        fila.renovar(job, T0.plus(Duration.ofMinutes(9)));
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(19)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.id()).isEqualTo(id));
    }

    @Test
    @DisplayName("renovar um job já concluído não o devolve para processamento")
    void renovacaoDeJobConcluido() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        fila.concluir(job, T0);

        fila.renovar(job, T0.plusSeconds(30));

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
    }

    @Test
    @DisplayName("consumidores concorrentes nunca reservam o mesmo job")
    void reservaConcorrente() throws Exception {
        String tipo = stamp();
        int total = 40;
        for (int i = 0; i < total; i++) {
            fila.enfileirar(tipo, Map.of("i", i), T0);
        }
        List<Long> reservados = Collections.synchronizedList(new ArrayList<>());
        Callable<Void> consumidor = () -> {
            while (true) {
                var job = fila.reservar(Set.of(tipo), T0);
                if (job.isEmpty()) {
                    return null;
                }
                reservados.add(job.get().id());
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (var futuro : executor.invokeAll(List.of(consumidor, consumidor, consumidor, consumidor))) {
                futuro.get();
            }
        }

        assertThat(reservados).hasSize(total).doesNotHaveDuplicates();
    }

    private StatusJob status(long id) {
        return StatusJob.valueOf(jdbc.sql("SELECT status FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single());
    }

    private String ultimoErro(long id) {
        return jdbc.sql("SELECT ultimo_erro FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/armazenamento/StorageServiceIT.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.armazenamento.StorageService.ParteEnviada;
import br.com.celke.cursos.support.Containers;
import br.com.celke.cursos.support.IntegrationTest;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

/**
 * Cobre: upload e download por URL pré-assinada, upload em partes, consulta e exclusão no Garage
 * real, e a configuração do bucket aplicada pela API ao subir (CORS e expiração de uploads).
 * Não cobre: regras de quem pode enviar ou baixar (módulos de vídeo e arquivos).
 * Dado criado: arquivos em uploads/it-<stamp>/.
 */
class StorageServiceIT extends IntegrationTest {

    private static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final int CINCO_MB = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String pasta = "uploads/" + stamp() + "/";

    @Autowired
    private StorageService storage;

    @Autowired
    private S3Client s3;

    @Autowired
    private ArmazenamentoProperties props;

    @Test
    @DisplayName("envia por PUT pré-assinado, consulta, baixa por GET pré-assinado e exclui")
    void cicloDeUmArquivo() throws Exception {
        String chave = pasta + "texto.txt";
        byte[] conteudo = "conteúdo do arquivo".getBytes(StandardCharsets.UTF_8);

        URL urlUpload = storage.urlUpload(chave, "text/plain", VALIDADE);
        assertThat(urlUpload.toString()).startsWith(props.endpointPublico());
        assertThat(put(urlUpload, conteudo, "text/plain").statusCode()).isEqualTo(200);

        assertThat(storage.info(chave)).contains(new InfoArquivo(conteudo.length, "text/plain"));

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(storage.urlDownload(chave, VALIDADE).toURI())
                        .build(),
                BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(conteudo);

        storage.excluir(chave);
        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("URL de upload não aceita conteúdo com tipo diferente do assinado")
    void uploadComTipoDiferente() throws Exception {
        URL url = storage.urlUpload(pasta + "imagem.png", "image/png", VALIDADE);
        assertThat(put(url, new byte[] {1, 2, 3}, "text/html").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("upload em partes junta as partes na ordem certa")
    void uploadEmPartes() throws Exception {
        String chave = pasta + "video.mp4";
        byte[] parte1 = new byte[CINCO_MB];
        Arrays.fill(parte1, (byte) 1);
        byte[] parte2 = "fim".getBytes(StandardCharsets.UTF_8);

        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        // Envia fora de ordem: a conclusão ordena pelo número da parte
        String etag2 = put(storage.urlParte(chave, uploadId, 2, VALIDADE), parte2, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        String etag1 = put(storage.urlParte(chave, uploadId, 1, VALIDADE), parte1, null)
                .headers()
                .firstValue("ETag")
                .orElseThrow();
        storage.concluirMultipart(chave, uploadId, List.of(new ParteEnviada(2, etag2), new ParteEnviada(1, etag1)));

        assertThat(storage.info(chave)).hasValueSatisfying(info -> {
            assertThat(info.tamanho()).isEqualTo(CINCO_MB + parte2.length);
            assertThat(info.contentType()).isEqualTo("video/mp4");
        });
    }

    @Test
    @DisplayName("upload em partes abortado não deixa arquivo")
    void uploadEmPartesAbortado() throws Exception {
        String chave = pasta + "abortado.mp4";
        String uploadId = storage.iniciarMultipart(chave, "video/mp4");
        put(storage.urlParte(chave, uploadId, 1, VALIDADE), "parte".getBytes(StandardCharsets.UTF_8), null);

        storage.abortarMultipart(chave, uploadId);

        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("grava e baixa arquivos do disco, lê só o início e exclui por prefixo")
    void arquivosDoDiscoEPrefixo(@TempDir Path dir) throws Exception {
        Path origem = Files.writeString(dir.resolve("index.m3u8"), "#EXTM3U\n#EXT-X-ENDLIST\n");
        String prefixo = pasta + "hls/";
        storage.gravarArquivo(prefixo + "720p/index.m3u8", origem, "application/vnd.apple.mpegurl");
        storage.gravarArquivo(prefixo + "720p/seg_0000.ts", origem, "video/mp2t");
        storage.gravarArquivo(pasta + "fora-do-prefixo.txt", origem, "text/plain");

        assertThat(storage.info(prefixo + "720p/index.m3u8"))
                .contains(new InfoArquivo(Files.size(origem), "application/vnd.apple.mpegurl"));
        assertThat(storage.baixarInicio(prefixo + "720p/index.m3u8", 4))
                .isEqualTo("#EXT".getBytes(StandardCharsets.UTF_8));
        Path destino = dir.resolve("baixado.m3u8");
        storage.baixarParaArquivo(prefixo + "720p/index.m3u8", destino);
        assertThat(destino).hasSameBinaryContentAs(origem);

        storage.excluirPrefixo(prefixo);

        assertThat(storage.info(prefixo + "720p/index.m3u8")).isEmpty();
        assertThat(storage.info(prefixo + "720p/seg_0000.ts")).isEmpty();
        assertThat(storage.info(pasta + "fora-do-prefixo.txt"))
                .as("fora do prefixo continua")
                .isPresent();
        assertThatThrownBy(() -> storage.excluirPrefixo("videos")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consulta de arquivo inexistente devolve vazio")
    void arquivoInexistente() {
        assertThat(storage.info(pasta + "nao-existe.txt")).isEmpty();
    }

    @Test
    @DisplayName("bucket fica com CORS para o front, expondo o ETag")
    void corsDoBucket() {
        List<CORSRule> regras =
                s3.getBucketCors(r -> r.bucket(Containers.S3_BUCKET)).corsRules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.allowedOrigins()).containsExactly("http://localhost:5173");
            assertThat(regra.allowedMethods()).containsExactlyInAnyOrder("GET", "PUT", "HEAD");
            assertThat(regra.exposeHeaders()).contains("ETag");
        });
    }

    @Test
    @DisplayName("preflight do navegador é aceito só para a origem configurada")
    void preflightCors() throws Exception {
        URI url = storage.urlUpload(pasta + "cors.txt", "text/plain", VALIDADE).toURI();

        HttpResponse<Void> permitida = preflight(url, "http://localhost:5173");
        assertThat(permitida.statusCode()).isEqualTo(200);
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(permitida.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(metodos -> assertThat(metodos).contains("PUT"));

        // Origem não configurada: o preflight é recusado e o navegador não envia o PUT. O Garage
        // põe "Access-Control-Allow-Origin: *" só para o navegador conseguir ler a resposta de erro.
        HttpResponse<Void> negada = preflight(url, "https://site-estranho.com");
        assertThat(negada.statusCode()).isEqualTo(403);
        assertThat(negada.headers().firstValue("Access-Control-Allow-Methods")).isEmpty();
    }

    @Test
    @DisplayName("bucket expira o que fica em uploads/ e os uploads em partes incompletos")
    void expiracaoDeUploads() {
        List<LifecycleRule> regras = s3.getBucketLifecycleConfiguration(r -> r.bucket(Containers.S3_BUCKET))
                .rules();
        assertThat(regras).singleElement().satisfies(regra -> {
            assertThat(regra.filter().prefix()).isEqualTo("uploads/");
            assertThat(regra.expiration().days()).isEqualTo(2);
            assertThat(regra.abortIncompleteMultipartUpload().daysAfterInitiation())
                    .isEqualTo(1);
        });
    }

    private HttpResponse<Void> put(URL url, byte[] corpo, String contentType) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(url.toURI()).PUT(BodyPublishers.ofByteArray(corpo));
        if (contentType != null) {
            requisicao.header("Content-Type", contentType);
        }
        return http.send(requisicao.build(), BodyHandlers.discarding());
    }

    private HttpResponse<Void> preflight(URI url, String origem) throws Exception {
        return http.send(
                HttpRequest.newBuilder(url)
                        .method("OPTIONS", BodyPublishers.noBody())
                        .header("Origin", origem)
                        .header("Access-Control-Request-Method", "PUT")
                        .build(),
                BodyHandlers.discarding());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/VideoAcessoIT.java`:

`````java
package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de vídeo do ADM ({@code /api/adm/aulas/{id}/video}: 401 anônimo,
 * 403 ALUNO, ADMIN passa pela segurança).
 * Não cobre: o comportamento de cada rota (VideoEnvioIT).
 * Dado criado: a cada caso, um curso it-<stamp> com turma, módulo e aula sem vídeo (id real nas rotas).
 */
class VideoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long aula;

    @BeforeEach
    void criarAula() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
    }

    /**
     * Rotas com corpo válido e o status esperado para o ADMIN numa aula sem vídeo: o envio abre um
     * upload de verdade; as demais respondem que não há vídeo ou envio em andamento.
     */
    static Stream<Arguments> rotas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/aulas/{a}/video", null, HttpStatus.NOT_FOUND),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/aulas/{a}/video/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":10}",
                        HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/aulas/{a}/video/envio/conclusao",
                        "{\"partes\":[{\"numero\":1,\"etag\":\"x\"}]}",
                        HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/video/reprocessamento", null, HttpStatus.NOT_FOUND),
                Arguments.of(HttpMethod.DELETE, "/api/adm/aulas/{a}/video", null, HttpStatus.NOT_FOUND));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        var requisicao = mvc.method(metodo).uri(rota.replace("{a}", String.valueOf(aula)));
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("anônimo recebe 401")
    void anonimo(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotas")
    @DisplayName("ALUNO recebe 403")
    void aluno(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotas")
    @DisplayName("ADMIN passa pela segurança")
    void admin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }

    @Test
    @DisplayName("ALUNO recebe 403 mesmo com corpo inválido: o papel é conferido antes do corpo")
    void papelAntesDoCorpo() {
        assertThat(chamar(HttpMethod.POST, "/api/adm/aulas/{a}/video/envio", "{}", tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/video/VideoEnvioIT.java`:

`````java
package br.com.celke.cursos.video;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.video.domain.Video;
import br.com.celke.cursos.video.infra.VideoRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: envio do vídeo em partes direto ao Garage real, conferência do arquivo na conclusão (tamanho e
 * assinatura), conversão para HLS com o ffmpeg de verdade (fila processada no próprio teste), troca do
 * vídeo (o HLS anterior é apagado), erro e reprocessamento, exclusão e os bloqueios durante o
 * processamento.
 * Não cobre: argumentos do ffmpeg e regras caso a caso (ComandosFfmpegTest, VarianteTest,
 * EnvioVideoServiceTest, VideoAdmServiceTest).
 * Dado criado: cursos it-<stamp>, arquivos em uploads/videos/ e videos/. Exige ffmpeg no PATH.
 */
class VideoEnvioIT extends IntegrationTest {

    /** Início de um MP4 válido seguido de lixo: passa na assinatura, mas o ffprobe recusa. */
    private static final byte[] MP4_CORROMPIDO =
            HexFormat.of().parseHex("000000206674797069736f6d" + "00".repeat(100) + "deadbeef".repeat(50));

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private VideoRepository videos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private CursoITHelper adm;
    private long modulo;
    private long aula;
    /** Chave do original do último envio feito por {@link #enviar}. */
    private String ultimoUpload;

    @BeforeEach
    void criarAula() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula com vídeo", false);
    }

    private static byte[] amostra() throws Exception {
        try (InputStream entrada = VideoEnvioIT.class.getResourceAsStream("/amostras/video-3s.mp4")) {
            return entrada.readAllBytes();
        }
    }

    private String rota(String sufixo) {
        return "/api/adm/aulas/" + aula + "/video" + sufixo;
    }

    private MvcTestResult iniciar(String contentType, long tamanho) {
        return adm.enviar(
                "POST",
                rota("/envio"),
                "{\"nomeArquivo\":\"aula.mp4\",\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, tamanho));
    }

    /** Inicia o envio, faz o PUT de cada parte nas URLs pré-assinadas e pede a conclusão. */
    private MvcTestResult enviar(byte[] conteudo, String contentType) throws Exception {
        MvcTestResult inicio = iniciar(contentType, conteudo.length);
        assertThat(inicio).hasStatusOk();
        ultimoUpload = video().getUploadChave();
        String corpo = CursoITHelper.corpo(inicio);
        int tamanhoParte = JsonPath.<Number>read(corpo, "$.tamanhoParte").intValue();
        List<String> urls = JsonPath.read(corpo, "$.partes[*].url");
        StringBuilder partes = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            assertThat(urls.get(i)).startsWith(armazenamento.endpointPublico());
            int de = i * tamanhoParte;
            int ate = Math.min(conteudo.length, de + tamanhoParte);
            var put = http.send(
                    HttpRequest.newBuilder(URI.create(urls.get(i)))
                            .PUT(BodyPublishers.ofByteArray(conteudo, de, ate - de))
                            .build(),
                    BodyHandlers.discarding());
            assertThat(put.statusCode()).isEqualTo(200);
            partes.append(i == 0 ? "" : ",").append("{\"numero\":%d,\"etag\":\"%s\"}".formatted(i + 1, etag(put)));
        }
        return adm.enviar("POST", rota("/envio/conclusao"), "{\"partes\":[" + partes + "]}");
    }

    /** ETag da resposta do PUT, escapado para ir dentro de uma string JSON (ele vem entre aspas). */
    private static String etag(HttpResponse<?> put) {
        return put.headers().firstValue("ETag").orElseThrow().replace("\"", "\\\"");
    }

    private Video video() {
        return videos.findByAulaId(aula).orElseThrow();
    }

    private String texto(String chave) {
        return new String(storage.baixar(chave), StandardCharsets.UTF_8);
    }

    private static String prefixo(String playlist) {
        return playlist.substring(0, playlist.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("envia em partes, converte para HLS 720p sem ampliar, troca o vídeo e remove")
    void cicloDoVideo() throws Exception {
        byte[] original = amostra();

        assertThat(enviar(original, "video/mp4"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDENTE");
        String upload = ultimoUpload;
        assertThat(video().getUploadChave()).isEqualTo(upload);
        assertThat(upload).startsWith("uploads/videos/" + aula + "/");
        assertThat(storage.info(upload))
                .get()
                .extracting(StorageService.InfoArquivo::tamanho)
                .isEqualTo((long) original.length);

        processarFila();

        var resposta = adm.enviar("GET", rota(""), null);
        assertThat(resposta).hasStatusOk();
        var json = assertThat(resposta).bodyJson();
        json.extractingPath("$.status").isEqualTo("PRONTO");
        json.extractingPath("$.publicado").isEqualTo(true);
        json.extractingPath("$.nomeOriginal").isEqualTo("aula.mp4");
        json.extractingPath("$.tamanhoOriginal").isEqualTo(original.length);
        json.extractingPath("$.duracaoSegundos").isEqualTo(3);
        json.extractingPath("$.largura").isEqualTo(1280);
        json.extractingPath("$.altura").isEqualTo(720);
        json.extractingPath("$.variantes").isEqualTo(List.of("720p"));
        json.extractingPath("$.thumbnailUrl").asString().startsWith(armazenamento.endpointPublico());
        json.extractingPath("$.segundosProcessamentoPorMinuto").isNotNull();
        json.extractingPath("$.erro").isNull();

        Video pronto = video();
        String playlist = pronto.getPlaylistChave();
        assertThat(playlist).matches("videos/" + aula + "/\\d+/master\\.m3u8");
        assertThat(texto(playlist)).contains("720p/index.m3u8").doesNotContain("1080p");
        assertThat(texto(prefixo(playlist) + "720p/index.m3u8"))
                .contains("#EXT-X-ENDLIST")
                .contains("seg_0000.ts");
        assertThat(storage.info(prefixo(playlist) + "720p/seg_0000.ts"))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("video/mp2t");
        assertThat(storage.info(pronto.getThumbnailChave()))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("image/jpeg");
        assertThat(storage.info(prefixo(playlist) + "ffmpeg.log"))
                .as("log do ffmpeg não é publicado")
                .isEmpty();
        assertThat(pronto.getTamanhoFinal()).isPositive();
        assertThat(storage.info(upload))
                .as("original apagado depois da conversão")
                .isEmpty();

        // A aula com vídeo não pode ser excluída
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula tem vídeo. Remova o vídeo ou inative a aula.");

        // Troca: o HLS anterior continua até o novo ficar pronto e então é apagado
        assertThat(enviar(original, "video/mp4")).hasStatus(HttpStatus.ACCEPTED);
        assertThat(video().getPlaylistChave()).isEqualTo(playlist);
        processarFila();
        String nova = video().getPlaylistChave();
        assertThat(nova).isNotEqualTo(playlist);
        assertThat(storage.info(nova)).isPresent();
        assertThat(storage.info(playlist)).as("HLS anterior apagado").isEmpty();
        assertThat(storage.info(prefixo(playlist) + "720p/seg_0000.ts")).isEmpty();

        // Remoção: vídeo e arquivos somem; a aula pode ser excluída
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(nova)).isEmpty();
        assertThat(adm.enviar("GET", rota(""), null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula não tem vídeo.");
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("arquivo com extensão falsa é recusado na conclusão, apagado, e não pode ser reprocessado")
    void extensaoFalsa() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThat(enviar(html, "video/mp4"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");

        assertThat(ultimoUpload).startsWith("uploads/videos/" + aula + "/");
        assertThat(storage.info(ultimoUpload)).as("original recusado é apagado").isEmpty();
        assertThat(video().getUploadChave()).isNull();
        var json = assertThat(adm.enviar("GET", rota(""), null)).bodyJson();
        json.extractingPath("$.status").isEqualTo("ERRO");
        json.extractingPath("$.erro").isEqualTo("O arquivo não é um vídeo MP4, MOV, MKV ou WebM.");
        json.extractingPath("$.publicado").isEqualTo(false);
        assertThat(adm.enviar("POST", rota("/reprocessamento"), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo original não está mais disponível. Envie o vídeo de novo.");
    }

    @Test
    @DisplayName("vídeo corrompido falha no worker sem nova tentativa; reprocessar tenta de novo a partir do original")
    void corrompidoEReprocessamento() throws Exception {
        assertThat(enviar(MP4_CORROMPIDO, "video/mp4")).hasStatus(HttpStatus.ACCEPTED);

        // Na fila: não aceita envio novo, reprocessamento nem exclusão
        assertThat(iniciar("video/mp4", 10))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O vídeo da aula está em processamento. Aguarde o fim para alterá-lo.");
        assertThat(adm.enviar("POST", rota("/reprocessamento"), null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.CONFLICT);

        processarFila();

        Video comErro = video();
        assertThat(comErro.getStatus()).hasToString("ERRO");
        assertThat(comErro.getErro()).startsWith("O arquivo não é um vídeo válido");
        assertThat(storage.info(comErro.getUploadChave()))
                .as("original guardado")
                .isPresent();

        assertThat(adm.enviar("POST", rota("/reprocessamento"), null))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDENTE");
        processarFila();
        assertThat(video().getStatus()).hasToString("ERRO");

        // Com erro, o vídeo pode ser removido (e o original vai junto)
        assertThat(adm.enviar("DELETE", rota(""), null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(comErro.getUploadChave())).isEmpty();
    }

    @Test
    @DisplayName("tamanho real diferente do informado é recusado e apagado")
    void tamanhoDiferente() throws Exception {
        MvcTestResult inicio = iniciar("video/mp4", 5000);
        String corpo = CursoITHelper.corpo(inicio);
        String url = JsonPath.read(corpo, "$.partes[0].url");
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(BodyPublishers.ofByteArray(amostra(), 0, 100))
                        .build(),
                BodyHandlers.discarding());
        assertThat(adm.enviar(
                        "POST",
                        rota("/envio/conclusao"),
                        "{\"partes\":[{\"numero\":1,\"etag\":\"%s\"}]}".formatted(etag(put))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O tamanho do arquivo enviado não confere com o informado. Comece um novo envio.");
        assertThat(video().getUploadChave()).isNull();
    }

    @Test
    @DisplayName("recusas antes de assinar: tipo, tamanho, validação, aula inexistente e conclusão sem envio")
    void recusas() {
        assertThat(iniciar("video/x-msvideo", 10))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie um vídeo MP4, MOV, MKV ou WebM.");
        assertThat(iniciar("video/mp4", 5L * 1024 * 1024 * 1024 + 1))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O vídeo pode ter no máximo 5 GB.");
        assertThat(adm.enviar("POST", rota("/envio"), "{\"contentType\":\"video/mp4\",\"tamanho\":0}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nomeArquivo", "tamanho"));
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/aulas/999999999/video/envio",
                        "{\"nomeArquivo\":\"a.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(adm.enviar("POST", rota("/envio/conclusao"), "{\"partes\":[{\"numero\":1,\"etag\":\"x\"}]}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Nenhum envio em andamento para esta aula. Comece um novo envio.");
        assertThat(videos.findByAulaId(aula)).isEmpty();
    }
}
`````

### 11. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Midia.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.RequestSpecification;
import java.net.URI;

/** Acesso ao Garage pelo Caddy ({@code media.localhost}), com as URLs pré-assinadas devolvidas pela API. */
public final class Midia {

    public static final String HOST = "media.localhost";

    private Midia() {}

    /**
     * Requisição com a URL pré-assinada como veio: sem recodificar a query e sem acrescentar charset ao
     * Content-Type, que fazem parte da assinatura.
     */
    public static RequestSpecification requisicao() {
        return given().config(RestAssuredConfig.config()
                        .encoderConfig(EncoderConfig.encoderConfig()
                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false)
                .baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", HOST + ":" + E2eConfig.PORTA);
    }

    /** Caminho e query da URL pré-assinada, para usar com {@link #requisicao()}. */
    public static String caminho(URI url) {
        return url.getRawPath() + "?" + url.getRawQuery();
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Arquivos.java`:

`````java
package br.com.celke.cursos.e2e.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Amostras usadas nas jornadas (em {@code src/test/resources/amostras}, as mesmas dos testes de integração). */
public final class Arquivos {

    private Arquivos() {}

    public static byte[] amostra(String nome) {
        try (InputStream entrada = Arquivos.class.getResourceAsStream("/amostras/" + nome)) {
            if (entrada == null) {
                throw new IllegalStateException("Amostra não encontrada: " + nome);
            }
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/curso/CatalogoE2E.java`:

`````java
package br.com.celke.cursos.e2e.curso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.E2eConfig;
import br.com.celke.cursos.e2e.support.Midia;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import io.restassured.response.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM monta um curso (turma, módulo, aulas e capa enviada direto ao Garage pelo Caddy),
 * publica, e o visitante sem login encontra o curso no catálogo, com o conteúdo e a capa.
 */
class CatalogoE2E {

    @Test
    @DisplayName("ADM cria e publica um curso com capa; o catálogo público mostra o curso")
    void admPublicaCatalogoMostra() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso, turma ativa, módulo e duas aulas (uma gratuita)
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Primeiros passos\"}");
        adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Boas-vindas\",\"gratuita\":true}");
        adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Instalação\"}");

        // 2. Capa: URL pré-assinada → PUT no Garage pelo Caddy (media) → confirmação na API
        byte[] png = ImagemFixture.png(1600, 900);
        Response envio = adm.requisicao()
                .body("{\"contentType\":\"image/png\",\"tamanho\":%d}".formatted(png.length))
                .post("/api/adm/cursos/" + curso + "/capa/envio")
                .then()
                .statusCode(200)
                .extract()
                .response();
        URI url = URI.create(envio.jsonPath().getString("url"));
        assertThat(url.getHost()).isEqualTo(Midia.HOST);
        Midia.requisicao()
                .contentType("image/png")
                .body(png)
                .put(Midia.caminho(url))
                .then()
                .statusCode(200);
        adm.requisicao()
                .body("{\"chave\":\"%s\"}".formatted(envio.jsonPath().getString("chave")))
                .put("/api/adm/cursos/" + curso + "/capa")
                .then()
                .statusCode(200)
                .body("capaUrl", startsWith(E2eConfig.origem(Midia.HOST)));

        // 3. Antes de publicar, o catálogo não conhece o curso
        Api.aluno().get("/api/cursos/" + slug).then().statusCode(404);
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 4. Visitante sem login: listagem, detalhe e capa
        assertThat(slugsNoCatalogo()).contains(slug);
        Api.aluno()
                .get("/api/cursos/" + slug)
                .then()
                .statusCode(200)
                .body("titulo", equalTo("Curso " + slug))
                .body("capaUrl", equalTo("/api/cursos/" + slug + "/capa"))
                .body("modulos[0].titulo", equalTo("Primeiros passos"))
                .body("modulos[0].aulas.titulo", equalTo(List.of("Boas-vindas", "Instalação")))
                .body("modulos[0].aulas.gratuita", equalTo(List.of(true, false)));

        Response redirecionamento = Api.aluno()
                .redirects()
                .follow(false)
                .get("/api/cursos/" + slug + "/capa")
                .then()
                .statusCode(302)
                .extract()
                .response();
        URI capa = URI.create(redirecionamento.getHeader("Location"));
        assertThat(capa.getHost()).isEqualTo(Midia.HOST);
        Response imagem = Midia.requisicao()
                .get(Midia.caminho(capa))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(imagem.getContentType()).isEqualTo("image/jpeg");
        assertThat(imagem.asByteArray()).startsWith((byte) 0xFF, (byte) 0xD8);
    }

    @Test
    @DisplayName("aluno não acessa a gestão de cursos pelo Caddy")
    void alunoNaoGerencia() {
        String token =
                Api.login(E2eConfig.ALUNO_SEED, E2eConfig.SENHA_SEED).jsonPath().getString("accessToken");

        Api.adm()
                .header("Authorization", Api.bearer(token))
                .get("/api/adm/cursos")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Acesso negado."));
    }

    private static List<String> slugsNoCatalogo() {
        List<String> slugs = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var corpo = Api.aluno()
                    .get("/api/cursos?size=100&page=" + pagina)
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath();
            slugs.addAll(corpo.getList("itens.slug", String.class));
            if (pagina + 1 >= corpo.getInt("totalPaginas")) {
                return slugs;
            }
        }
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/video/VideoE2E.java`:

`````java
package br.com.celke.cursos.e2e.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Arquivos;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM envia o vídeo de uma aula em partes, direto ao Garage pelo Caddy; o worker converte
 * com o ffmpeg e o vídeo fica pronto, com o tamanho antes e depois e a thumbnail.
 */
class VideoE2E {

    @Test
    @DisplayName("upload em partes de um vídeo curto até o status pronto")
    void uploadAtePronto() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        byte[] video = Arquivos.amostra("video-3s.mp4");

        // 1. Curso, turma, módulo e aula
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula em vídeo\"}");
        String rota = "/api/adm/aulas/" + aula + "/video";

        // 2. URLs pré-assinadas de cada parte, no host de mídia
        JsonPath envio = adm.requisicao()
                .body("{\"nomeArquivo\":\"aula.mp4\",\"contentType\":\"video/mp4\",\"tamanho\":%d}"
                        .formatted(video.length))
                .post(rota + "/envio")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        int tamanhoParte = envio.getInt("tamanhoParte");
        List<String> urls = envio.getList("partes.url", String.class);

        // 3. PUT de cada parte direto no Garage pelo Caddy, guardando o ETag
        List<Map<String, Object>> partes = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            URI url = URI.create(urls.get(i));
            assertThat(url.getHost()).isEqualTo(Midia.HOST);
            int de = i * tamanhoParte;
            int ate = Math.min(video.length, de + tamanhoParte);
            byte[] parte = Arrays.copyOfRange(video, de, ate);
            Response put = Midia.requisicao()
                    .body(parte)
                    .put(Midia.caminho(url))
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
            partes.add(Map.of("numero", i + 1, "etag", put.getHeader("ETag")));
        }

        // 4. Conclusão: a API confere o arquivo e coloca na fila
        adm.requisicao()
                .body(Map.of("partes", partes))
                .post(rota + "/envio/conclusao")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDENTE"));

        // 5. O worker converte: status pronto, com o tamanho antes e depois
        JsonPath pronto = await("vídeo processado pelo worker")
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(2))
                .until(
                        () -> adm.requisicao()
                                .get(rota)
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath(),
                        json -> !List.of("PENDENTE", "PROCESSANDO").contains(json.getString("status")));
        assertThat(pronto.getString("status"))
                .as("erro: %s", pronto.getString("erro"))
                .isEqualTo("PRONTO");
        assertThat(pronto.getLong("tamanhoOriginal")).isEqualTo(video.length);
        assertThat(pronto.getLong("tamanhoFinal")).isPositive();
        assertThat(pronto.getInt("duracaoSegundos")).isEqualTo(3);
        assertThat(pronto.getList("variantes", String.class)).containsExactly("720p");

        // 6. A thumbnail sai pelo host de mídia
        URI thumbnail = URI.create(pronto.getString("thumbnailUrl"));
        assertThat(thumbnail.getHost()).isEqualTo(Midia.HOST);
        Response imagem = Midia.requisicao()
                .get(Midia.caminho(thumbnail))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(imagem.getContentType()).isEqualTo("image/jpeg");
        assertThat(imagem.asByteArray()).startsWith((byte) 0xFF, (byte) 0xD8);
    }
}
`````

## Antes de executar

Fase 3 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/upload-video
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 512 testes unitários e 268 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 14 jornadas passam, entre elas a `VideoE2E`.

No ambiente local, pelo Swagger, com uma aula criada:

- `POST /api/adm/aulas/{id}/video/envio` com o nome, o tipo e o tamanho de um MP4; envie cada parte
  com `curl -X PUT --data-binary @parte "<url>"` e guarde o cabeçalho `ETag`.
- Conclua com os ETags: a resposta é 202 com `PENDENTE`; em poucos segundos `GET .../video` mostra
  `PRONTO`, o tamanho antes e depois e as variantes. O original some de `uploads/`.
- Envie um arquivo de texto como `video/mp4`: a conclusão responde 400 e o vídeo fica com erro.

## Depois de executar

```bash
git add backend/ infra/ .github/ docs/
git commit -m "Upload de vídeo em partes e conversão para HLS"
git push -u origin feature/upload-video
```

Abra o Pull Request de `feature/upload-video` para `homolog`.

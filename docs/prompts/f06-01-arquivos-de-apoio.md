# Arquivos de apoio

**Fase:** 6
**Branch:** `feature/arquivos-apoio`

## Prompt

Vamos criar o módulo `arquivo` do backend: materiais (código-fonte, apostilas) por aula e por módulo,
enviados pelo ADM direto ao Garage e baixados pelo aluno sempre pela política de acesso. Siga o
`backend/CLAUDE.md` e o padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Arquivo de apoio** (migration `V11`): de uma aula **ou** de um módulo (`CHECK` com
  `num_nonnulls`), título, nome de download, chave, tipo e tamanho. Aula ou módulo com arquivos não pode
  ser excluído: a chave estrangeira gera o 409, com mensagem própria.
- **Envio pelo ADM** em duas etapas, como a capa do curso: `POST /api/adm/{aulas|modulos}/{id}/arquivos/envio`
  (ZIP, PDF ou TXT até 200 MB) devolve a URL de PUT em `uploads/arquivos/<dono>/<uuid>`; a confirmação
  `POST /api/adm/{aulas|modulos}/{id}/arquivos` aceita só a chave gerada para o mesmo dono, confere o
  tamanho real e o conteúdo (assinatura do ZIP e do PDF; texto sem byte nulo nos primeiros 512 bytes) e
  copia para `arquivos/<dono>/<uuid>` (`uploads/` expira); o envio é sempre apagado. O nome de download
  sai limpo (sem caminho, acento ou aspas) e com a extensão do formato real. `GET` lista e
  `DELETE /api/adm/arquivos/{id}` remove do bucket.
- **Aluno** (`/api/aluno`): `GET /aulas/{id}/arquivos` (mesmo acesso da aula, gratuita inclusive),
  `GET /modulos/{id}/arquivos` (matrícula vigente na turma; módulo inativo responde 404) e
  `GET /arquivos/{id}/download`, que confere o acesso e devolve uma URL pré-assinada de 5 minutos que
  baixa como anexo (`Content-Disposition` com o nome do arquivo). A chave do bucket nunca sai na resposta.
- O módulo `curso` exporta `moduloExiste` e `moduloAtivo` no `ConteudoTurmaService`; o `StorageService`
  ganha `copiar` e o download com nome de arquivo.
- O `CursoCrudIT` passa a percorrer todas as páginas da listagem filtrada (outras classes também criam
  cursos em rascunho).

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

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

    /**
     * URL para baixar um arquivo como anexo, com o nome informado (cabeçalho {@code Content-Disposition}
     * da resposta, definido na assinatura).
     */
    public URL urlDownload(String chave, Duration validade, String nomeArquivo) {
        String disposicao = "attachment; filename=\"%s\"".formatted(nomeArquivo.replace("\"", ""));
        return presigner
                .presignGetObject(p -> p.signatureDuration(validade)
                        .getObjectRequest(o -> o.bucket(bucket).key(chave).responseContentDisposition(disposicao)))
                .url();
    }

    /** Copia um arquivo dentro do bucket (ex.: de {@code uploads/}, que expira, para o lugar definitivo). */
    public void copiar(String origem, String destino) {
        s3.copyObject(r -> r.sourceBucket(bucket)
                .sourceKey(origem)
                .destinationBucket(bucket)
                .destinationKey(destino));
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

    /** O módulo existe, em qualquer status (gestão dos arquivos pelo ADM). */
    @Transactional(readOnly = true)
    public boolean moduloExiste(Long moduloId) {
        return modulos.existsById(moduloId);
    }

    /** Módulo ativo com a turma dele (arquivos do módulo); vazio se não existe ou está inativo. */
    @Transactional(readOnly = true)
    public Optional<ModuloContexto> moduloAtivo(Long moduloId) {
        return modulos.findById(moduloId)
                .filter(modulo -> modulo.getStatus() == StatusConteudo.ATIVO)
                .map(modulo -> new ModuloContexto(
                        modulo.getId(), modulo.getTitulo(), modulo.getTurma().getId()));
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

    public record ModuloContexto(Long id, String titulo, Long turmaId) {}

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

    /** Exclui a aula (só sem vídeo e sem arquivos) e refaz a sequência das que ficam. */
    @Transactional
    public void excluir(Long id) {
        Aula aula = buscar(id);
        Long moduloId = aula.getModulo().getId();
        modulos.travar(moduloId);
        try {
            aulas.delete(aula);
            aulas.flush();
        } catch (DataIntegrityViolationException e) {
            // As chaves estrangeiras do vídeo e dos arquivos de apoio impedem a exclusão
            throw new ConflitoException("A aula tem vídeo ou arquivos de apoio. Remova-os ou inative a aula.");
        }
        Ordenacao.compactar(aulas.findByModuloIdOrderByOrdem(moduloId));
    }

    private Aula buscar(Long id) {
        return aulas.findById(id).orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/ModuloAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.Ordenacao;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão dos módulos de uma turma pelo ADM. Toda mudança de ordem trava a turma, para duas
 * operações simultâneas não gerarem posições repetidas.
 */
@Service
public class ModuloAdmService {

    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final AulaRepository aulas;
    private final Clock clock;

    public ModuloAdmService(TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas, Clock clock) {
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Modulo> listar(Long turmaId) {
        if (!turmas.existsById(turmaId)) {
            throw TurmaAdmService.naoEncontrada();
        }
        return modulos.findByTurmaIdOrderByOrdem(turmaId);
    }

    /** O módulo novo entra no fim da turma. */
    @Transactional
    public Modulo criar(Long turmaId, String titulo) {
        Turma turma = turmas.travar(turmaId).orElseThrow(TurmaAdmService::naoEncontrada);
        int ordem = Ordenacao.proxima(modulos.findByTurmaIdOrderByOrdem(turmaId));
        return modulos.save(new Modulo(turma, titulo, ordem, clock.instant()));
    }

    @Transactional
    public Modulo renomear(Long id, String titulo) {
        Modulo modulo = buscar(id);
        modulo.renomear(titulo, clock.instant());
        return modulo;
    }

    @Transactional
    public Modulo alterarStatus(Long id, StatusConteudo status) {
        Modulo modulo = buscar(id);
        modulo.alterarStatus(status, clock.instant());
        return modulo;
    }

    @Transactional
    public List<Modulo> reordenar(Long turmaId, List<Long> ids) {
        turmas.travar(turmaId).orElseThrow(TurmaAdmService::naoEncontrada);
        List<Modulo> atuais = modulos.findByTurmaIdOrderByOrdem(turmaId);
        Ordenacao.reordenar(atuais, ids);
        return atuais.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
    }

    /** Exclui um módulo sem aulas e refaz a sequência dos que ficam. */
    @Transactional
    public void excluir(Long id) {
        Modulo modulo = buscar(id);
        Long turmaId = modulo.getTurma().getId();
        turmas.travar(turmaId);
        if (aulas.existsByModuloId(id)) {
            throw new ConflitoException("O módulo tem aulas. Exclua as aulas ou inative o módulo.");
        }
        try {
            modulos.delete(modulo);
            modulos.flush();
        } catch (DataIntegrityViolationException e) {
            // A chave estrangeira dos arquivos de apoio impede a exclusão
            throw new ConflitoException("O módulo tem arquivos de apoio. Remova os arquivos ou inative o módulo.");
        }
        Ordenacao.compactar(modulos.findByTurmaIdOrderByOrdem(turmaId));
    }

    private Modulo buscar(Long id) {
        return modulos.findById(id).orElseThrow(ModuloAdmService::naoEncontrado);
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Módulo não encontrado.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CursoCrudIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: criar, buscar, atualizar, publicar, listar com filtro e excluir curso; slug gerado e
 * normalizado; slug duplicado; validação; exclusão só de item vazio (curso, turma, módulo).
 * Não cobre: ordem de módulos e aulas (ConteudoOrdemIT), capa (CapaCursoIT), catálogo (CatalogoIT).
 * Dado criado: cursos com título e slug it-<stamp>.
 */
class CursoCrudIT extends IntegrationTest {

    private CursoITHelper adm;

    @BeforeEach
    void entrarComoAdmin() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
    }

    @Test
    @DisplayName("cria (slug gerado do título), busca, atualiza, publica, lista e exclui")
    void cicloDoCurso() {
        String stamp = stamp();
        var criacao = adm.enviar(
                "POST",
                "/api/adm/cursos",
                "{\"titulo\":\"  Programação Ágil %s \",\"resumo\":\" Resumo \",\"descricao\":\"   \"}"
                        .formatted(stamp));
        assertThat(criacao).hasStatus(HttpStatus.CREATED);
        long id = CursoITHelper.idCriado(criacao);
        assertThat(criacao.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/adm/cursos/" + id);
        assertThat(criacao).bodyJson().isLenientlyEqualTo("""
                        {"titulo":"Programação Ágil %s","slug":"programacao-agil-%s","resumo":"Resumo",
                         "descricao":null,"status":"RASCUNHO","capaUrl":null}
                        """.formatted(stamp, stamp));

        assertThat(adm.enviar("GET", "/api/adm/cursos/" + id, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo("programacao-agil-" + stamp);

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + id,
                        "{\"titulo\":\"Ágil\",\"slug\":\"%s\",\"resumo\":\"Novo\",\"descricao\":\"Texto\"}"
                                .formatted(stamp)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"titulo\":\"Ágil\",\"slug\":\"%s\",\"resumo\":\"Novo\",\"descricao\":\"Texto\"}"
                        .formatted(stamp));

        adm.publicar(id);
        // Outras classes também criam cursos: a busca percorre todas as páginas do filtro
        assertThat(campo("PUBLICADO", "slug")).contains(stamp);
        String rascunho = stamp();
        adm.criarCurso("IT Rascunho " + rascunho, rascunho);
        assertThat(campo("RASCUNHO", "slug")).contains(rascunho).doesNotContain(stamp);
        assertThat(campo("RASCUNHO", "status")).containsOnly("RASCUNHO");

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + id, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", "/api/adm/cursos/" + id, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"status\":404,\"detail\":\"Curso não encontrado.\",\"instance\":\"/api/adm/cursos/%d\"}"
                                .formatted(id));
    }

    /** Valor do campo em todos os cursos do status, percorrendo as páginas da listagem. */
    private List<String> campo(String status, String campo) {
        List<String> valores = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var resposta =
                    adm.enviar("GET", "/api/adm/cursos?status=%s&size=100&page=%d".formatted(status, pagina), null);
            assertThat(resposta).hasStatusOk();
            String corpo = CursoITHelper.corpo(resposta);
            valores.addAll(JsonPath.read(corpo, "$.itens[*]." + campo));
            if (pagina + 1 >= JsonPath.<Integer>read(corpo, "$.totalPaginas")) {
                return valores;
            }
        }
    }

    @Test
    @DisplayName("slug já usado por outro curso responde 409, na criação e na edição")
    void slugDuplicado() {
        String slug = stamp();
        adm.criarCurso("IT Curso " + slug, slug);
        long outro = adm.criarCurso("IT Outro " + slug, slug + "-outro");
        String esperado = "Já existe um curso com o slug \"%s\".".formatted(slug);

        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/cursos",
                        "{\"titulo\":\"X\",\"slug\":\"%s\",\"resumo\":\"R\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(esperado);
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/cursos/" + outro,
                        "{\"titulo\":\"X\",\"slug\":\"%s\",\"resumo\":\"R\"}".formatted(slug)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("dados inválidos respondem 400 com a lista de campos")
    void validacao() {
        assertThat(adm.enviar("POST", "/api/adm/cursos", "{\"titulo\":\" \",\"slug\":\"Com Espaço\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("resumo", "slug", "titulo");
        assertThat(adm.enviar("PATCH", "/api/adm/cursos/1/status", "{\"status\":\"PUBLICADA\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(adm.enviar("GET", "/api/adm/cursos?status=QUALQUER", null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("só exclui item vazio: aula → módulo → turma → curso")
    void exclusaoSoDeItemVazio() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma");
        long modulo = adm.criarModulo(turma, "Módulo");
        long aula = adm.criarAula(modulo, "Aula", false);

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + turma, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + modulo, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O módulo tem aulas. Exclua as aulas ou inative o módulo.");

        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + aula, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + modulo, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + turma, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("turma: cria em preparação, renomeia, ativa e lista no curso")
    void turma() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma 1");

        assertThat(adm.enviar("PUT", "/api/adm/turmas/" + turma, "{\"nome\":\" Turma 2026 \"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"cursoId\":%d,\"nome\":\"Turma 2026\",\"status\":\"PREPARACAO\"}".formatted(curso));
        adm.ativarTurma(turma);
        assertThat(adm.enviar("GET", "/api/adm/cursos/" + curso + "/turmas", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[{\"id\":%d,\"status\":\"ATIVA\"}]".formatted(turma));
        assertThat(adm.enviar("POST", "/api/adm/cursos/999999999/turmas", "{\"nome\":\"T\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
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
                .isEqualTo("A aula tem vídeo ou arquivos de apoio. Remova-os ou inative a aula.");

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

### 2. Migration

`backend/src/main/resources/db/migration/V11__criar_arquivos_apoio.sql`:

`````sql
-- Arquivos de apoio (código-fonte, materiais) de uma aula ou de um módulo: exatamente um dos dois.
-- Aula ou módulo com arquivos não pode ser excluído (409), para não deixar arquivos sem dono no bucket.
CREATE TABLE arquivos_apoio (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aula_id       BIGINT        REFERENCES aulas (id) ON DELETE RESTRICT,
    modulo_id     BIGINT        REFERENCES modulos (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150)  NOT NULL,
    -- Nome sugerido no download
    nome_arquivo  VARCHAR(255)  NOT NULL,
    chave         VARCHAR(200)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    tamanho       BIGINT        NOT NULL,
    criado_em     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_arquivos_apoio_dono CHECK (num_nonnulls(aula_id, modulo_id) = 1),
    CONSTRAINT ck_arquivos_apoio_tamanho CHECK (tamanho > 0),
    CONSTRAINT uq_arquivos_apoio_chave UNIQUE (chave)
);

CREATE INDEX ix_arquivos_apoio_aula ON arquivos_apoio (aula_id);
CREATE INDEX ix_arquivos_apoio_modulo ON arquivos_apoio (modulo_id);
`````

### 3. Domínio

`backend/src/main/java/br/com/celke/cursos/arquivo/domain/FormatoArquivo.java`:

`````java
package br.com.celke.cursos.arquivo.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos nos arquivos de apoio, cada um conferido pelo conteúdo real depois do envio (o tipo
 * informado pelo navegador não basta): ZIP e PDF pela assinatura, texto por não ter byte nulo no início.
 */
public enum FormatoArquivo {
    ZIP("application/zip"),
    PDF("application/pdf"),
    TEXTO("text/plain");

    /** Bytes lidos do início do arquivo para a conferência. */
    public static final int BYTES_CONFERENCIA = 512;

    private static final byte[] ZIP_LOCAL = {'P', 'K', 3, 4};
    private static final byte[] ZIP_VAZIO = {'P', 'K', 5, 6};
    private static final byte[] PDF_INICIO = {'%', 'P', 'D', 'F', '-'};

    private final String contentType;

    FormatoArquivo(String contentType) {
        this.contentType = contentType;
    }

    public static Optional<FormatoArquivo> doContentType(String contentType) {
        return Arrays.stream(values())
                .filter(f -> f.contentType.equals(contentType))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public boolean conteudoConfere(byte[] inicio) {
        return switch (this) {
            case ZIP -> comeca(inicio, ZIP_LOCAL) || comeca(inicio, ZIP_VAZIO);
            case PDF -> comeca(inicio, PDF_INICIO);
            case TEXTO -> inicio.length > 0 && !temByteNulo(inicio);
        };
    }

    private static boolean comeca(byte[] inicio, byte[] assinatura) {
        return inicio.length >= assinatura.length
                && Arrays.equals(inicio, 0, assinatura.length, assinatura, 0, assinatura.length);
    }

    private static boolean temByteNulo(byte[] inicio) {
        for (byte b : inicio) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/domain/NomeArquivo.java`:

`````java
package br.com.celke.cursos.arquivo.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nome sugerido no download, seguro para o cabeçalho {@code Content-Disposition}: sem caminho, sem acento,
 * só letras, números, ponto, hífen e sublinhado, e com a extensão do formato real.
 */
public final class NomeArquivo {

    static final int TAMANHO_MAXIMO = 100;

    private NomeArquivo() {}

    public static String de(String informado, FormatoArquivo formato) {
        String nome = informado == null ? "" : informado.strip();
        // Sem caminho: fica só o que vem depois da última barra
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        base = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        if (base.isEmpty()) {
            base = "arquivo";
        }
        String extensao = "." + extensao(formato);
        if (base.length() > TAMANHO_MAXIMO - extensao.length()) {
            base = base.substring(0, TAMANHO_MAXIMO - extensao.length());
        }
        return base + extensao;
    }

    private static String extensao(FormatoArquivo formato) {
        return switch (formato) {
            case TEXTO -> "txt";
            default -> formato.name().toLowerCase(Locale.ROOT);
        };
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/domain/ArquivoApoio.java`:

`````java
package br.com.celke.cursos.arquivo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Arquivo de apoio de uma aula ou de um módulo (exatamente um dos dois). Guarda só os ids, sem
 * relacionamento JPA com o módulo {@code curso}.
 */
@Entity
@Table(name = "arquivos_apoio")
public class ArquivoApoio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aula_id", updatable = false)
    private Long aulaId;

    @Column(name = "modulo_id", updatable = false)
    private Long moduloId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(nullable = false, length = 200, updatable = false)
    private String chave;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long tamanho;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected ArquivoApoio() {}

    private ArquivoApoio(
            Long aulaId,
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        this.aulaId = aulaId;
        this.moduloId = moduloId;
        this.titulo = titulo.strip();
        this.nomeArquivo = nomeArquivo;
        this.chave = chave;
        this.contentType = formato.contentType();
        this.tamanho = tamanho;
        this.criadoEm = agora;
    }

    public static ArquivoApoio daAula(
            Long aulaId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        return new ArquivoApoio(aulaId, null, titulo, nomeArquivo, chave, formato, tamanho, agora);
    }

    public static ArquivoApoio doModulo(
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String chave,
            FormatoArquivo formato,
            long tamanho,
            Instant agora) {
        return new ArquivoApoio(null, moduloId, titulo, nomeArquivo, chave, formato, tamanho, agora);
    }

    public Long getId() {
        return id;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public Long getModuloId() {
        return moduloId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getChave() {
        return chave;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanho() {
        return tamanho;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/infra/ArquivoApoioRepository.java`:

`````java
package br.com.celke.cursos.arquivo.infra;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoApoioRepository extends JpaRepository<ArquivoApoio, Long> {

    List<ArquivoApoio> findByAulaIdOrderById(Long aulaId);

    List<ArquivoApoio> findByModuloIdOrderById(Long moduloId);
}
`````

### 4. Casos de uso

`backend/src/main/java/br/com/celke/cursos/arquivo/application/ArquivoInvalidoException.java`:

`````java
package br.com.celke.cursos.arquivo.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Arquivo de apoio recusado (formato, tamanho, conteúdo ou envio inválido). */
public class ArquivoInvalidoException extends RegraNegocioException {

    public ArquivoInvalidoException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/application/DonoArquivo.java`:

`````java
package br.com.celke.cursos.arquivo.application;

/** A aula ou o módulo a que o arquivo pertence. */
public record DonoArquivo(Tipo tipo, Long id) {

    public enum Tipo {
        AULA,
        MODULO
    }

    public static DonoArquivo aula(Long id) {
        return new DonoArquivo(Tipo.AULA, id);
    }

    public static DonoArquivo modulo(Long id) {
        return new DonoArquivo(Tipo.MODULO, id);
    }

    /** Trecho da chave no bucket: {@code aulas/<id>} ou {@code modulos/<id>}. */
    public String pasta() {
        return (tipo == Tipo.AULA ? "aulas/" : "modulos/") + id;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/application/ArquivoAdmService.java`:

`````java
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
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/application/ArquivoAlunoService.java`:

`````java
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
`````

### 5. API

`backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoRequests.java`:

`````java
package br.com.celke.cursos.arquivo.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Corpos das rotas de arquivos de apoio do ADM. */
public final class ArquivoRequests {

    private ArquivoRequests() {}

    public record IniciarEnvio(
            @NotBlank String contentType, @Positive long tamanho) {}

    /** O nome do arquivo é limpo e ganha a extensão do formato real. */
    public record Confirmar(
            @NotBlank @Size(max = 200) String chave,
            @NotBlank @Size(max = 150) String titulo,
            @NotBlank @Size(max = 255) String nomeArquivo) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoResponses.java`:

`````java
package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.application.ArquivoAdmService.EnvioArquivo;
import br.com.celke.cursos.arquivo.application.ArquivoAlunoService.Download;
import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import java.time.Instant;

/** Respostas das rotas de arquivos de apoio (ADM e aluno). A chave do bucket nunca sai daqui. */
public final class ArquivoResponses {

    private ArquivoResponses() {}

    public record Arquivo(
            Long id,
            Long aulaId,
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String contentType,
            long tamanho,
            Instant criadoEm) {

        public static Arquivo de(ArquivoApoio a) {
            return new Arquivo(
                    a.getId(),
                    a.getAulaId(),
                    a.getModuloId(),
                    a.getTitulo(),
                    a.getNomeArquivo(),
                    a.getContentType(),
                    a.getTamanho(),
                    a.getCriadoEm());
        }
    }

    /** Onde e como enviar: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioResponse de(EnvioArquivo envio) {
            return new EnvioResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /** URL temporária do download (baixa como anexo, com o nome do arquivo). */
    public record DownloadResponse(String url, Instant expiraEm) {

        public static DownloadResponse de(Download download) {
            return new DownloadResponse(download.url().toString(), download.expiraEm());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoAdmController.java`:

`````java
package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.api.ArquivoResponses.Arquivo;
import br.com.celke.cursos.arquivo.api.ArquivoResponses.EnvioResponse;
import br.com.celke.cursos.arquivo.application.ArquivoAdmService;
import br.com.celke.cursos.arquivo.application.DonoArquivo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Arquivos de apoio")
public class ArquivoAdmController {

    private final ArquivoAdmService arquivoAdmService;

    public ArquivoAdmController(ArquivoAdmService arquivoAdmService) {
        this.arquivoAdmService = arquivoAdmService;
    }

    @GetMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Arquivos da aula")
    public List<Arquivo> listarDaAula(@PathVariable Long aulaId) {
        return lista(DonoArquivo.aula(aulaId));
    }

    @GetMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Arquivos do módulo")
    public List<Arquivo> listarDoModulo(@PathVariable Long moduloId) {
        return lista(DonoArquivo.modulo(moduloId));
    }

    @PostMapping("/aulas/{aulaId}/arquivos/envio")
    @Operation(
            summary = "Iniciar o envio de um arquivo da aula",
            description = "ZIP, PDF ou TXT até 200 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioResponse iniciarEnvioDaAula(
            @PathVariable Long aulaId, @Valid @RequestBody ArquivoRequests.IniciarEnvio request) {
        return iniciar(DonoArquivo.aula(aulaId), request);
    }

    @PostMapping("/modulos/{moduloId}/arquivos/envio")
    @Operation(
            summary = "Iniciar o envio de um arquivo do módulo",
            description = "ZIP, PDF ou TXT até 200 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioResponse iniciarEnvioDoModulo(
            @PathVariable Long moduloId, @Valid @RequestBody ArquivoRequests.IniciarEnvio request) {
        return iniciar(DonoArquivo.modulo(moduloId), request);
    }

    @PostMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Confirmar um arquivo da aula", description = "Confere o arquivo enviado e o publica.")
    public ResponseEntity<Arquivo> confirmarDaAula(
            @PathVariable Long aulaId, @Valid @RequestBody ArquivoRequests.Confirmar request) {
        return confirmar(DonoArquivo.aula(aulaId), request);
    }

    @PostMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Confirmar um arquivo do módulo", description = "Confere o arquivo enviado e o publica.")
    public ResponseEntity<Arquivo> confirmarDoModulo(
            @PathVariable Long moduloId, @Valid @RequestBody ArquivoRequests.Confirmar request) {
        return confirmar(DonoArquivo.modulo(moduloId), request);
    }

    @DeleteMapping("/arquivos/{id}")
    @Operation(summary = "Remover arquivo", description = "Apaga o registro e o arquivo do armazenamento.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        arquivoAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private List<Arquivo> lista(DonoArquivo dono) {
        return arquivoAdmService.listar(dono).stream().map(Arquivo::de).toList();
    }

    private EnvioResponse iniciar(DonoArquivo dono, ArquivoRequests.IniciarEnvio request) {
        return EnvioResponse.de(arquivoAdmService.iniciarEnvio(dono, request.contentType(), request.tamanho()));
    }

    private ResponseEntity<Arquivo> confirmar(DonoArquivo dono, ArquivoRequests.Confirmar request) {
        Arquivo arquivo =
                Arquivo.de(arquivoAdmService.confirmar(dono, request.chave(), request.titulo(), request.nomeArquivo()));
        return ResponseEntity.created(URI.create("/api/adm/arquivos/" + arquivo.id()))
                .body(arquivo);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/arquivo/api/ArquivoAlunoController.java`:

`````java
package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.api.ArquivoResponses.Arquivo;
import br.com.celke.cursos.arquivo.api.ArquivoResponses.DownloadResponse;
import br.com.celke.cursos.arquivo.application.ArquivoAlunoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Arquivos de apoio na área do aluno, sempre no escopo do usuário do token. */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno — Arquivos de apoio")
public class ArquivoAlunoController {

    private final ArquivoAlunoService arquivoAlunoService;

    public ArquivoAlunoController(ArquivoAlunoService arquivoAlunoService) {
        this.arquivoAlunoService = arquivoAlunoService;
    }

    @GetMapping("/aulas/{aulaId}/arquivos")
    @Operation(summary = "Arquivos da aula", description = "Mesmo acesso da aula (aula gratuita inclusive).")
    public List<Arquivo> daAula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return arquivoAlunoService.daAula(usuario(jwt), aulaId).stream()
                .map(Arquivo::de)
                .toList();
    }

    @GetMapping("/modulos/{moduloId}/arquivos")
    @Operation(summary = "Arquivos do módulo", description = "Exige matrícula vigente na turma.")
    public List<Arquivo> doModulo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long moduloId) {
        return arquivoAlunoService.doModulo(usuario(jwt), moduloId).stream()
                .map(Arquivo::de)
                .toList();
    }

    @GetMapping("/arquivos/{id}/download")
    @Operation(summary = "Baixar arquivo", description = "Confere o acesso e devolve uma URL válida por 5 minutos.")
    public ResponseEntity<DownloadResponse> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DownloadResponse.de(arquivoAlunoService.download(usuario(jwt), id)));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/arquivo/domain/FormatoArquivoTest.java`:

`````java
package br.com.celke.cursos.arquivo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FormatoArquivoTest {

    private static byte[] texto(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"application/zip, ZIP", "application/pdf, PDF", "text/plain, TEXTO"})
    @DisplayName("reconhece os tipos aceitos")
    void tiposAceitos(String contentType, FormatoArquivo esperado) {
        assertThat(FormatoArquivo.doContentType(contentType)).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/x-msdownload", "text/html", "image/svg+xml", "APPLICATION/ZIP", ""})
    @DisplayName("recusa os outros tipos (executável, HTML, SVG...)")
    void tiposRecusados(String contentType) {
        assertThat(FormatoArquivo.doContentType(contentType)).isEmpty();
    }

    @Test
    @DisplayName("ZIP: assinatura de arquivo local ou de ZIP vazio")
    void zip() {
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K', 3, 4, 20, 0}))
                .isTrue();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K', 5, 6}))
                .isTrue();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(texto("MZ\u0090executável")))
                .isFalse();
        assertThat(FormatoArquivo.ZIP.conteudoConfere(new byte[] {'P', 'K'})).isFalse();
    }

    @Test
    @DisplayName("PDF: começa com %PDF-")
    void pdf() {
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("%PDF-1.7\n"))).isTrue();
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("<html><script>"))).isFalse();
        assertThat(FormatoArquivo.PDF.conteudoConfere(texto("%PDF"))).isFalse();
    }

    @Test
    @DisplayName("texto: sem byte nulo no início; vazio não passa")
    void textoPuro() {
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(texto("public class Ola {}\n// acentuação")))
                .isTrue();
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(new byte[] {'P', 'K', 3, 4, 0, 0}))
                .isFalse();
        assertThat(FormatoArquivo.TEXTO.conteudoConfere(new byte[0])).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/domain/NomeArquivoTest.java`:

`````java
package br.com.celke.cursos.arquivo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NomeArquivoTest {

    @ParameterizedTest(name = "\"{0}\" ({1}) → {2}")
    @CsvSource({
        "codigo-fonte.zip,            ZIP,   codigo-fonte.zip",
        "Apostila Módulo 1.pdf,       PDF,   Apostila-Modulo-1.pdf",
        "../../etc/passwd,            TEXTO, passwd.txt",
        "C:\\Users\\ana\\aula.zip,    ZIP,   aula.zip",
        "relatorio.exe,               PDF,   relatorio.pdf",
        "sem extensao,                ZIP,   sem-extensao.zip",
        "'\"x\";filename=y.zip',      ZIP,   x-filename-y.zip",
        ".oculto,                     TEXTO, oculto.txt",
        "'   ',                       PDF,   arquivo.pdf",
        "%%%.zip,                     ZIP,   arquivo.zip",
    })
    @DisplayName("limpa o nome: sem caminho, sem acento, sem aspas, com a extensão do formato real")
    void nomes(String informado, FormatoArquivo formato, String esperado) {
        assertThat(NomeArquivo.de(informado, formato)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nome nulo vira arquivo")
    void nulo() {
        assertThat(NomeArquivo.de(null, FormatoArquivo.ZIP)).isEqualTo("arquivo.zip");
    }

    @Test
    @DisplayName("nome longo é cortado em 100 caracteres, mantendo a extensão")
    void longo() {
        String nome = NomeArquivo.de("a".repeat(300) + ".pdf", FormatoArquivo.PDF);

        assertThat(nome).hasSize(NomeArquivo.TAMANHO_MAXIMO).endsWith(".pdf");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/api/ArquivoRequestsTest.java`:

`````java
package br.com.celke.cursos.arquivo.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.arquivo.api.ArquivoRequests.Confirmar;
import br.com.celke.cursos.arquivo.api.ArquivoRequests.IniciarEnvio;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArquivoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object dto) {
        return VALIDATOR.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("início do envio: tipo obrigatório e tamanho positivo")
    void iniciar() {
        assertThat(camposInvalidos(new IniciarEnvio("application/zip", 1))).isEmpty();
        assertThat(camposInvalidos(new IniciarEnvio(" ", 0))).containsExactlyInAnyOrder("contentType", "tamanho");
        assertThat(camposInvalidos(new IniciarEnvio("application/zip", -1))).containsExactly("tamanho");
    }

    @Test
    @DisplayName("confirmação: chave, título e nome obrigatórios, com os limites de tamanho")
    void confirmar() {
        assertThat(camposInvalidos(new Confirmar("k", "Código-fonte", "aula.zip")))
                .isEmpty();
        assertThat(camposInvalidos(new Confirmar("", " ", null)))
                .containsExactlyInAnyOrder("chave", "titulo", "nomeArquivo");
        assertThat(camposInvalidos(new Confirmar("k".repeat(201), "t".repeat(151), "n".repeat(256))))
                .containsExactlyInAnyOrder("chave", "titulo", "nomeArquivo");
        assertThat(camposInvalidos(new Confirmar("k".repeat(200), "t".repeat(150), "n".repeat(255))))
                .isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/application/ArquivoAdmServiceTest.java`:

`````java
package br.com.celke.cursos.arquivo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.domain.FormatoArquivo;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArquivoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String UUID = "0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";
    private static final String UPLOAD = "uploads/arquivos/aulas/9/" + UUID;
    private static final String DESTINO = "arquivos/aulas/9/" + UUID;
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};

    @Mock
    private ArquivoApoioRepository arquivos;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private StorageService storage;

    private ArquivoAdmService service;

    @BeforeEach
    void criar() {
        service = new ArquivoAdmService(arquivos, conteudoTurmaService, storage, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private void aulaExiste() {
        when(conteudoTurmaService.aulaExiste(9L)).thenReturn(true);
    }

    @Nested
    class Iniciar {

        @Test
        @DisplayName("aula: URL de PUT em uploads/arquivos/aulas/<id>/<uuid> com o tipo informado")
        void aula() throws Exception {
            aulaExiste();
            when(storage.urlUpload(
                            startsWith("uploads/arquivos/aulas/9/"), eq("application/zip"), eq(Duration.ofMinutes(15))))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            var envio = service.iniciarEnvio(DonoArquivo.aula(9L), "application/zip", 100);

            assertThat(envio.chave()).matches("uploads/arquivos/aulas/9/[0-9a-f-]{36}");
            assertThat(envio.contentType()).isEqualTo("application/zip");
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        }

        @Test
        @DisplayName("módulo usa a pasta do módulo")
        void modulo() throws Exception {
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(true);
            when(storage.urlUpload(startsWith("uploads/arquivos/modulos/4/"), eq("application/pdf"), any()))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            assertThat(service.iniciarEnvio(DonoArquivo.modulo(4L), "application/pdf", 1)
                            .chave())
                    .startsWith("uploads/arquivos/modulos/4/");
        }

        @Test
        @DisplayName("tipo fora da lista e tamanho acima de 200 MB são recusados sem assinar")
        void recusas() {
            aulaExiste();

            assertThatThrownBy(() -> service.iniciarEnvio(DonoArquivo.aula(9L), "text/html", 10))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("Envie um arquivo ZIP, PDF ou TXT.");
            assertThatThrownBy(() -> service.iniciarEnvio(
                            DonoArquivo.aula(9L), "application/zip", ArquivoAdmService.TAMANHO_MAXIMO + 1))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O arquivo pode ter no máximo 200 MB.");
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("aula ou módulo inexistente responde 404")
        void donoInexistente() {
            when(conteudoTurmaService.aulaExiste(9L)).thenReturn(false);
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(false);

            assertThatThrownBy(() -> service.iniciarEnvio(DonoArquivo.aula(9L), "application/zip", 1))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Aula não encontrada.");
            assertThatThrownBy(() -> service.listar(DonoArquivo.modulo(4L)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Módulo não encontrado.");
        }
    }

    @Nested
    class Confirmar {

        @Test
        @DisplayName("confere, move para arquivos/, apaga o envio e registra com o nome limpo")
        void confirma() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
            when(arquivos.save(any(ArquivoApoio.class))).thenAnswer(i -> i.getArgument(0));

            ArquivoApoio arquivo = service.confirmar(DonoArquivo.aula(9L), UPLOAD, " Código-fonte ", "Aula 1.zip");

            verify(storage).copiar(UPLOAD, DESTINO);
            verify(storage).excluir(UPLOAD);
            assertThat(arquivo.getChave()).isEqualTo(DESTINO);
            assertThat(arquivo.getAulaId()).isEqualTo(9L);
            assertThat(arquivo.getModuloId()).isNull();
            assertThat(arquivo.getTitulo()).isEqualTo("Código-fonte");
            assertThat(arquivo.getNomeArquivo()).isEqualTo("Aula-1.zip");
            assertThat(arquivo.getContentType()).isEqualTo("application/zip");
            assertThat(arquivo.getTamanho()).isEqualTo(6);
            assertThat(arquivo.getCriadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("chave de outro dono ou fora do formato é recusada sem tocar no bucket")
        void chaveInvalida() {
            aulaExiste();
            for (String chave : List.of(
                    "uploads/arquivos/aulas/10/" + UUID,
                    "uploads/arquivos/aulas/9/../../capas/x",
                    "arquivos/aulas/9/" + UUID)) {
                assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), chave, "t", "a.zip"))
                        .isInstanceOf(ArquivoInvalidoException.class)
                        .hasMessage("Envio de arquivo inválido. Comece um novo envio.");
            }
            verify(storage, never()).info(anyString());
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("arquivo não enviado")
        void naoEnviado() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O arquivo não foi enviado. Comece um novo envio.");
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("conteúdo que não confere com o tipo (extensão falsa) é recusado e o envio apagado")
        void conteudoFalso() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(20, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA))
                    .thenReturn("<html><script>".getBytes());

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(ArquivoInvalidoException.class)
                    .hasMessage("O conteúdo do arquivo não confere com o tipo informado.");
            verify(storage).excluir(UPLOAD);
            verify(storage, never()).copiar(anyString(), anyString());
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("tamanho real acima do limite, vazio ou tipo gravado fora da lista: recusado e apagado")
        void recusasDoArquivoReal() {
            aulaExiste();
            when(storage.info(UPLOAD))
                    .thenReturn(Optional.of(new InfoArquivo(ArquivoAdmService.TAMANHO_MAXIMO + 1, "application/zip")))
                    .thenReturn(Optional.of(new InfoArquivo(0, "application/zip")))
                    .thenReturn(Optional.of(new InfoArquivo(10, "text/html")));

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("O arquivo pode ter no máximo 200 MB.");
            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("O arquivo está vazio.");
            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .hasMessage("Envie um arquivo ZIP, PDF ou TXT.");
            verify(storage, times(3)).excluir(UPLOAD);
            verify(arquivos, never()).save(any());
        }

        @Test
        @DisplayName("falha ao registrar apaga o arquivo já movido")
        void falhaAoRegistrar() {
            aulaExiste();
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
            when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
            when(arquivos.save(any(ArquivoApoio.class))).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                    .isInstanceOf(IllegalStateException.class);
            verify(storage).excluir(DESTINO);
        }

        @Test
        @DisplayName("arquivo do módulo fica com o id do módulo")
        void modulo() {
            String upload = "uploads/arquivos/modulos/4/" + UUID;
            when(conteudoTurmaService.moduloExiste(4L)).thenReturn(true);
            when(storage.info(upload)).thenReturn(Optional.of(new InfoArquivo(9, "application/pdf")));
            when(storage.baixarInicio(upload, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn("%PDF-1.7".getBytes());
            ArgumentCaptor<ArquivoApoio> captor = ArgumentCaptor.forClass(ArquivoApoio.class);
            when(arquivos.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            service.confirmar(DonoArquivo.modulo(4L), upload, "Apostila", "apostila");

            assertThat(captor.getValue().getModuloId()).isEqualTo(4L);
            assertThat(captor.getValue().getAulaId()).isNull();
            assertThat(captor.getValue().getNomeArquivo()).isEqualTo("apostila.pdf");
            verify(storage).copiar(upload, "arquivos/modulos/4/" + UUID);
        }
    }

    @Test
    @DisplayName("listar devolve os arquivos do dono")
    void listar() {
        aulaExiste();
        List<ArquivoApoio> lista = List.of();
        when(arquivos.findByAulaIdOrderById(9L)).thenReturn(lista);

        assertThat(service.listar(DonoArquivo.aula(9L))).isSameAs(lista);
    }

    @Test
    @DisplayName("excluir apaga o registro e o arquivo; inexistente responde 404")
    void excluir() {
        ArquivoApoio arquivo = ArquivoApoio.daAula(9L, "t", "a.zip", DESTINO, FormatoArquivo.ZIP, 6, AGORA);
        when(arquivos.findById(1L)).thenReturn(Optional.of(arquivo));
        when(arquivos.findById(2L)).thenReturn(Optional.empty());

        service.excluir(1L);

        verify(arquivos).delete(arquivo);
        verify(storage).excluir(DESTINO);
        assertThatThrownBy(() -> service.excluir(2L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Arquivo não encontrado.");
    }

    @Test
    @DisplayName("falha do bucket ao copiar não registra nada e ainda apaga o envio")
    void falhaAoCopiar() {
        aulaExiste();
        when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(6, "application/zip")));
        when(storage.baixarInicio(UPLOAD, FormatoArquivo.BYTES_CONFERENCIA)).thenReturn(ZIP);
        doThrow(new IllegalStateException("S3")).when(storage).copiar(UPLOAD, DESTINO);

        assertThatThrownBy(() -> service.confirmar(DonoArquivo.aula(9L), UPLOAD, "t", "a.zip"))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).excluir(UPLOAD);
        verify(arquivos, never()).save(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/application/ArquivoAlunoServiceTest.java`:

`````java
package br.com.celke.cursos.arquivo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import br.com.celke.cursos.arquivo.domain.FormatoArquivo;
import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloContexto;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArquivoAlunoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Duration CINCO_MINUTOS = Duration.ofMinutes(5);

    @Mock
    private ArquivoApoioRepository arquivos;

    @Mock
    private AcessoAulaService acessoAulaService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private StorageService storage;

    private ArquivoAlunoService service;

    @BeforeEach
    void criar() {
        service = new ArquivoAlunoService(
                arquivos, acessoAulaService, conteudoTurmaService, storage, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ArquivoApoio daAula() {
        return ArquivoApoio.daAula(9L, "Código", "aula.zip", "arquivos/aulas/9/x", FormatoArquivo.ZIP, 6, AGORA);
    }

    private static ArquivoApoio doModulo() {
        return ArquivoApoio.doModulo(
                4L, "Apostila", "apostila.pdf", "arquivos/modulos/4/y", FormatoArquivo.PDF, 9, AGORA);
    }

    private static URL url() throws Exception {
        return URI.create("http://media.localhost/assinado").toURL();
    }

    @Test
    @DisplayName("arquivos da aula passam pela política da aula")
    void listaDaAula() {
        when(arquivos.findByAulaIdOrderById(9L)).thenReturn(List.of(daAula()));

        assertThat(service.daAula(7L, 9L)).hasSize(1);
        verify(acessoAulaService).exigirAcesso(7L, 9L);
    }

    @Test
    @DisplayName("arquivos do módulo exigem acesso à turma do módulo; módulo inativo ou inexistente responde 404")
    void listaDoModulo() {
        when(conteudoTurmaService.moduloAtivo(4L)).thenReturn(Optional.of(new ModuloContexto(4L, "M", 5L)));
        when(conteudoTurmaService.moduloAtivo(8L)).thenReturn(Optional.empty());
        when(arquivos.findByModuloIdOrderById(4L)).thenReturn(List.of(doModulo()));

        assertThat(service.doModulo(7L, 4L)).hasSize(1);
        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
        assertThatThrownBy(() -> service.doModulo(7L, 8L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Módulo não encontrado.");
    }

    @Test
    @DisplayName("download do arquivo da aula: URL de 5 minutos, como anexo com o nome do arquivo")
    void downloadDaAula() throws Exception {
        when(arquivos.findById(1L)).thenReturn(Optional.of(daAula()));
        when(storage.urlDownload("arquivos/aulas/9/x", CINCO_MINUTOS, "aula.zip"))
                .thenReturn(url());

        var download = service.download(7L, 1L);

        verify(acessoAulaService).exigirAcesso(7L, 9L);
        assertThat(download.url()).isEqualTo(url());
        assertThat(download.expiraEm()).isEqualTo(AGORA.plus(CINCO_MINUTOS));
    }

    @Test
    @DisplayName("download do arquivo do módulo passa pela turma do módulo")
    void downloadDoModulo() throws Exception {
        when(arquivos.findById(2L)).thenReturn(Optional.of(doModulo()));
        when(conteudoTurmaService.moduloAtivo(4L)).thenReturn(Optional.of(new ModuloContexto(4L, "M", 5L)));
        when(storage.urlDownload("arquivos/modulos/4/y", CINCO_MINUTOS, "apostila.pdf"))
                .thenReturn(url());

        assertThat(service.download(7L, 2L).url()).isEqualTo(url());
        verify(acessoAulaService).exigirAcessoTurma(7L, 5L);
    }

    @Test
    @DisplayName("sem acesso, nenhuma URL é assinada; arquivo inexistente responde 404")
    void semAcesso() {
        when(arquivos.findById(1L)).thenReturn(Optional.of(daAula()));
        when(arquivos.findById(3L)).thenReturn(Optional.empty());
        doThrow(new AcessoNegadoException("sem acesso")).when(acessoAulaService).exigirAcesso(7L, 9L);

        assertThatThrownBy(() -> service.download(7L, 1L)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.download(7L, 3L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Arquivo não encontrado.");
        verify(storage, never()).urlDownload(anyString(), any(), anyString());
    }
}
`````

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/arquivo/ArquivoITHelper.java`:

`````java
package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Envia arquivos de apoio pela API do ADM (URL pré-assinada → PUT no Garage → confirmação). */
final class ArquivoITHelper {

    private final CursoITHelper adm;
    private final HttpClient http = HttpClient.newHttpClient();

    ArquivoITHelper(CursoITHelper adm) {
        this.adm = adm;
    }

    /** Inicia o envio em {@code dono} ({@code /api/adm/aulas/<id>} ou {@code .../modulos/<id>}), faz o PUT e devolve a chave. */
    String enviar(String dono, byte[] conteudo, String contentType) throws Exception {
        MvcTestResult inicio = adm.enviar(
                "POST",
                dono + "/arquivos/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, conteudo.length));
        assertThat(inicio).hasStatusOk();
        String corpo = CursoITHelper.corpo(inicio);
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(conteudo))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    MvcTestResult confirmar(String dono, String chave, String titulo, String nomeArquivo) {
        return adm.enviar(
                "POST",
                dono + "/arquivos",
                "{\"chave\":\"%s\",\"titulo\":\"%s\",\"nomeArquivo\":\"%s\"}".formatted(chave, titulo, nomeArquivo));
    }

    /** Envia e confirma; confere o 201 e devolve o id do arquivo. */
    long publicar(String dono, byte[] conteudo, String contentType, String titulo, String nomeArquivo)
            throws Exception {
        return CursoITHelper.idCriado(confirmar(dono, enviar(dono, conteudo, contentType), titulo, nomeArquivo));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/ArquivoAcessoIT.java`:

`````java
package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de papéis das rotas de arquivos de apoio do ADM (401 anônimo, 403 ALUNO, ADMIN passa
 * pela segurança).
 * Não cobre: o comportamento de cada rota e o acesso do aluno (ArquivoIT).
 * Dado criado: a cada caso, curso it-<stamp> com turma, módulo, aula e um arquivo TXT publicado na aula
 * (ids reais nas rotas).
 */
class ArquivoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long modulo;
    private long aula;
    private long arquivo;

    @BeforeEach
    void criarEstrutura() throws Exception {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        modulo = adm.criarModulo(adm.criarTurma(curso, "Turma"), "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
        arquivo = new ArquivoITHelper(adm)
                .publicar(
                        "/api/adm/aulas/" + aula,
                        "anotações da aula".getBytes(StandardCharsets.UTF_8),
                        "text/plain",
                        "Notas",
                        "notas.txt");
    }

    static Stream<Arguments> rotas() {
        String envio = "{\"contentType\":\"application/zip\",\"tamanho\":10}";
        String confirmacao = "{\"chave\":\"x\",\"titulo\":\"T\",\"nomeArquivo\":\"a.zip\"}";
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/aulas/{a}/arquivos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/adm/modulos/{m}/arquivos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/arquivos/envio", envio, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/arquivos/envio", envio, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/aulas/{a}/arquivos", confirmacao, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/arquivos", confirmacao, HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.DELETE, "/api/adm/arquivos/{f}", null, HttpStatus.NO_CONTENT));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token) {
        String uri = rota.replace("{a}", String.valueOf(aula))
                .replace("{m}", String.valueOf(modulo))
                .replace("{f}", String.valueOf(arquivo));
        var requisicao = mvc.method(metodo).uri(uri);
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
        assertThat(chamar(metodo, rota, corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
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
}
`````

`backend/src/test/java/br/com/celke/cursos/arquivo/ArquivoIT.java`:

`````java
package br.com.celke.cursos.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.arquivo.infra.ArquivoApoioRepository;
import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.matricula.MatriculaITHelper;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: arquivos de apoio com o Garage real — envio pelo ADM (confirmação que confere o conteúdo e
 * move para arquivos/), listagem e download do aluno pela política (matriz de acesso: vigente, sem
 * matrícula, vencida, cancelada, ainda não iniciada, aula gratuita; módulo só com matrícula), URL de
 * download baixando como anexo, exclusão e os bloqueios (extensão falsa, chave de outra aula, aula e
 * módulo com arquivos).
 * Não cobre: formatos e nomes caso a caso (FormatoArquivoTest, NomeArquivoTest, ArquivoAdmServiceTest).
 * Dado criado: curso it-<stamp> publicado, arquivos em uploads/arquivos/ e arquivos/, e um aluno próprio.
 */
class ArquivoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 'c', 'o', 'd', 'i', 'g', 'o'};
    private static final byte[] PDF = "%PDF-1.7\n%apostila\n".getBytes(StandardCharsets.UTF_8);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private ArquivoApoioRepository arquivos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private MatriculaITHelper helper;
    private ArquivoITHelper envio;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoEAluno() {
        CursoITHelper adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        helper = new MatriculaITHelper(adm);
        envio = new ArquivoITHelper(adm);
        estrutura = helper.cursoPublicado(stamp());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private String aula(long id) {
        return "/api/adm/aulas/" + id;
    }

    private String modulo(long id) {
        return "/api/adm/modulos/" + id;
    }

    private MvcTestResult get(String uri, String tokenUsado) {
        var requisicao = mvc.get().uri(uri);
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private MvcTestResult download(long arquivo) {
        return get("/api/aluno/arquivos/" + arquivo + "/download", token);
    }

    private void matricularVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));
    }

    @Test
    @DisplayName("ADM publica o arquivo da aula; o aluno matriculado lista e baixa como anexo; ADM remove")
    void cicloDoArquivoDaAula() throws Exception {
        String upload = envio.enviar(aula(estrutura.aulaPaga()), ZIP, "application/zip");
        MvcTestResult criado =
                envio.confirmar(aula(estrutura.aulaPaga()), upload, "Código-fonte", "Código da aula 1.zip");
        assertThat(criado).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"aulaId":%d,"moduloId":null,"titulo":"Código-fonte","nomeArquivo":"Codigo-da-aula-1.zip",
                 "contentType":"application/zip","tamanho":%d}
                """.formatted(
                        estrutura.aulaPaga(), ZIP.length));
        assertThat(CursoITHelper.corpo(criado)).doesNotContain("chave").doesNotContain("uploads/");
        long id = CursoITHelper.idCriado(criado);
        String chave = arquivos.findById(id).orElseThrow().getChave();
        assertThat(chave).startsWith("arquivos/aulas/" + estrutura.aulaPaga() + "/");
        assertThat(storage.info(upload)).as("envio apagado de uploads/").isEmpty();
        assertThat(storage.info(chave)).isPresent();
        assertThat(get(aula(estrutura.aulaPaga()) + "/arquivos", tokenAdmin()))
                .bodyJson()
                .extractingPath("$[*].id")
                .isEqualTo(List.of((int) id));

        matricularVigente();
        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].titulo")
                .isEqualTo(List.of("Código-fonte"));
        MvcTestResult link = download(id);
        assertThat(link).hasStatusOk();
        assertThat(link.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String url = JsonPath.read(CursoITHelper.corpo(link), "$.url");
        assertThat(url).startsWith(armazenamento.endpointPublico()).contains("X-Amz-Expires=300");
        HttpResponse<byte[]> baixado =
                http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofByteArray());
        assertThat(baixado.statusCode()).isEqualTo(200);
        assertThat(baixado.body()).isEqualTo(ZIP);
        assertThat(baixado.headers().firstValue("Content-Disposition"))
                .contains("attachment; filename=\"Codigo-da-aula-1.zip\"");

        // Aula com arquivo não pode ser excluída
        assertThat(helper.adm().enviar("DELETE", aula(estrutura.aulaPaga()), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A aula tem vídeo ou arquivos de apoio. Remova-os ou inative a aula.");

        assertThat(helper.adm().enviar("DELETE", "/api/adm/arquivos/" + id, null))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(chave)).isEmpty();
        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .bodyJson()
                .extractingPath("$")
                .isEqualTo(List.of());
        assertThat(download(id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Arquivo não encontrado.");
    }

    private String tokenAdmin() {
        return login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
    }

    static Stream<Arguments> semMatriculaVigente() {
        return Stream.of(
                Arguments.of("sem matrícula"),
                Arguments.of("vencida"),
                Arguments.of("cancelada"),
                Arguments.of("ainda não iniciada"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semMatriculaVigente")
    @DisplayName("sem matrícula vigente: 403 na lista e no download do arquivo da aula paga e do módulo")
    void semAcesso(String situacao) throws Exception {
        long daAula = envio.publicar(aula(estrutura.aulaPaga()), ZIP, "application/zip", "Código", "c.zip");
        long doModulo = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "a.pdf");
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaPaga() + "/arquivos", token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(download(daAula))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(download(doModulo))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("aula gratuita: arquivo liberado sem matrícula; o do módulo continua exigindo matrícula")
    void gratuita() throws Exception {
        long daGratuita = envio.publicar(aula(estrutura.aulaGratuita()), ZIP, "application/zip", "Código", "c.zip");
        long doModulo = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "a.pdf");

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaGratuita() + "/arquivos", token))
                .hasStatusOk();
        assertThat(download(daGratuita)).hasStatusOk();
        assertThat(download(doModulo)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("módulo: matriculado baixa; módulo inativo some; módulo só com arquivos não é excluído")
    void arquivoDoModulo() throws Exception {
        long id = envio.publicar(modulo(estrutura.modulo()), PDF, "application/pdf", "Apostila", "Apostila.pdf");
        matricularVigente();

        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].nomeArquivo")
                .isEqualTo("Apostila.pdf");
        assertThat(download(id)).hasStatusOk();

        helper.adm().status("/api/adm/modulos/" + estrutura.modulo() + "/status", "INATIVO");
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Módulo não encontrado.");

        long vazio = helper.adm().criarModulo(estrutura.turma(), "Só materiais");
        envio.publicar(modulo(vazio), PDF, "application/pdf", "Extra", "extra.pdf");
        assertThat(helper.adm().enviar("DELETE", modulo(vazio), null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O módulo tem arquivos de apoio. Remova os arquivos ou inative o módulo.");
    }

    @Test
    @DisplayName("conteúdo que não é ZIP enviado como ZIP (extensão falsa) é recusado e apagado")
    void extensaoFalsa() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        String upload = envio.enviar(aula(estrutura.aulaPaga()), html, "application/zip");

        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), upload, "Falso", "falso.zip"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O conteúdo do arquivo não confere com o tipo informado.");
        assertThat(storage.info(upload)).isEmpty();
        assertThat(arquivos.findByAulaIdOrderById(estrutura.aulaPaga())).isEmpty();
    }

    @Test
    @DisplayName("recusas: tipo não aceito, chave de outra aula, envio não feito, aula inexistente")
    void recusas() throws Exception {
        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                aula(estrutura.aulaPaga()) + "/arquivos/envio",
                                "{\"contentType\":\"application/x-msdownload\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie um arquivo ZIP, PDF ou TXT.");

        String daGratuita = envio.enviar(aula(estrutura.aulaGratuita()), ZIP, "application/zip");
        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), daGratuita, "Outro", "o.zip"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de arquivo inválido. Comece um novo envio.");
        assertThat(storage.info(daGratuita))
                .as("o envio da outra aula continua lá")
                .isPresent();

        String naoEnviado = JsonPath.read(
                CursoITHelper.corpo(helper.adm()
                        .enviar(
                                "POST",
                                aula(estrutura.aulaPaga()) + "/arquivos/envio",
                                "{\"contentType\":\"text/plain\",\"tamanho\":10}")),
                "$.chave");
        assertThat(envio.confirmar(aula(estrutura.aulaPaga()), naoEnviado, "T", "t.txt"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O arquivo não foi enviado. Comece um novo envio.");

        assertThat(helper.adm()
                        .enviar(
                                "POST",
                                "/api/adm/aulas/999999999/arquivos/envio",
                                "{\"contentType\":\"text/plain\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
    }

    @Test
    @DisplayName("rotas do aluno exigem token")
    void anonimo() throws Exception {
        long id = envio.publicar(aula(estrutura.aulaGratuita()), ZIP, "application/zip", "Código", "c.zip");

        assertThat(get("/api/aluno/aulas/" + estrutura.aulaGratuita() + "/arquivos", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aluno/modulos/" + estrutura.modulo() + "/arquivos", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/aluno/arquivos/" + id + "/download", null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

### 8. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/arquivo/ArquivoE2E.java`:

`````java
package br.com.celke.cursos.e2e.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.Midia;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM envia o código-fonte da aula direto ao Garage pelo Caddy e publica; o aluno sem
 * matrícula recebe 403; matriculado, baixa o arquivo pelo host de mídia como anexo.
 */
class ArquivoE2E {

    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 'e', '2', 'e'};

    @Test
    @DisplayName("download do arquivo de apoio: bloqueado sem matrícula, liberado com matrícula")
    void downloadPermitidoEBloqueado() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();

        // 1. Curso publicado com turma ativa e uma aula paga
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Arquivos e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long aula = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Aula com código\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        // 2. Envio: URL pré-assinada → PUT no Garage pelo Caddy → confirmação na API
        JsonPath envio = adm.requisicao()
                .body("{\"contentType\":\"application/zip\",\"tamanho\":%d}".formatted(ZIP.length))
                .post("/api/adm/aulas/" + aula + "/arquivos/envio")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        URI url = URI.create(envio.getString("url"));
        assertThat(url.getHost()).isEqualTo(Midia.HOST);
        Midia.requisicao()
                .contentType("application/zip")
                .body(ZIP)
                .put(Midia.caminho(url))
                .then()
                .statusCode(200);
        long arquivo = adm.criar(
                "/api/adm/aulas/" + aula + "/arquivos",
                "{\"chave\":\"%s\",\"titulo\":\"Código-fonte\",\"nomeArquivo\":\"codigo.zip\"}"
                        .formatted(envio.getString("chave")));

        // 3. Aluno sem matrícula: 403
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        aluno(token).get("/api/aluno/arquivos/" + arquivo + "/download").then().statusCode(403);

        // 4. Matriculado: URL de download no host de mídia, baixando o mesmo conteúdo como anexo
        adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, Instant.now().plus(Duration.ofDays(30))));
        URI download = URI.create(aluno(token)
                .get("/api/aluno/arquivos/" + arquivo + "/download")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("url"));
        assertThat(download.getHost()).isEqualTo(Midia.HOST);
        Response baixado = Midia.requisicao()
                .get(Midia.caminho(download))
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertThat(baixado.asByteArray()).isEqualTo(ZIP);
        assertThat(baixado.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"codigo.zip\"");
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

## Antes de executar

Fase 5 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/arquivos-apoio
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 611 testes unitários e 318 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 15 jornadas passam, entre elas a `ArquivoE2E`.

No ambiente local, pelo Swagger, com uma aula e um aluno matriculado:

- Inicie o envio de um ZIP, faça o PUT com `curl -X PUT -H "Content-Type: application/zip" --data-binary @codigo.zip "<url>"`
  e confirme com a chave: o arquivo aparece em `GET /api/adm/aulas/{id}/arquivos`.
- Como aluno, `GET /api/aluno/arquivos/{id}/download` devolve a URL; o `curl -OJ "<url>"` salva com o
  nome do arquivo. Sem matrícula, 403.
- Envie um HTML como `application/zip`: a confirmação responde 400 e nada é publicado.

## Depois de executar

```bash
git add backend/ infra/ docs/
git commit -m "Arquivos de apoio"
git push -u origin feature/arquivos-apoio
```

Abra o Pull Request de `feature/arquivos-apoio` para `homolog`.

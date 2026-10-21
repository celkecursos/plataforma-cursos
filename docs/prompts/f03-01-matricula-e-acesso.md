# Matrícula e política única de acesso

**Fase:** 3
**Branch:** `feature/matricula-acesso`

## Prompt

Vamos criar o módulo `matricula` do backend: a matrícula manual pelo ADM, a área do aluno com os
cursos dele e a política única que decide o acesso ao conteúdo. Siga o `backend/CLAUDE.md` e o padrão
das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Matrícula** (migration `V7`): usuário e turma (chaves estrangeiras `RESTRICT`), início, fim
  opcional (sem fim, não vence), status `ATIVA` ou `CANCELADA`, forma de pagamento (`MANUAL`,
  `CORTESIA`, `PIX`, `CARTAO`, `BOLETO`), transação e data de arquivamento pelo aluno. Índices únicos
  parciais: uma matrícula ativa por aluno e turma, e cada transação gera uma matrícula só.
- **Situação calculada, não gravada**: `Matricula.situacao(agora)` devolve `AGUARDANDO_INICIO`,
  `VIGENTE`, `VENCIDA` ou `CANCELADA` pelas datas e pelo `Clock` (o fim vale até o último segundo).
  Nenhum job marca matrículas como vencidas.
- **Fronteira entre módulos**: a matrícula guarda só os ids do usuário e da turma, sem relacionamento
  JPA. O módulo `curso` exporta o `ConteudoTurmaService` (resumo da turma com o curso, conteúdo ativo,
  aula ativa com o que a política precisa), que o catálogo passa a usar também; o `UsuarioService`
  ganha a busca de vários usuários por id.
- **Rotas do ADM** em `/api/adm`:
  - `GET /turmas/{id}/matriculas` (paginado, inclui canceladas, com nome e e-mail do aluno);
  - `POST /turmas/{id}/matriculas` pelo e-mail de um usuário ativo (início padrão agora); recusa turma
    encerrada, usuário inexistente, matrícula ativa repetida e transação repetida;
  - `PUT /matriculas/{id}/periodo` e `POST /matriculas/{id}/cancelamento` (cancelada não muda mais).
  Turma com matrículas não pode ser excluída: a chave estrangeira gera o 409.
- **`AcessoAulaPolicy`** (classe pura, com `Clock`): aula liberada com matrícula vigente na turma ou se
  a aula é aberta (gratuita, de turma ativa e curso publicado); a turma inteira só com matrícula vigente.
  O `AcessoAulaService.exigirAcesso(usuario, aula)` é a porta de entrada que vídeo, arquivo, fórum,
  progresso e certificado vão usar: aula inexistente ou inativa 404, sem acesso 403.
- **Rotas do aluno** em `/api/aluno`, sempre com o id do token:
  - `GET /cursos?situacao=ATIVOS|ARQUIVADOS|EXPIRADOS` (ativos e arquivados incluem a matrícula que vai
    começar; cancelada não aparece);
  - `GET /turmas/{id}` com os módulos e as aulas ativos;
  - `GET /aulas/{id}`;
  - `PUT` e `DELETE /matriculas/{id}/arquivamento` (matrícula de outro aluno responde 404).

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

`backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operações de usuário usadas pelos outros módulos (eles nunca acessam o repositório direto). */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Cria um usuário com o e-mail já confirmado: contas criadas por quem administra a plataforma
     * (comando {@code criar-admin}, seeds). A senha já deve ter passado pela {@code PoliticaSenha}.
     */
    @Transactional
    public Usuario criarConfirmado(String nome, String email, String senha, Set<Papel> papeis) {
        return criar(nome, email, senha, papeis, true);
    }

    /** Cadastro feito pelo próprio aluno: o e-mail fica pendente até ele confirmar. */
    @Transactional
    public Usuario cadastrarAluno(String nome, String email, String senha) {
        return criar(nome, email, senha, Set.of(Papel.ALUNO), false);
    }

    private Usuario criar(String nome, String email, String senha, Set<Papel> papeis, boolean confirmado) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        return repository.save(new Usuario(
                nome, emailNormalizado, passwordEncoder.encode(senha), papeis, confirmado, clock.instant()));
    }

    @Transactional
    public void confirmarEmail(Usuario usuario) {
        usuario.confirmarEmail(clock.instant());
        repository.save(usuario);
    }

    /** Troca a senha. A nova senha já deve ter passado pela {@code PoliticaSenha}. */
    @Transactional
    public void trocarSenha(Usuario usuario, String novaSenha) {
        usuario.trocarHashSenha(passwordEncoder.encode(novaSenha), clock.instant());
        repository.save(usuario);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(Usuario.normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id);
    }

    /** Usuários indexados pelo id, numa consulta só; ids inexistentes ficam fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Usuario> buscarPorIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
    }

    /** Regrava o hash quando ele usa um algoritmo antigo (ex.: BCrypt vindo do sistema legado). */
    @Transactional
    public void atualizarHashSeNecessario(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.trocarHashSenha(passwordEncoder.encode(senha), clock.instant());
            repository.save(usuario);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/infra/TurmaRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TurmaRepository extends JpaRepository<Turma, Long> {

    List<Turma> findByCursoIdOrderById(Long cursoId);

    boolean existsByCursoId(Long cursoId);

    /** Turma ativa mais recente: é o conteúdo que o catálogo mostra. */
    Optional<Turma> findFirstByCursoIdAndStatusOrderByIdDesc(Long cursoId, StatusTurma status);

    /** Turmas com o curso carregado na mesma consulta. */
    @Query("select t from Turma t join fetch t.curso where t.id in :ids")
    List<Turma> comCurso(Collection<Long> ids);

    /** Trava a turma enquanto a ordem dos módulos muda (criar, reordenar, excluir). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id")
    Optional<Turma> travar(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/infra/AulaRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    List<Aula> findByModuloIdOrderByOrdem(Long moduloId);

    /** Aulas de vários módulos numa consulta só (catálogo). */
    List<Aula> findByModuloIdInAndStatusOrderByOrdem(Collection<Long> moduloIds, StatusConteudo status);

    boolean existsByModuloId(Long moduloId);

    /** Aula com módulo, turma e curso carregados na mesma consulta (política de acesso). */
    @Query("select a from Aula a join fetch a.modulo m join fetch m.turma t join fetch t.curso where a.id = :id")
    Optional<Aula> comHierarquia(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/CatalogoService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo público: só cursos publicados e, no detalhe, só o conteúdo ativo da turma ativa mais
 * recente. Curso em rascunho ou arquivado responde 404, como se não existisse.
 */
@Service
public class CatalogoService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ConteudoTurmaService conteudoTurmaService;

    public CatalogoService(CursoRepository cursos, TurmaRepository turmas, ConteudoTurmaService conteudoTurmaService) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.conteudoTurmaService = conteudoTurmaService;
    }

    @Transactional(readOnly = true)
    public Page<Curso> listar(Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("titulo", "id"));
        return cursos.findByStatus(StatusCurso.PUBLICADO, ordenada);
    }

    @Transactional(readOnly = true)
    public Curso buscar(String slug) {
        return cursos.findBySlugAndStatus(slug, StatusCurso.PUBLICADO)
                .orElseThrow(() -> new NaoEncontradoException("Curso não encontrado."));
    }

    @Transactional(readOnly = true)
    public CursoDetalhe detalhar(String slug) {
        Curso curso = buscar(slug);
        List<ModuloConteudo> conteudo = turmas.findFirstByCursoIdAndStatusOrderByIdDesc(
                        curso.getId(), StatusTurma.ATIVA)
                .map(turma -> conteudoTurmaService.conteudoAtivo(turma.getId()))
                .orElse(List.of());
        return new CursoDetalhe(curso, conteudo);
    }

    public record CursoDetalhe(Curso curso, List<ModuloConteudo> modulos) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/TurmaAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão das turmas de um curso pelo ADM. */
@Service
public class TurmaAdmService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final ModuloRepository modulos;
    private final Clock clock;

    public TurmaAdmService(CursoRepository cursos, TurmaRepository turmas, ModuloRepository modulos, Clock clock) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.modulos = modulos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Turma> listar(Long cursoId) {
        if (!cursos.existsById(cursoId)) {
            throw CursoAdmService.naoEncontrado();
        }
        return turmas.findByCursoIdOrderById(cursoId);
    }

    @Transactional
    public Turma criar(Long cursoId, String nome) {
        var curso = cursos.findById(cursoId).orElseThrow(CursoAdmService::naoEncontrado);
        return turmas.save(new Turma(curso, nome, clock.instant()));
    }

    @Transactional
    public Turma renomear(Long id, String nome) {
        Turma turma = buscar(id);
        turma.renomear(nome, clock.instant());
        return turma;
    }

    @Transactional
    public Turma alterarStatus(Long id, StatusTurma status) {
        Turma turma = buscar(id);
        turma.alterarStatus(status, clock.instant());
        return turma;
    }

    /** Exclui uma turma sem módulos e sem matrículas; as outras são encerradas. */
    @Transactional
    public void excluir(Long id) {
        Turma turma = buscar(id);
        if (modulos.existsByTurmaId(id)) {
            throw new ConflitoException("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        }
        try {
            turmas.delete(turma);
            turmas.flush();
        } catch (DataIntegrityViolationException e) {
            // A chave estrangeira das matrículas impede a exclusão
            throw new ConflitoException("A turma tem matrículas. Encerre a turma.");
        }
    }

    private Turma buscar(Long id) {
        return turmas.findById(id).orElseThrow(TurmaAdmService::naoEncontrada);
    }

    static NaoEncontradoException naoEncontrada() {
        return new NaoEncontradoException("Turma não encontrada.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.CapaCursoService.EnvioCapa;
import br.com.celke.cursos.curso.application.CatalogoService.CursoDetalhe;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Respostas das rotas de cursos: gestão ({@code /api/adm}) e catálogo público ({@code /api/cursos}). */
public final class CursoResponses {

    private CursoResponses() {}

    /** Curso na gestão; {@code capaUrl} é temporária (vale uma hora). */
    public record CursoAdm(
            Long id,
            String titulo,
            String slug,
            String resumo,
            String descricao,
            StatusCurso status,
            String capaUrl,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static CursoAdm de(br.com.celke.cursos.curso.domain.Curso curso, Optional<URL> capa) {
            return new CursoAdm(
                    curso.getId(),
                    curso.getTitulo(),
                    curso.getSlug(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    curso.getStatus(),
                    capa.map(URL::toString).orElse(null),
                    curso.getCriadoEm(),
                    curso.getAtualizadoEm());
        }
    }

    public record Turma(Long id, Long cursoId, String nome, StatusTurma status) {

        public static Turma de(br.com.celke.cursos.curso.domain.Turma turma) {
            return new Turma(turma.getId(), turma.getCurso().getId(), turma.getNome(), turma.getStatus());
        }
    }

    public record Modulo(Long id, Long turmaId, String titulo, int ordem, StatusConteudo status) {

        public static Modulo de(br.com.celke.cursos.curso.domain.Modulo modulo) {
            return new Modulo(
                    modulo.getId(),
                    modulo.getTurma().getId(),
                    modulo.getTitulo(),
                    modulo.getOrdem(),
                    modulo.getStatus());
        }
    }

    public record Aula(
            Long id,
            Long moduloId,
            String titulo,
            String descricao,
            int ordem,
            boolean gratuita,
            StatusConteudo status) {

        public static Aula de(br.com.celke.cursos.curso.domain.Aula aula) {
            return new Aula(
                    aula.getId(),
                    aula.getModulo().getId(),
                    aula.getTitulo(),
                    aula.getDescricao(),
                    aula.getOrdem(),
                    aula.isGratuita(),
                    aula.getStatus());
        }
    }

    /** Onde e como enviar a imagem: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioCapaResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioCapaResponse de(EnvioCapa envio) {
            return new EnvioCapaResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /**
     * Curso no catálogo. {@code capaUrl} é um endereço estável da API (relativo), que redireciona para
     * a imagem: pode ir para HTML em cache ou pré-renderizado sem vencer.
     */
    public record CursoCatalogo(String slug, String titulo, String resumo, String capaUrl) {

        public static CursoCatalogo de(br.com.celke.cursos.curso.domain.Curso curso) {
            return new CursoCatalogo(curso.getSlug(), curso.getTitulo(), curso.getResumo(), enderecoCapa(curso));
        }
    }

    public record CursoCatalogoDetalhe(
            String slug, String titulo, String resumo, String descricao, String capaUrl, List<ModuloCatalogo> modulos) {

        public static CursoCatalogoDetalhe de(CursoDetalhe detalhe) {
            var curso = detalhe.curso();
            return new CursoCatalogoDetalhe(
                    curso.getSlug(),
                    curso.getTitulo(),
                    curso.getResumo(),
                    curso.getDescricao(),
                    enderecoCapa(curso),
                    detalhe.modulos().stream().map(ModuloCatalogo::de).toList());
        }
    }

    public record ModuloCatalogo(String titulo, List<AulaCatalogo> aulas) {

        static ModuloCatalogo de(ModuloConteudo modulo) {
            return new ModuloCatalogo(
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaCatalogo::de).toList());
        }
    }

    public record AulaCatalogo(Long id, String titulo, boolean gratuita) {

        static AulaCatalogo de(AulaConteudo aula) {
            return new AulaCatalogo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    private static String enderecoCapa(br.com.celke.cursos.curso.domain.Curso curso) {
        return curso.getCapaChave() == null ? null : "/api/cursos/%s/capa".formatted(curso.getSlug());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CursoITHelper.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Monta cursos, turmas, módulos e aulas pela API do ADM (testes dos módulos curso e matrícula). */
public final class CursoITHelper {

    private final MockMvcTester mvc;
    private final String bearer;

    public CursoITHelper(MockMvcTester mvc, String bearer) {
        this.mvc = mvc;
        this.bearer = bearer;
    }

    public MvcTestResult enviar(String metodo, String uri, String corpo) {
        var requisicao = mvc.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return requisicao.exchange();
    }

    public long criarCurso(String titulo, String slug) {
        String corpo = slug == null
                ? "{\"titulo\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, titulo)
                : "{\"titulo\":\"%s\",\"slug\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, slug, titulo);
        return idCriado(enviar("POST", "/api/adm/cursos", corpo));
    }

    public void publicar(long cursoId) {
        status("/api/adm/cursos/" + cursoId + "/status", "PUBLICADO");
    }

    public long criarTurma(long cursoId, String nome) {
        return idCriado(enviar("POST", "/api/adm/cursos/" + cursoId + "/turmas", "{\"nome\":\"%s\"}".formatted(nome)));
    }

    public void ativarTurma(long turmaId) {
        status("/api/adm/turmas/" + turmaId + "/status", "ATIVA");
    }

    public long criarModulo(long turmaId, String titulo) {
        return idCriado(
                enviar("POST", "/api/adm/turmas/" + turmaId + "/modulos", "{\"titulo\":\"%s\"}".formatted(titulo)));
    }

    public long criarAula(long moduloId, String titulo, boolean gratuita) {
        return idCriado(enviar(
                "POST",
                "/api/adm/modulos/" + moduloId + "/aulas",
                "{\"titulo\":\"%s\",\"gratuita\":%s}".formatted(titulo, gratuita)));
    }

    /** {@code PATCH <rota>} com o novo status; confere o 200. */
    public void status(String rota, String status) {
        assertThat(enviar("PATCH", rota, "{\"status\":\"%s\"}".formatted(status)))
                .hasStatusOk();
    }

    public static long idCriado(MvcTestResult resposta) {
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(corpo(resposta), "$.id")).longValue();
    }

    public static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/CatalogoServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    private CatalogoService service;
    private final Curso curso = publicado(curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new CatalogoService(cursos, turmas, conteudoTurmaService);
    }

    @Test
    @DisplayName("lista só publicados, em ordem alfabética, ignorando a ordenação do cliente")
    void listar() {
        when(cursos.findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id"))))
                .thenReturn(Page.empty());

        service.listar(PageRequest.of(1, 5, Sort.by("capaChave")));

        verify(cursos).findByStatus(StatusCurso.PUBLICADO, PageRequest.of(1, 5, Sort.by("titulo", "id")));
    }

    @Test
    @DisplayName("curso não publicado responde não encontrado")
    void naoPublicado() {
        when(cursos.findBySlugAndStatus("rascunho", StatusCurso.PUBLICADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar("rascunho"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Curso não encontrado.");
    }

    @Test
    @DisplayName("curso sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.detalhar("java").modulos()).isEmpty();
    }

    @Test
    @DisplayName("mostra o conteúdo ativo da turma ativa mais recente")
    void conteudoDaTurmaAtiva() {
        List<ModuloConteudo> conteudo = List.of(new ModuloConteudo(10L, "Introdução", List.of()));
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.of(turma(5, curso)));
        when(conteudoTurmaService.conteudoAtivo(5L)).thenReturn(conteudo);

        var detalhe = service.detalhar("java");

        assertThat(detalhe.curso()).isSameAs(curso);
        assertThat(detalhe.modulos()).isEqualTo(conteudo);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/TurmaAdmServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TurmaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    private TurmaAdmService service;

    @BeforeEach
    void setUp() {
        service = new TurmaAdmService(cursos, turmas, modulos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("turma nova nasce em preparação, no curso informado")
    void criar() {
        var curso = curso(1, "java");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));
        when(turmas.save(any(Turma.class))).thenAnswer(i -> i.getArgument(0));

        Turma turma = service.criar(1L, " Turma 2026 ");

        assertThat(turma.getNome()).isEqualTo("Turma 2026");
        assertThat(turma.getStatus()).isEqualTo(StatusTurma.PREPARACAO);
        assertThat(turma.getCurso()).isSameAs(curso);
    }

    @Test
    @DisplayName("listar e criar em curso inexistente respondem não encontrado")
    void cursoInexistente() {
        when(cursos.existsById(9L)).thenReturn(false);
        when(cursos.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, "Turma")).isInstanceOf(NaoEncontradoException.class);
        verify(turmas, never()).save(any());
    }

    @Test
    @DisplayName("renomeia e altera o status")
    void renomearEAlterarStatus() {
        Turma turma = turma(5, curso(1, "java"));
        when(turmas.findById(5L)).thenReturn(Optional.of(turma));

        service.renomear(5L, "Nova");
        service.alterarStatus(5L, StatusTurma.ATIVA);

        assertThat(turma.getNome()).isEqualTo("Nova");
        assertThat(turma.getStatus()).isEqualTo(StatusTurma.ATIVA);
    }

    @Test
    @DisplayName("turma sem módulos é excluída")
    void excluiVazia() {
        Turma turma = turma(5, curso(1, "java"));
        when(turmas.findById(5L)).thenReturn(Optional.of(turma));
        when(modulos.existsByTurmaId(5L)).thenReturn(false);

        service.excluir(5L);

        verify(turmas).delete(turma);
    }

    @Test
    @DisplayName("turma com módulos não é excluída")
    void naoExcluiComModulos() {
        when(turmas.findById(5L)).thenReturn(Optional.of(turma(5, curso(1, "java"))));
        when(modulos.existsByTurmaId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        verify(turmas, never()).delete(any());
    }

    @Test
    @DisplayName("turma com matrículas não é excluída (chave estrangeira no banco)")
    void naoExcluiComMatriculas() {
        when(turmas.findById(5L)).thenReturn(Optional.of(turma(5, curso(1, "java"))));
        when(modulos.existsByTurmaId(5L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_matriculas"))
                .when(turmas)
                .flush();

        assertThatThrownBy(() -> service.excluir(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("A turma tem matrículas. Encerre a turma.");
    }

    @Test
    @DisplayName("turma inexistente responde não encontrada")
    void inexistente() {
        when(turmas.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(9L, "Nova"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/curso/CatalogoE2E.java`:

`````java
package br.com.celke.cursos.e2e.curso;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Dados;
import br.com.celke.cursos.e2e.support.E2eConfig;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
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

    private static final String HOST_MIDIA = "media.localhost";

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
        assertThat(url.getHost()).isEqualTo(HOST_MIDIA);
        midia().contentType("image/png")
                .body(png)
                .put(url.getRawPath() + "?" + url.getRawQuery())
                .then()
                .statusCode(200);
        adm.requisicao()
                .body("{\"chave\":\"%s\"}".formatted(envio.jsonPath().getString("chave")))
                .put("/api/adm/cursos/" + curso + "/capa")
                .then()
                .statusCode(200)
                .body("capaUrl", startsWith(E2eConfig.origem(HOST_MIDIA)));

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
        assertThat(capa.getHost()).isEqualTo(HOST_MIDIA);
        Response imagem = midia().get(capa.getRawPath() + "?" + capa.getRawQuery())
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

    /**
     * Requisição ao Garage pelo Caddy com a URL pré-assinada como veio: sem recodificar a query e sem
     * acrescentar charset ao Content-Type, que fazem parte da assinatura.
     */
    private static RequestSpecification midia() {
        return given().config(RestAssuredConfig.config()
                        .encoderConfig(EncoderConfig.encoderConfig()
                                .appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                .urlEncodingEnabled(false)
                .baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", HOST_MIDIA + ":" + E2eConfig.PORTA);
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

### 2. Migration

`backend/src/main/resources/db/migration/V7__criar_matriculas.sql`:

`````sql
-- Matrícula de um usuário numa turma. "Vencida" não é status: sai das datas comparadas com o relógio.
CREATE TABLE matriculas (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    turma_id         BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    inicio           TIMESTAMPTZ  NOT NULL,
    -- Nulo: acesso sem prazo
    fim              TIMESTAMPTZ,
    status           VARCHAR(20)  NOT NULL,
    forma_pagamento  VARCHAR(20)  NOT NULL,
    -- Identificador da transação no meio de pagamento (webhook futuro)
    transacao        VARCHAR(100),
    arquivada_em     TIMESTAMPTZ,
    cancelada_em     TIMESTAMPTZ,
    criado_em        TIMESTAMPTZ  NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_matriculas_periodo CHECK (fim IS NULL OR fim > inicio),
    CONSTRAINT ck_matriculas_status CHECK (status IN ('ATIVA', 'CANCELADA')),
    CONSTRAINT ck_matriculas_forma_pagamento
        CHECK (forma_pagamento IN ('MANUAL', 'CORTESIA', 'PIX', 'CARTAO', 'BOLETO'))
);

-- Uma matrícula ativa por aluno e turma; canceladas ficam no histórico
CREATE UNIQUE INDEX uq_matriculas_ativa ON matriculas (usuario_id, turma_id) WHERE status = 'ATIVA';
-- A mesma transação não gera duas matrículas
CREATE UNIQUE INDEX uq_matriculas_transacao ON matriculas (forma_pagamento, transacao) WHERE transacao IS NOT NULL;
CREATE INDEX ix_matriculas_turma ON matriculas (turma_id);
`````

### 3. Conteúdo exportado pelo módulo curso

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

### 4. Erro de acesso

`backend/src/main/java/br/com/celke/cursos/shared/erro/AcessoNegadoException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import org.springframework.http.HttpStatus;

/** Usuário autenticado sem direito ao recurso pela regra de negócio (ex.: sem matrícula vigente). */
public class AcessoNegadoException extends RegraNegocioException {

    public AcessoNegadoException(String mensagem) {
        super(HttpStatus.FORBIDDEN, mensagem);
    }
}
`````

### 5. Domínio da matrícula

`backend/src/main/java/br/com/celke/cursos/matricula/domain/StatusMatricula.java`:

`````java
package br.com.celke.cursos.matricula.domain;

/** Status gravado. Se a matrícula está vencida ou ainda não começou, quem diz é {@link SituacaoMatricula}. */
public enum StatusMatricula {
    ATIVA,
    CANCELADA
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/SituacaoMatricula.java`:

`````java
package br.com.celke.cursos.matricula.domain;

/** Situação num instante, calculada pelo status e pelas datas. Só {@code VIGENTE} dá acesso ao conteúdo. */
public enum SituacaoMatricula {
    AGUARDANDO_INICIO,
    VIGENTE,
    VENCIDA,
    CANCELADA
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/FormaPagamento.java`:

`````java
package br.com.celke.cursos.matricula.domain;

/** Como a matrícula foi paga. {@code MANUAL} e {@code CORTESIA} são lançadas pelo ADM. */
public enum FormaPagamento {
    MANUAL,
    CORTESIA,
    PIX,
    CARTAO,
    BOLETO
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/Matricula.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Matrícula de um usuário numa turma. Guarda só os ids do usuário e da turma: os dados deles vêm dos
 * serviços dos módulos {@code usuario} e {@code curso}.
 */
@Entity
@Table(name = "matriculas")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "turma_id", nullable = false)
    private Long turmaId;

    @Column(nullable = false)
    private Instant inicio;

    /** Nulo: acesso sem prazo. */
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusMatricula status;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Column(length = 100)
    private String transacao;

    @Column(name = "arquivada_em")
    private Instant arquivadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Matricula() {}

    public Matricula(
            Long usuarioId,
            Long turmaId,
            Instant inicio,
            Instant fim,
            FormaPagamento formaPagamento,
            String transacao,
            Instant agora) {
        validarPeriodo(inicio, fim);
        this.usuarioId = usuarioId;
        this.turmaId = turmaId;
        this.inicio = inicio;
        this.fim = fim;
        this.status = StatusMatricula.ATIVA;
        this.formaPagamento = formaPagamento;
        this.transacao = transacao == null || transacao.isBlank() ? null : transacao.strip();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Situação no instante informado: o fim é inclusivo (vale até o último segundo). */
    public SituacaoMatricula situacao(Instant agora) {
        if (status == StatusMatricula.CANCELADA) {
            return SituacaoMatricula.CANCELADA;
        }
        if (agora.isBefore(inicio)) {
            return SituacaoMatricula.AGUARDANDO_INICIO;
        }
        if (fim != null && agora.isAfter(fim)) {
            return SituacaoMatricula.VENCIDA;
        }
        return SituacaoMatricula.VIGENTE;
    }

    public boolean isVigente(Instant agora) {
        return situacao(agora) == SituacaoMatricula.VIGENTE;
    }

    public void alterarPeriodo(Instant inicio, Instant fim, Instant agora) {
        exigirAtiva();
        validarPeriodo(inicio, fim);
        this.inicio = inicio;
        this.fim = fim;
        this.atualizadoEm = agora;
    }

    public void cancelar(Instant agora) {
        exigirAtiva();
        this.status = StatusMatricula.CANCELADA;
        this.canceladaEm = agora;
        this.atualizadoEm = agora;
    }

    /** O aluno tira o curso da lista principal; o acesso continua o mesmo. */
    public void arquivar(Instant agora) {
        if (arquivadaEm == null) {
            this.arquivadaEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public void desarquivar(Instant agora) {
        if (arquivadaEm != null) {
            this.arquivadaEm = null;
            this.atualizadoEm = agora;
        }
    }

    public boolean isArquivada() {
        return arquivadaEm != null;
    }

    private void exigirAtiva() {
        if (status == StatusMatricula.CANCELADA) {
            throw new ConflitoException("A matrícula está cancelada.");
        }
    }

    private static void validarPeriodo(Instant inicio, Instant fim) {
        if (fim != null && !fim.isAfter(inicio)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "O fim da matrícula deve ser depois do início.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public StatusMatricula getStatus() {
        return status;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getTransacao() {
        return transacao;
    }

    public Instant getArquivadaEm() {
        return arquivadaEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/FiltroMeusCursos.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import java.time.Instant;

/**
 * Abas de "meus cursos". Ativos e arquivados incluem a matrícula que ainda vai começar; expirados são
 * as vencidas. Cancelada não aparece em nenhuma.
 */
public enum FiltroMeusCursos {
    ATIVOS,
    ARQUIVADOS,
    EXPIRADOS;

    public boolean aceita(Matricula matricula, Instant agora) {
        SituacaoMatricula situacao = matricula.situacao(agora);
        boolean emAndamento = situacao == SituacaoMatricula.VIGENTE || situacao == SituacaoMatricula.AGUARDANDO_INICIO;
        return switch (this) {
            case ATIVOS -> emAndamento && !matricula.isArquivada();
            case ARQUIVADOS -> emAndamento && matricula.isArquivada();
            case EXPIRADOS -> situacao == SituacaoMatricula.VENCIDA;
        };
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/domain/AcessoAulaPolicy.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Política única de acesso ao conteúdo do curso (aula, vídeo, arquivo, fórum, progresso,
 * certificado): matrícula vigente na turma da aula, ou aula aberta (gratuita, de turma ativa e curso
 * publicado). Ninguém decide acesso ao conteúdo fora daqui.
 */
@Component
public class AcessoAulaPolicy {

    private final Clock clock;

    public AcessoAulaPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param matricula matrícula do aluno na turma da aula, ou {@code null} se ele não tem
     * @param aulaAberta aula gratuita de turma ativa e curso publicado
     */
    public boolean podeAcessarAula(Matricula matricula, boolean aulaAberta) {
        return aulaAberta || podeAcessarTurma(matricula);
    }

    /** Conteúdo completo da turma (lista de módulos e aulas, progresso, certificado): só com matrícula vigente. */
    public boolean podeAcessarTurma(Matricula matricula) {
        return matricula != null && matricula.isVigente(clock.instant());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/infra/MatriculaRepository.java`:

`````java
package br.com.celke.cursos.matricula.infra;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /** A matrícula ativa (vigente, futura ou vencida) do aluno na turma; o banco garante no máximo uma. */
    Optional<Matricula> findByUsuarioIdAndTurmaIdAndStatus(Long usuarioId, Long turmaId, StatusMatricula status);

    boolean existsByUsuarioIdAndTurmaIdAndStatus(Long usuarioId, Long turmaId, StatusMatricula status);

    List<Matricula> findByUsuarioIdAndStatus(Long usuarioId, StatusMatricula status);

    /** Matrícula do próprio aluno: a de outro aluno não é encontrada (404, sem revelar que existe). */
    Optional<Matricula> findByIdAndUsuarioId(Long id, Long usuarioId);

    Page<Matricula> findByTurmaId(Long turmaId, Pageable pageable);

    boolean existsByFormaPagamentoAndTransacao(FormaPagamento formaPagamento, String transacao);
}
`````

### 6. Casos de uso

`backend/src/main/java/br/com/celke/cursos/matricula/application/NovaMatricula.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import java.time.Instant;

/** Dados da matrícula lançada pelo ADM; sem início, começa agora; sem fim, não vence. */
public record NovaMatricula(
        String email, Instant inicio, Instant fim, FormaPagamento formaPagamento, String transacao) {}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/application/MatriculaAdmService.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Matrícula manual pelo ADM: matricular, alterar o período e cancelar. */
@Service
public class MatriculaAdmService {

    private final MatriculaRepository matriculas;
    private final UsuarioService usuarioService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final Clock clock;

    public MatriculaAdmService(
            MatriculaRepository matriculas,
            UsuarioService usuarioService,
            ConteudoTurmaService conteudoTurmaService,
            Clock clock) {
        this.matriculas = matriculas;
        this.usuarioService = usuarioService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.clock = clock;
    }

    /** Matrículas da turma (inclusive canceladas), das mais recentes para as mais antigas. */
    @Transactional(readOnly = true)
    public Page<MatriculaComAluno> listar(Long turmaId, Pageable pagina) {
        buscarTurma(turmaId);
        Page<Matricula> page = matriculas.findByTurmaId(
                turmaId,
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Usuario> usuarios = usuarioService.buscarPorIds(
                page.getContent().stream().map(Matricula::getUsuarioId).toList());
        return page.map(m -> new MatriculaComAluno(m, usuarios.get(m.getUsuarioId())));
    }

    @Transactional
    public MatriculaComAluno matricular(Long turmaId, NovaMatricula dados) {
        TurmaResumo turma = buscarTurma(turmaId);
        if (turma.turmaStatus() == StatusTurma.ENCERRADA) {
            throw new ConflitoException("A turma está encerrada e não recebe novas matrículas.");
        }
        Usuario usuario = usuarioService
                .buscarPorEmail(dados.email())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        if (matriculas.existsByUsuarioIdAndTurmaIdAndStatus(usuario.getId(), turmaId, StatusMatricula.ATIVA)) {
            throw jaMatriculado();
        }
        if (dados.transacao() != null
                && !dados.transacao().isBlank()
                && matriculas.existsByFormaPagamentoAndTransacao(
                        dados.formaPagamento(), dados.transacao().strip())) {
            throw new ConflitoException("Esta transação já gerou uma matrícula.");
        }
        Instant agora = clock.instant();
        Matricula matricula = new Matricula(
                usuario.getId(),
                turmaId,
                dados.inicio() == null ? agora : dados.inicio(),
                dados.fim(),
                dados.formaPagamento(),
                dados.transacao(),
                agora);
        try {
            // O índice único do banco decide quando duas matrículas iguais chegam ao mesmo tempo
            return new MatriculaComAluno(matriculas.saveAndFlush(matricula), usuario);
        } catch (DataIntegrityViolationException e) {
            throw jaMatriculado();
        }
    }

    @Transactional
    public MatriculaComAluno alterarPeriodo(Long id, Instant inicio, Instant fim) {
        Matricula matricula = buscar(id);
        matricula.alterarPeriodo(inicio, fim, clock.instant());
        return comAluno(matricula);
    }

    @Transactional
    public MatriculaComAluno cancelar(Long id) {
        Matricula matricula = buscar(id);
        matricula.cancelar(clock.instant());
        return comAluno(matricula);
    }

    private MatriculaComAluno comAluno(Matricula matricula) {
        return new MatriculaComAluno(
                matricula, usuarioService.buscarPorId(matricula.getUsuarioId()).orElse(null));
    }

    private Matricula buscar(Long id) {
        return matriculas.findById(id).orElseThrow(() -> new NaoEncontradoException("Matrícula não encontrada."));
    }

    private TurmaResumo buscarTurma(Long turmaId) {
        return conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
    }

    private static ConflitoException jaMatriculado() {
        return new ConflitoException("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");
    }

    public record MatriculaComAluno(Matricula matricula, Usuario usuario) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/application/AlunoCursosService.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Área do aluno: os cursos dele e o conteúdo das turmas em que tem matrícula vigente. */
@Service
public class AlunoCursosService {

    private final MatriculaRepository matriculas;
    private final ConteudoTurmaService conteudoTurmaService;
    private final AcessoAulaPolicy policy;
    private final Clock clock;

    public AlunoCursosService(
            MatriculaRepository matriculas,
            ConteudoTurmaService conteudoTurmaService,
            AcessoAulaPolicy policy,
            Clock clock) {
        this.matriculas = matriculas;
        this.conteudoTurmaService = conteudoTurmaService;
        this.policy = policy;
        this.clock = clock;
    }

    /** Cursos do aluno na aba pedida, em ordem alfabética. */
    @Transactional(readOnly = true)
    public List<MeuCurso> meusCursos(Long usuarioId, FiltroMeusCursos filtro) {
        Instant agora = clock.instant();
        List<Matricula> doFiltro = matriculas.findByUsuarioIdAndStatus(usuarioId, StatusMatricula.ATIVA).stream()
                .filter(m -> filtro.aceita(m, agora))
                .toList();
        Map<Long, TurmaResumo> turmas = conteudoTurmaService.turmas(
                doFiltro.stream().map(Matricula::getTurmaId).toList());
        return doFiltro.stream()
                .map(m -> new MeuCurso(m, turmas.get(m.getTurmaId()), m.situacao(agora)))
                .sorted(Comparator.comparing((MeuCurso c) -> c.turma().cursoTitulo())
                        .thenComparing(c -> c.matricula().getId()))
                .toList();
    }

    /** Turma com os módulos e as aulas ativos; exige matrícula vigente (403 sem ela). */
    @Transactional(readOnly = true)
    public TurmaDoAluno turma(Long usuarioId, Long turmaId) {
        TurmaResumo turma = conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
        Matricula matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, turmaId, StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarTurma(matricula)) {
            throw new AcessoNegadoException("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        }
        return new TurmaDoAluno(matricula, turma, conteudoTurmaService.conteudoAtivo(turmaId));
    }

    @Transactional
    public void arquivar(Long usuarioId, Long matriculaId) {
        doAluno(usuarioId, matriculaId).arquivar(clock.instant());
    }

    @Transactional
    public void desarquivar(Long usuarioId, Long matriculaId) {
        doAluno(usuarioId, matriculaId).desarquivar(clock.instant());
    }

    /** Matrícula do próprio aluno; a de outro aluno ou cancelada responde 404, sem revelar que existe. */
    private Matricula doAluno(Long usuarioId, Long matriculaId) {
        return matriculas
                .findByIdAndUsuarioId(matriculaId, usuarioId)
                .filter(m -> m.getStatus() == StatusMatricula.ATIVA)
                .orElseThrow(() -> new NaoEncontradoException("Matrícula não encontrada."));
    }

    public record MeuCurso(Matricula matricula, TurmaResumo turma, SituacaoMatricula situacao) {}

    public record TurmaDoAluno(Matricula matricula, TurmaResumo turma, List<ModuloConteudo> modulos) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/application/AcessoAulaService.java`:

`````java
package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada da {@link AcessoAulaPolicy} para os outros módulos: toda rota de aula, vídeo,
 * arquivo, fórum ou progresso chama {@link #exigirAcesso} antes de responder.
 */
@Service
public class AcessoAulaService {

    private final ConteudoTurmaService conteudoTurmaService;
    private final MatriculaRepository matriculas;
    private final AcessoAulaPolicy policy;

    public AcessoAulaService(
            ConteudoTurmaService conteudoTurmaService, MatriculaRepository matriculas, AcessoAulaPolicy policy) {
        this.conteudoTurmaService = conteudoTurmaService;
        this.matriculas = matriculas;
        this.policy = policy;
    }

    /**
     * Devolve a aula se o usuário pode acessá-la. Aula inexistente ou inativa responde 404; sem
     * matrícula vigente (e sem ser aula aberta), 403.
     */
    @Transactional(readOnly = true)
    public AulaContexto exigirAcesso(Long usuarioId, Long aulaId) {
        AulaContexto aula = conteudoTurmaService
                .aulaAtiva(aulaId)
                .orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
        var matricula = matriculas
                .findByUsuarioIdAndTurmaIdAndStatus(usuarioId, aula.turmaId(), StatusMatricula.ATIVA)
                .orElse(null);
        if (!policy.podeAcessarAula(matricula, aula.aberta())) {
            throw new AcessoNegadoException("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        }
        return aula;
    }
}
`````

### 7. API

`backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaRequests.java`:

`````java
package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.application.NovaMatricula;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Corpos das rotas de matrícula do ADM. Datas em ISO-8601 com fuso (ex.: {@code 2026-03-01T03:00:00Z}). */
public final class MatriculaRequests {

    private MatriculaRequests() {}

    /** Sem início, começa agora; sem fim, não vence. */
    public record Matricular(
            @NotBlank @Email @Size(max = 254) String email,
            Instant inicio,
            Instant fim,
            @NotNull FormaPagamento formaPagamento,
            @Size(max = 100) String transacao) {

        public NovaMatricula dados() {
            return new NovaMatricula(email, inicio, fim, formaPagamento, transacao);
        }
    }

    /** Sem fim, a matrícula passa a não vencer. */
    public record Periodo(@NotNull Instant inicio, Instant fim) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaResponses.java`:

`````java
package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.matricula.application.AlunoCursosService.MeuCurso;
import br.com.celke.cursos.matricula.application.AlunoCursosService.TurmaDoAluno;
import br.com.celke.cursos.matricula.application.MatriculaAdmService.MatriculaComAluno;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import java.time.Instant;
import java.util.List;

/** Respostas das rotas de matrícula: ADM ({@code /api/adm}) e área do aluno ({@code /api/aluno}). */
public final class MatriculaResponses {

    private MatriculaResponses() {}

    public record MatriculaAdm(
            Long id,
            Long turmaId,
            Long usuarioId,
            String nome,
            String email,
            Instant inicio,
            Instant fim,
            StatusMatricula status,
            SituacaoMatricula situacao,
            FormaPagamento formaPagamento,
            String transacao) {

        public static MatriculaAdm de(MatriculaComAluno item, Instant agora) {
            Matricula m = item.matricula();
            return new MatriculaAdm(
                    m.getId(),
                    m.getTurmaId(),
                    m.getUsuarioId(),
                    item.usuario() == null ? null : item.usuario().getNome(),
                    item.usuario() == null ? null : item.usuario().getEmail(),
                    m.getInicio(),
                    m.getFim(),
                    m.getStatus(),
                    m.situacao(agora),
                    m.getFormaPagamento(),
                    m.getTransacao());
        }
    }

    /** Curso na lista do aluno; {@code capaUrl} é temporária (vale uma hora). */
    public record MeuCursoResponse(
            Long matriculaId,
            Long turmaId,
            String cursoTitulo,
            String cursoSlug,
            String turmaNome,
            String capaUrl,
            Instant inicio,
            Instant fim,
            SituacaoMatricula situacao,
            boolean arquivada) {

        public static MeuCursoResponse de(MeuCurso curso) {
            Matricula m = curso.matricula();
            return new MeuCursoResponse(
                    m.getId(),
                    m.getTurmaId(),
                    curso.turma().cursoTitulo(),
                    curso.turma().cursoSlug(),
                    curso.turma().turmaNome(),
                    curso.turma().capaUrl(),
                    m.getInicio(),
                    m.getFim(),
                    curso.situacao(),
                    m.isArquivada());
        }
    }

    public record TurmaAlunoResponse(
            Long matriculaId,
            Long turmaId,
            String turmaNome,
            String cursoTitulo,
            String cursoSlug,
            Instant inicio,
            Instant fim,
            List<ModuloAluno> modulos) {

        public static TurmaAlunoResponse de(TurmaDoAluno turma) {
            Matricula m = turma.matricula();
            return new TurmaAlunoResponse(
                    m.getId(),
                    m.getTurmaId(),
                    turma.turma().turmaNome(),
                    turma.turma().cursoTitulo(),
                    turma.turma().cursoSlug(),
                    m.getInicio(),
                    m.getFim(),
                    turma.modulos().stream().map(ModuloAluno::de).toList());
        }
    }

    public record ModuloAluno(Long id, String titulo, List<AulaResumo> aulas) {

        static ModuloAluno de(ModuloConteudo modulo) {
            return new ModuloAluno(
                    modulo.id(),
                    modulo.titulo(),
                    modulo.aulas().stream().map(AulaResumo::de).toList());
        }
    }

    public record AulaResumo(Long id, String titulo, boolean gratuita) {

        static AulaResumo de(AulaConteudo aula) {
            return new AulaResumo(aula.id(), aula.titulo(), aula.gratuita());
        }
    }

    public record AulaAlunoResponse(Long id, Long turmaId, String titulo, String descricao, boolean gratuita) {

        public static AulaAlunoResponse de(AulaContexto aula) {
            return new AulaAlunoResponse(aula.id(), aula.turmaId(), aula.titulo(), aula.descricao(), aula.gratuita());
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/api/MatriculaAdmController.java`:

`````java
package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.api.MatriculaResponses.MatriculaAdm;
import br.com.celke.cursos.matricula.application.MatriculaAdmService;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Matrículas")
public class MatriculaAdmController {

    private final MatriculaAdmService matriculaAdmService;
    private final Clock clock;

    public MatriculaAdmController(MatriculaAdmService matriculaAdmService, Clock clock) {
        this.matriculaAdmService = matriculaAdmService;
        this.clock = clock;
    }

    @GetMapping("/turmas/{turmaId}/matriculas")
    @Operation(summary = "Listar as matrículas da turma", description = "Inclui as canceladas; mais recentes primeiro.")
    public Pagina<MatriculaAdm> listar(@PathVariable Long turmaId, @ParameterObject Pageable pagina) {
        return Pagina.de(
                matriculaAdmService.listar(turmaId, pagina).map(item -> MatriculaAdm.de(item, clock.instant())));
    }

    @PostMapping("/turmas/{turmaId}/matriculas")
    @Operation(
            summary = "Matricular",
            description = "Pelo e-mail de um usuário existente; sem início, começa agora; sem fim, não vence.")
    public ResponseEntity<MatriculaAdm> matricular(
            @PathVariable Long turmaId, @Valid @RequestBody MatriculaRequests.Matricular request) {
        MatriculaAdm matricula =
                MatriculaAdm.de(matriculaAdmService.matricular(turmaId, request.dados()), clock.instant());
        return ResponseEntity.created(URI.create("/api/adm/matriculas/" + matricula.id()))
                .body(matricula);
    }

    @PutMapping("/matriculas/{id}/periodo")
    @Operation(summary = "Alterar o período da matrícula")
    public MatriculaAdm alterarPeriodo(@PathVariable Long id, @Valid @RequestBody MatriculaRequests.Periodo request) {
        return MatriculaAdm.de(
                matriculaAdmService.alterarPeriodo(id, request.inicio(), request.fim()), clock.instant());
    }

    @PostMapping("/matriculas/{id}/cancelamento")
    @Operation(
            summary = "Cancelar a matrícula",
            description = "O acesso termina na hora; o registro fica no histórico.")
    public MatriculaAdm cancelar(@PathVariable Long id) {
        return MatriculaAdm.de(matriculaAdmService.cancelar(id), clock.instant());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/api/AlunoController.java`:

`````java
package br.com.celke.cursos.matricula.api;

import br.com.celke.cursos.matricula.api.MatriculaResponses.AulaAlunoResponse;
import br.com.celke.cursos.matricula.api.MatriculaResponses.MeuCursoResponse;
import br.com.celke.cursos.matricula.api.MatriculaResponses.TurmaAlunoResponse;
import br.com.celke.cursos.matricula.application.AcessoAulaService;
import br.com.celke.cursos.matricula.application.AlunoCursosService;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Área do aluno: tudo no escopo do usuário do token (o id nunca vem do cliente). */
@RestController
@RequestMapping("/api/aluno")
@Tag(name = "Área do aluno")
public class AlunoController {

    private final AlunoCursosService alunoCursosService;
    private final AcessoAulaService acessoAulaService;

    public AlunoController(AlunoCursosService alunoCursosService, AcessoAulaService acessoAulaService) {
        this.alunoCursosService = alunoCursosService;
        this.acessoAulaService = acessoAulaService;
    }

    @GetMapping("/cursos")
    @Operation(summary = "Meus cursos", description = "Aba ATIVOS (padrão), ARQUIVADOS ou EXPIRADOS.")
    public List<MeuCursoResponse> meusCursos(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "ATIVOS") FiltroMeusCursos situacao) {
        return alunoCursosService.meusCursos(usuario(jwt), situacao).stream()
                .map(MeuCursoResponse::de)
                .toList();
    }

    @GetMapping("/turmas/{turmaId}")
    @Operation(summary = "Turma com módulos e aulas", description = "Exige matrícula vigente.")
    public TurmaAlunoResponse turma(@AuthenticationPrincipal Jwt jwt, @PathVariable Long turmaId) {
        return TurmaAlunoResponse.de(alunoCursosService.turma(usuario(jwt), turmaId));
    }

    @GetMapping("/aulas/{aulaId}")
    @Operation(summary = "Aula", description = "Exige matrícula vigente, exceto aula gratuita de curso publicado.")
    public AulaAlunoResponse aula(@AuthenticationPrincipal Jwt jwt, @PathVariable Long aulaId) {
        return AulaAlunoResponse.de(acessoAulaService.exigirAcesso(usuario(jwt), aulaId));
    }

    @PutMapping("/matriculas/{id}/arquivamento")
    @Operation(summary = "Arquivar curso", description = "Tira o curso da aba de ativos; o acesso não muda.")
    public ResponseEntity<Void> arquivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        alunoCursosService.arquivar(usuario(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/matriculas/{id}/arquivamento")
    @Operation(summary = "Desarquivar curso")
    public ResponseEntity<Void> desarquivar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        alunoCursosService.desarquivar(usuario(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
`````

### 8. Fábrica de teste

`backend/src/test/java/br/com/celke/cursos/support/fixtures/MatriculaFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de matrículas para testes unitários (sem banco: o id é preenchido aqui). */
public final class MatriculaFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private MatriculaFixture() {}

    /** Matrícula ativa do usuário na turma, no período informado ({@code fim} nulo: sem prazo). */
    public static Matricula matricula(long id, long usuarioId, long turmaId, Instant inicio, Instant fim) {
        Matricula matricula = new Matricula(usuarioId, turmaId, inicio, fim, FormaPagamento.MANUAL, null, CRIADO_EM);
        ReflectionTestUtils.setField(matricula, "id", id);
        return matricula;
    }

    public static Matricula cancelada(Matricula matricula) {
        matricula.cancelar(CRIADO_EM);
        return matricula;
    }
}
`````

### 9. Testes unitários

`backend/src/test/java/br/com/celke/cursos/curso/application/ConteudoTurmaServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConteudoTurmaServiceTest {

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    @Mock
    private CapaCursoService capaCursoService;

    private ConteudoTurmaService service;
    private final Turma turma = turma(5, publicado(curso(1, "java")));

    @BeforeEach
    void setUp() {
        service = new ConteudoTurmaService(turmas, modulos, aulas, capaCursoService);
    }

    @Test
    @DisplayName("resumo da turma traz os dados do curso e a URL da capa")
    void resumo() throws Exception {
        Turma comCapa = turma(6, comCapa(curso(2, "react"), "capas/2/a.jpg"));
        when(turmas.comCurso(List.of(6L))).thenReturn(List.of(comCapa));
        when(capaCursoService.url(comCapa.getCurso()))
                .thenReturn(Optional.of(URI.create("http://s3/capa").toURL()));

        var resumo = service.turma(6L).orElseThrow();

        assertThat(resumo.cursoSlug()).isEqualTo("react");
        assertThat(resumo.turmaStatus()).isEqualTo(StatusTurma.PREPARACAO);
        assertThat(resumo.cursoStatus()).isEqualTo(StatusCurso.RASCUNHO);
        assertThat(resumo.capaUrl()).isEqualTo("http://s3/capa");
    }

    @Test
    @DisplayName("turma inexistente fica fora do resultado")
    void turmaInexistente() {
        when(turmas.comCurso(List.of(9L))).thenReturn(List.of());

        assertThat(service.turma(9L)).isEmpty();
    }

    @Test
    @DisplayName("agrupa as aulas ativas por módulo, na ordem; módulo sem aulas vem com lista vazia")
    void agrupaAulas() {
        var primeiro = modulo(10, turma, 1);
        var segundo = modulo(20, turma, 2);
        when(modulos.findByTurmaIdAndStatusOrderByOrdem(5L, StatusConteudo.ATIVO))
                .thenReturn(List.of(primeiro, segundo));
        when(aulas.findByModuloIdInAndStatusOrderByOrdem(List.of(10L, 20L), StatusConteudo.ATIVO))
                .thenReturn(List.of(aula(100, primeiro, 1), aula(101, primeiro, 2)));

        var conteudo = service.conteudoAtivo(5L);

        assertThat(conteudo).hasSize(2);
        assertThat(conteudo.get(0).aulas()).extracting(AulaConteudo::id).containsExactly(100L, 101L);
        assertThat(conteudo.get(1).aulas()).isEmpty();
    }

    @ParameterizedTest(name = "aula {0}, módulo {1} → encontrada: {2}")
    @CsvSource({"ATIVO, ATIVO, true", "INATIVO, ATIVO, false", "ATIVO, INATIVO, false"})
    void aulaAtivaSoComAulaEModuloAtivos(StatusConteudo statusAula, StatusConteudo statusModulo, boolean encontrada) {
        var modulo = modulo(10, turma, 1);
        modulo.alterarStatus(statusModulo, turma.getCurso().getCriadoEm());
        Aula aula = aula(100, modulo, 1);
        aula.alterarStatus(statusAula, turma.getCurso().getCriadoEm());
        when(aulas.comHierarquia(100L)).thenReturn(Optional.of(aula));

        assertThat(service.aulaAtiva(100L).isPresent()).isEqualTo(encontrada);
    }

    @ParameterizedTest(name = "gratuita {0}, turma {1}, curso {2} → aberta: {3}")
    @CsvSource({
        "true,  ATIVA,      PUBLICADO, true",
        "false, ATIVA,      PUBLICADO, false",
        "true,  PREPARACAO, PUBLICADO, false",
        "true,  ENCERRADA,  PUBLICADO, false",
        "true,  ATIVA,      RASCUNHO,  false",
        "true,  ATIVA,      ARQUIVADO, false",
    })
    void aulaAberta(boolean gratuita, StatusTurma turma, StatusCurso curso, boolean aberta) {
        assertThat(new AulaContexto(1L, "Aula", null, gratuita, 5L, turma, curso).aberta())
                .isEqualTo(aberta);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/domain/MatriculaTest.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MatriculaTest {

    private static final Instant INICIO = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FIM = Instant.parse("2026-06-01T10:00:00Z");

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes do início,   2026-03-01T09:59:59Z, AGUARDANDO_INICIO",
        "exatamente no início,         2026-03-01T10:00:00Z, VIGENTE",
        "no meio do período,           2026-04-15T00:00:00Z, VIGENTE",
        "exatamente no fim,            2026-06-01T10:00:00Z, VIGENTE",
        "um segundo depois do fim,     2026-06-01T10:00:01Z, VENCIDA",
    })
    void situacaoPeloPeriodo(String caso, Instant agora, SituacaoMatricula esperada) {
        assertThat(matricula(1, 2, 3, INICIO, FIM).situacao(agora)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("sem fim, não vence")
    void semFim() {
        assertThat(matricula(1, 2, 3, INICIO, null).situacao(Instant.parse("2099-01-01T00:00:00Z")))
                .isEqualTo(SituacaoMatricula.VIGENTE);
    }

    @Test
    @DisplayName("cancelada é cancelada em qualquer instante, mesmo dentro do período")
    void canceladaEmQualquerInstante() {
        Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
        assertThat(matricula.situacao(Instant.parse("2026-04-15T00:00:00Z"))).isEqualTo(SituacaoMatricula.CANCELADA);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(matricula.getCanceladaEm()).isNotNull();
    }

    @ParameterizedTest(name = "fim {0}")
    @CsvSource({"2026-03-01T10:00:00Z", "2026-02-28T10:00:00Z"})
    void fimPrecisaSerDepoisDoInicio(Instant fim) {
        assertThatThrownBy(() -> matricula(1, 2, 3, INICIO, fim))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O fim da matrícula deve ser depois do início.");
    }

    @Test
    @DisplayName("um segundo depois do início já é um fim válido")
    void fimUmSegundoDepois() {
        assertThat(matricula(1, 2, 3, INICIO, INICIO.plusSeconds(1)).getFim()).isEqualTo(INICIO.plusSeconds(1));
    }

    @Test
    @DisplayName("transação em branco vira nula; preenchida é aparada")
    void transacao() {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, "  ", agora).getTransacao())
                .isNull();
        assertThat(new Matricula(1L, 2L, INICIO, null, FormaPagamento.PIX, " tx-1 ", agora).getTransacao())
                .isEqualTo("tx-1");
    }

    @Nested
    class AlterarPeriodo {

        @Test
        @DisplayName("altera o início e o fim")
        void altera() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            Instant novoFim = FIM.plusSeconds(86_400);
            matricula.alterarPeriodo(INICIO, novoFim, INICIO);
            assertThat(matricula.getFim()).isEqualTo(novoFim);
        }

        @Test
        @DisplayName("recusa período invertido sem mudar nada")
        void recusaInvertido() {
            Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
            assertThatThrownBy(() -> matricula.alterarPeriodo(FIM, INICIO, INICIO))
                    .isInstanceOf(RegraNegocioException.class);
            assertThat(matricula.getInicio()).isEqualTo(INICIO);
        }

        @Test
        @DisplayName("matrícula cancelada não muda de período nem é cancelada de novo")
        void naoAlteraCancelada() {
            Matricula matricula = cancelada(matricula(1, 2, 3, INICIO, FIM));
            assertThatThrownBy(() -> matricula.alterarPeriodo(INICIO, null, INICIO))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A matrícula está cancelada.");
            assertThatThrownBy(() -> matricula.cancelar(INICIO)).isInstanceOf(ConflitoException.class);
        }
    }

    @Test
    @DisplayName("arquivar e desarquivar não mudam a situação e são idempotentes")
    void arquivar() {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        Instant agora = Instant.parse("2026-04-15T00:00:00Z");
        matricula.arquivar(agora);
        matricula.arquivar(agora.plusSeconds(60));
        assertThat(matricula.getArquivadaEm()).isEqualTo(agora);
        assertThat(matricula.isVigente(agora)).isTrue();
        matricula.desarquivar(agora);
        matricula.desarquivar(agora);
        assertThat(matricula.isArquivada()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(FiltroMeusCursos.class)
    void canceladaNaoApareceEmNenhumaAba(FiltroMeusCursos filtro) {
        assertThat(filtro.aceita(cancelada(matricula(1, 2, 3, INICIO, FIM)), Instant.parse("2026-04-15T00:00:00Z")))
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "vigente,                       2026-04-15T00:00:00Z, false, ATIVOS",
        "aguardando início,             2026-02-01T00:00:00Z, false, ATIVOS",
        "vigente arquivada,             2026-04-15T00:00:00Z, true,  ARQUIVADOS",
        "aguardando início arquivada,   2026-02-01T00:00:00Z, true,  ARQUIVADOS",
        "vencida,                       2026-07-01T00:00:00Z, false, EXPIRADOS",
        "vencida arquivada,             2026-07-01T00:00:00Z, true,  EXPIRADOS",
    })
    void abaDeMeusCursos(String caso, Instant agora, boolean arquivada, FiltroMeusCursos esperado) {
        Matricula matricula = matricula(1, 2, 3, INICIO, FIM);
        if (arquivada) {
            matricula.arquivar(INICIO);
        }
        for (FiltroMeusCursos filtro : FiltroMeusCursos.values()) {
            assertThat(filtro.aceita(matricula, agora)).as(filtro.name()).isEqualTo(filtro == esperado);
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/domain/AcessoAulaPolicyTest.java`:

`````java
package br.com.celke.cursos.matricula.domain;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AcessoAulaPolicyTest {

    private static final Instant INICIO = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant FIM = Instant.parse("2026-06-01T10:00:00Z");

    private static AcessoAulaPolicy em(Instant agora) {
        return new AcessoAulaPolicy(Clock.fixed(agora, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "um segundo antes do início,     2026-03-01T09:59:59Z, false, false",
        "exatamente no início,           2026-03-01T10:00:00Z, false, true",
        "exatamente no fim,              2026-06-01T10:00:00Z, false, true",
        "um segundo depois do fim,       2026-06-01T10:00:01Z, false, false",
        "antes do início — aula aberta,   2026-03-01T09:59:59Z, true,  true",
        "depois do fim — aula aberta,     2026-06-01T10:00:01Z, true,  true",
    })
    void acessoPeloPeriodoDaMatricula(String caso, Instant agora, boolean aulaAberta, boolean esperado) {
        assertThat(em(agora).podeAcessarAula(matricula(1, 2, 3, INICIO, FIM), aulaAberta))
                .isEqualTo(esperado);
    }

    @ParameterizedTest(name = "aula aberta: {0}")
    @CsvSource({"false, false", "true, true"})
    void semMatricula(boolean aulaAberta, boolean esperado) {
        assertThat(em(INICIO).podeAcessarAula(null, aulaAberta)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "aula aberta: {0}")
    @CsvSource({"false, false", "true, true"})
    void matriculaCanceladaDentroDoPeriodo(boolean aulaAberta, boolean esperado) {
        assertThat(em(INICIO.plusSeconds(60)).podeAcessarAula(cancelada(matricula(1, 2, 3, INICIO, FIM)), aulaAberta))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("turma inteira só com matrícula vigente: aula aberta não abre a turma")
    void turmaSoComMatriculaVigente() {
        Instant dentro = Instant.parse("2026-04-15T00:00:00Z");
        assertThat(em(dentro).podeAcessarTurma(matricula(1, 2, 3, INICIO, FIM))).isTrue();
        assertThat(em(dentro).podeAcessarTurma(matricula(1, 2, 3, INICIO, null)))
                .isTrue();
        assertThat(em(dentro).podeAcessarTurma(null)).isFalse();
        assertThat(em(dentro).podeAcessarTurma(cancelada(matricula(1, 2, 3, INICIO, FIM))))
                .isFalse();
        assertThat(em(FIM.plusSeconds(1)).podeAcessarTurma(matricula(1, 2, 3, INICIO, FIM)))
                .isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/api/MatriculaRequestsTest.java`:

`````java
package br.com.celke.cursos.matricula.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatriculaRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("matricular exige e-mail válido e forma de pagamento; datas e transação são opcionais")
    void matricular() {
        assertThat(camposInvalidos(
                        new MatriculaRequests.Matricular("ana@celke.uno", null, null, FormaPagamento.MANUAL, null)))
                .isEmpty();
        assertThat(camposInvalidos(new MatriculaRequests.Matricular("ana", null, null, null, "a".repeat(101))))
                .containsExactlyInAnyOrder("email", "formaPagamento", "transacao");
        assertThat(camposInvalidos(
                        new MatriculaRequests.Matricular(" ", null, null, FormaPagamento.PIX, "a".repeat(100))))
                .containsExactly("email");
    }

    @Test
    @DisplayName("período exige o início; o fim é opcional")
    void periodo() {
        assertThat(camposInvalidos(new MatriculaRequests.Periodo(Instant.EPOCH, null)))
                .isEmpty();
        assertThat(camposInvalidos(new MatriculaRequests.Periodo(null, Instant.EPOCH)))
                .containsExactly("inicio");
    }

    @Test
    @DisplayName("os dados repassam todos os campos")
    void dados() {
        var dados = new MatriculaRequests.Matricular("ana@celke.uno", Instant.EPOCH, null, FormaPagamento.PIX, "tx")
                .dados();
        assertThat(dados.email()).isEqualTo("ana@celke.uno");
        assertThat(dados.inicio()).isEqualTo(Instant.EPOCH);
        assertThat(dados.transacao()).isEqualTo("tx");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/application/MatriculaAdmServiceTest.java`:

`````java
package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static br.com.celke.cursos.support.fixtures.UsuarioFixture.inativo;
import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.FormaPagamento;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MatriculaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String EMAIL = "ana@celke.uno";

    @Mock
    private MatriculaRepository matriculas;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    private MatriculaAdmService service;
    private final Usuario ana = usuario(7, EMAIL, "hash", Papel.ALUNO);

    @BeforeEach
    void setUp() {
        service = new MatriculaAdmService(
                matriculas, usuarioService, conteudoTurmaService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static TurmaResumo turma(StatusTurma status) {
        return new TurmaResumo(5L, "Turma", status, 1L, "Java", "java", StatusCurso.PUBLICADO, null);
    }

    private static NovaMatricula nova(Instant inicio, Instant fim, String transacao) {
        return new NovaMatricula(EMAIL, inicio, fim, FormaPagamento.PIX, transacao);
    }

    @Nested
    class Matricular {

        @Test
        @DisplayName("sem início, começa agora; grava usuário, turma, forma de pagamento e transação")
        void matricula() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.PIX, "tx-1"))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any(Matricula.class))).thenAnswer(i -> i.getArgument(0));

            Instant fim = AGORA.plusSeconds(86_400);
            Matricula matricula =
                    service.matricular(5L, nova(null, fim, " tx-1 ")).matricula();

            assertThat(matricula.getUsuarioId()).isEqualTo(7L);
            assertThat(matricula.getTurmaId()).isEqualTo(5L);
            assertThat(matricula.getInicio()).isEqualTo(AGORA);
            assertThat(matricula.getFim()).isEqualTo(fim);
            assertThat(matricula.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
            assertThat(matricula.getTransacao()).isEqualTo("tx-1");
        }

        @Test
        @DisplayName("turma inexistente responde não encontrada")
        void turmaInexistente() {
            when(conteudoTurmaService.turma(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.matricular(9L, nova(null, null, null)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Turma não encontrada.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("turma encerrada não recebe matrícula")
        void turmaEncerrada() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ENCERRADA)));

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("A turma está encerrada e não recebe novas matrículas.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("usuário inexistente ou inativo responde não encontrado")
        void usuarioInexistenteOuInativo() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.PREPARACAO)));
            when(usuarioService.buscarPorEmail(EMAIL))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(inativo(usuario(8, EMAIL, "hash", Papel.ALUNO))));

            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                        .isInstanceOf(NaoEncontradoException.class)
                        .hasMessage("Usuário não encontrado.");
            }
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("aluno com matrícula ativa na turma recebe 409")
        void jaMatriculado() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("transação já usada recebe 409")
        void transacaoRepetida() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.existsByFormaPagamentoAndTransacao(FormaPagamento.PIX, "tx-1"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, "tx-1")))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Esta transação já gerou uma matrícula.");
            verify(matriculas, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("duas matrículas iguais ao mesmo tempo: o índice do banco recusa a segunda com 409")
        void corrida() {
            when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
            when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(Optional.of(ana));
            when(matriculas.existsByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                    .thenReturn(false);
            when(matriculas.saveAndFlush(any(Matricula.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_matriculas_ativa"));

            assertThatThrownBy(() -> service.matricular(5L, nova(null, null, null)))
                    .isInstanceOf(ConflitoException.class);
        }
    }

    @Test
    @DisplayName("lista as mais recentes primeiro, com os dados do aluno, ignorando a ordenação do cliente")
    void listar() {
        Matricula matricula = matricula(1, 7, 5, AGORA, null);
        var pagina = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        when(conteudoTurmaService.turma(5L)).thenReturn(Optional.of(turma(StatusTurma.ATIVA)));
        when(matriculas.findByTurmaId(5L, pagina)).thenReturn(new PageImpl<>(List.of(matricula), pagina, 1));
        when(usuarioService.buscarPorIds(List.of(7L))).thenReturn(Map.of(7L, ana));

        var itens =
                service.listar(5L, PageRequest.of(0, 20, Sort.by("usuarioId"))).getContent();

        assertThat(itens).singleElement().satisfies(item -> {
            assertThat(item.matricula()).isSameAs(matricula);
            assertThat(item.usuario()).isSameAs(ana);
        });
    }

    @Test
    @DisplayName("altera o período e cancela; matrícula inexistente responde não encontrada")
    void periodoECancelamento() {
        Matricula matricula = matricula(1, 7, 5, AGORA, null);
        when(matriculas.findById(1L)).thenReturn(Optional.of(matricula));
        when(matriculas.findById(9L)).thenReturn(Optional.empty());
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));

        Instant fim = AGORA.plusSeconds(3_600);
        service.alterarPeriodo(1L, AGORA, fim);
        assertThat(matricula.getFim()).isEqualTo(fim);
        service.cancelar(1L);
        assertThat(matricula.getStatus()).isEqualTo(StatusMatricula.CANCELADA);
        assertThatThrownBy(() -> service.cancelar(9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Matrícula não encontrada.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/application/AlunoCursosServiceTest.java`:

`````java
package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.cancelada;
import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.ModuloConteudo;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.application.AlunoCursosService.MeuCurso;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.FiltroMeusCursos;
import br.com.celke.cursos.matricula.domain.Matricula;
import br.com.celke.cursos.matricula.domain.SituacaoMatricula;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlunoCursosServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant ONTEM = AGORA.minusSeconds(86_400);
    private static final Instant AMANHA = AGORA.plusSeconds(86_400);

    @Mock
    private MatriculaRepository matriculas;

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    private AlunoCursosService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        service = new AlunoCursosService(matriculas, conteudoTurmaService, new AcessoAulaPolicy(clock), clock);
    }

    private static TurmaResumo turma(long id, String cursoTitulo) {
        return new TurmaResumo(
                id, "Turma " + id, StatusTurma.ATIVA, id, cursoTitulo, "c" + id, StatusCurso.PUBLICADO, null);
    }

    @Test
    @DisplayName("meus cursos filtra pela aba e ordena pelo título do curso")
    void meusCursos() {
        Matricula react = matricula(1, 7, 10, ONTEM, null);
        Matricula angular = matricula(2, 7, 20, ONTEM, AMANHA);
        Matricula vencida = matricula(3, 7, 30, ONTEM.minusSeconds(86_400), ONTEM);
        when(matriculas.findByUsuarioIdAndStatus(7L, StatusMatricula.ATIVA))
                .thenReturn(List.of(react, angular, vencida));
        when(conteudoTurmaService.turmas(List.of(10L, 20L)))
                .thenReturn(Map.of(10L, turma(10, "React"), 20L, turma(20, "Angular")));

        List<MeuCurso> ativos = service.meusCursos(7L, FiltroMeusCursos.ATIVOS);

        assertThat(ativos).extracting(c -> c.turma().cursoTitulo()).containsExactly("Angular", "React");
        assertThat(ativos).extracting(MeuCurso::situacao).containsOnly(SituacaoMatricula.VIGENTE);
    }

    @Test
    @DisplayName("turma com matrícula vigente traz o conteúdo ativo")
    void turmaVigente() {
        Matricula matricula = matricula(1, 7, 10, ONTEM, AMANHA);
        List<ModuloConteudo> conteudo = List.of(new ModuloConteudo(1L, "Módulo", List.of()));
        when(conteudoTurmaService.turma(10L)).thenReturn(Optional.of(turma(10, "React")));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 10L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula));
        when(conteudoTurmaService.conteudoAtivo(10L)).thenReturn(conteudo);

        var turma = service.turma(7L, 10L);

        assertThat(turma.matricula()).isSameAs(matricula);
        assertThat(turma.modulos()).isEqualTo(conteudo);
    }

    @Test
    @DisplayName("turma sem matrícula, vencida ou ainda não iniciada responde 403 sem carregar o conteúdo")
    void turmaSemMatriculaVigente() {
        when(conteudoTurmaService.turma(10L)).thenReturn(Optional.of(turma(10, "React")));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 10L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(matricula(1, 7, 10, ONTEM.minusSeconds(60), ONTEM)))
                .thenReturn(Optional.of(matricula(1, 7, 10, AMANHA, null)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.turma(7L, 10L))
                    .isInstanceOf(AcessoNegadoException.class)
                    .hasMessage("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        }
        verify(conteudoTurmaService, never()).conteudoAtivo(10L);
    }

    @Test
    @DisplayName("turma inexistente responde não encontrada")
    void turmaInexistente() {
        when(conteudoTurmaService.turma(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.turma(7L, 9L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    @DisplayName("arquiva e desarquiva a própria matrícula")
    void arquivar() {
        Matricula matricula = matricula(1, 7, 10, ONTEM, null);
        when(matriculas.findByIdAndUsuarioId(1L, 7L)).thenReturn(Optional.of(matricula));

        service.arquivar(7L, 1L);
        assertThat(matricula.getArquivadaEm()).isEqualTo(AGORA);
        service.desarquivar(7L, 1L);
        assertThat(matricula.isArquivada()).isFalse();
    }

    @Test
    @DisplayName("matrícula de outro aluno ou cancelada responde 404")
    void matriculaDeOutroAlunoOuCancelada() {
        when(matriculas.findByIdAndUsuarioId(1L, 8L)).thenReturn(Optional.empty());
        when(matriculas.findByIdAndUsuarioId(2L, 7L))
                .thenReturn(Optional.of(cancelada(matricula(2, 7, 10, ONTEM, null))));

        assertThatThrownBy(() -> service.arquivar(8L, 1L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Matrícula não encontrada.");
        assertThatThrownBy(() -> service.desarquivar(7L, 2L)).isInstanceOf(NaoEncontradoException.class);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/application/AcessoAulaServiceTest.java`:

`````java
package br.com.celke.cursos.matricula.application;

import static br.com.celke.cursos.support.fixtures.MatriculaFixture.matricula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.AulaContexto;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.matricula.domain.AcessoAulaPolicy;
import br.com.celke.cursos.matricula.domain.StatusMatricula;
import br.com.celke.cursos.matricula.infra.MatriculaRepository;
import br.com.celke.cursos.shared.erro.AcessoNegadoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcessoAulaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ConteudoTurmaService conteudoTurmaService;

    @Mock
    private MatriculaRepository matriculas;

    private AcessoAulaService service;

    @BeforeEach
    void setUp() {
        service = new AcessoAulaService(
                conteudoTurmaService, matriculas, new AcessoAulaPolicy(Clock.fixed(AGORA, ZoneOffset.UTC)));
    }

    private static AulaContexto aula(boolean gratuita) {
        return new AulaContexto(100L, "Aula", null, gratuita, 5L, StatusTurma.ATIVA, StatusCurso.PUBLICADO);
    }

    @Test
    @DisplayName("matrícula vigente na turma da aula libera o acesso")
    void comMatricula() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.of(matricula(1, 7, 5, AGORA.minusSeconds(60), null)));

        assertThat(service.exigirAcesso(7L, 100L).id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("aula aberta libera sem matrícula")
    void aulaAberta() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(true)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.exigirAcesso(7L, 100L).gratuita()).isTrue();
    }

    @Test
    @DisplayName("sem matrícula vigente e aula paga responde 403")
    void semAcesso() {
        when(conteudoTurmaService.aulaAtiva(100L)).thenReturn(Optional.of(aula(false)));
        when(matriculas.findByUsuarioIdAndTurmaIdAndStatus(7L, 5L, StatusMatricula.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 100L))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
    }

    @Test
    @DisplayName("aula inexistente ou inativa responde 404 sem consultar a matrícula")
    void aulaInexistente() {
        when(conteudoTurmaService.aulaAtiva(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigirAcesso(7L, 9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(matriculas, never()).findByUsuarioIdAndTurmaIdAndStatus(any(), any(), any());
    }
}
`````

### 10. Testes de integração

`backend/src/test/java/br/com/celke/cursos/matricula/MatriculaITHelper.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Monta curso publicado com conteúdo e matrículas pela API do ADM, para os testes do módulo matrícula. */
final class MatriculaITHelper {

    private final CursoITHelper adm;

    MatriculaITHelper(CursoITHelper adm) {
        this.adm = adm;
    }

    CursoITHelper adm() {
        return adm;
    }

    /** Curso publicado com turma ativa e um módulo com uma aula paga e uma gratuita. */
    Estrutura cursoPublicado(String slug) {
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        long turma = adm.criarTurma(curso, "Turma");
        adm.ativarTurma(turma);
        long modulo = adm.criarModulo(turma, "Módulo");
        long paga = adm.criarAula(modulo, "Aula paga", false);
        long gratuita = adm.criarAula(modulo, "Aula gratuita", true);
        adm.publicar(curso);
        return new Estrutura(curso, turma, modulo, paga, gratuita);
    }

    MvcTestResult matricular(long turma, String email, Instant inicio, Instant fim) {
        return adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"MANUAL\"%s%s}"
                        .formatted(
                                email,
                                inicio == null ? "" : ",\"inicio\":\"" + inicio + "\"",
                                fim == null ? "" : ",\"fim\":\"" + fim + "\""));
    }

    /** Matricula e devolve o id da matrícula (confere o 201). */
    long matricula(long turma, String email, Instant inicio, Instant fim) {
        return CursoITHelper.idCriado(matricular(turma, email, inicio, fim));
    }

    void cancelar(long matricula) {
        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + matricula + "/cancelamento", null))
                .hasStatus(HttpStatus.OK);
    }

    record Estrutura(long curso, long turma, long modulo, long aulaPaga, long aulaGratuita) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/MatriculaAcessoIT.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Instant;
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
 * Cobre: matriz de papéis das rotas de matrícula — ADM ({@code /api/adm}: 401 anônimo, 403 ALUNO,
 * ADMIN passa) e área do aluno ({@code /api/aluno}: privada, 401 anônimo; ALUNO e ADMIN matriculados
 * acessam).
 * Não cobre: regras de cada rota (MatriculaAdmIT, AlunoCursosIT, AcessoConteudoIT).
 * Dado criado: a cada caso, curso it-<stamp> publicado, um ALUNO e um ADMIN próprios, matriculados.
 */
class MatriculaAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private MatriculaITHelper.Estrutura estrutura;
    private long matriculaAluno;
    private long matriculaAdmin;
    private long matriculaOutra;

    @BeforeEach
    void criarEstrutura() {
        MatriculaITHelper helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);
        Instant ontem = Instant.now().minusSeconds(86_400);
        matriculaAluno = helper.matricula(estrutura.turma(), aluno.email(), ontem, null);
        matriculaAdmin = helper.matricula(estrutura.turma(), admin.email(), ontem, null);
        matriculaOutra =
                helper.matricula(estrutura.turma(), criarUsuario(Papel.ALUNO).email(), ontem, null);
        tokenAluno = login(aluno.email(), aluno.senha());
        tokenAdmin = login(admin.email(), admin.senha());
    }

    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/matriculas", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/turmas/{t}/matriculas",
                        "{\"email\":\"{email}\",\"formaPagamento\":\"CORTESIA\"}",
                        HttpStatus.CREATED),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/matriculas/{outra}/periodo",
                        "{\"inicio\":\"2026-01-01T00:00:00Z\"}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/matriculas/{outra}/cancelamento", null, HttpStatus.OK));
    }

    /** Rotas do aluno; {matricula} é a matrícula de quem chama. */
    static Stream<Arguments> rotasAluno() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/aluno/cursos", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/aluno/turmas/{t}", HttpStatus.OK),
                Arguments.of(HttpMethod.GET, "/api/aluno/aulas/{paga}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/aluno/matriculas/{matricula}/arquivamento", HttpStatus.NO_CONTENT),
                Arguments.of(
                        HttpMethod.DELETE, "/api/aluno/matriculas/{matricula}/arquivamento", HttpStatus.NO_CONTENT));
    }

    private MvcTestResult chamar(HttpMethod metodo, String rota, String corpo, String token, long matricula) {
        String uri = rota.replace("{t}", String.valueOf(estrutura.turma()))
                .replace("{paga}", String.valueOf(estrutura.aulaPaga()))
                .replace("{outra}", String.valueOf(matriculaOutra))
                .replace("{matricula}", String.valueOf(matricula));
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            String email = criarUsuario(Papel.ALUNO).email();
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo.replace("{email}", email));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo 401, ALUNO 403")
    void admSemPapel(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null, 0)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(metodo, rota, corpo, tokenAluno, 0))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin, 0)).hasStatus(admin);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: anônimo 401")
    void alunoAnonimo(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, null, matriculaAluno))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: ALUNO matriculado acessa")
    void alunoAluno(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, tokenAluno, matriculaAluno)).hasStatus(esperado);
    }

    @ParameterizedTest(name = "{0} {1} → {2}")
    @MethodSource("rotasAluno")
    @DisplayName("aluno: ADMIN matriculado acessa")
    void alunoAdmin(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(chamar(metodo, rota, null, tokenAdmin, matriculaAdmin)).hasStatus(esperado);
    }

    @Test
    @DisplayName("matrícula de outro aluno responde 404 no arquivamento (sem revelar que existe)")
    void matriculaDeOutroAluno() {
        assertThat(chamar(
                        HttpMethod.PUT,
                        "/api/aluno/matriculas/{matricula}/arquivamento",
                        null,
                        tokenAluno,
                        matriculaOutra))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Matrícula não encontrada.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/MatriculaAdmIT.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: matrícula manual pelo ADM — matricular (padrões, Location, dados do aluno na lista),
 * matrícula repetida, nova matrícula depois de cancelar, turma encerrada, usuário inexistente,
 * transação repetida, período inválido, alteração de matrícula cancelada, validação e turma com
 * matrículas que não pode ser excluída.
 * Não cobre: acesso do aluno ao conteúdo (AcessoConteudoIT).
 * Dado criado: cursos it-<stamp> e alunos it-<stamp>@teste.celke.uno.
 */
class MatriculaAdmIT extends IntegrationTest {

    private MatriculaITHelper helper;
    private CursoITHelper adm;
    private long turma;

    @BeforeEach
    void criarTurma() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        helper = new MatriculaITHelper(adm);
        turma = helper.cursoPublicado(stamp()).turma();
    }

    @Test
    @DisplayName("matricula sem datas (começa agora, sem prazo), lista com o aluno, altera o período e cancela")
    void cicloDaMatricula() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        Instant antes = Instant.now();

        var criacao = adm.enviar(
                "POST",
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"PIX\",\"transacao\":\" %s \"}"
                        .formatted(aluno.email().toUpperCase(), aluno.email()));
        long id = CursoITHelper.idCriado(criacao);
        assertThat(criacao.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/adm/matriculas/" + id);
        assertThat(criacao).bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"usuarioId":%d,"email":"%s","fim":null,"status":"ATIVA",
                         "situacao":"VIGENTE","formaPagamento":"PIX","transacao":"%s"}
                        """.formatted(
                        turma, aluno.id(), aluno.email(), aluno.email()));
        String inicio = com.jayway.jsonpath.JsonPath.read(CursoITHelper.corpo(criacao), "$.inicio");
        assertThat(Instant.parse(inicio)).isAfterOrEqualTo(antes.minusSeconds(1));

        assertThat(adm.enviar("GET", "/api/adm/turmas/" + turma + "/matriculas", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"itens\":[{\"id\":%d,\"nome\":\"Usuário %s\"}],\"total\":1}".formatted(id, aluno.email()));

        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/matriculas/" + id + "/periodo",
                        "{\"inicio\":\"2026-01-01T00:00:00Z\",\"fim\":\"2026-02-01T00:00:00Z\"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"inicio\":\"2026-01-01T00:00:00Z\",\"fim\":\"2026-02-01T00:00:00Z\",\"situacao\":\"VENCIDA\"}");

        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + id + "/cancelamento", null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"status\":\"CANCELADA\",\"situacao\":\"CANCELADA\"}");
        assertThat(adm.enviar("POST", "/api/adm/matriculas/" + id + "/cancelamento", null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A matrícula está cancelada.");
        assertThat(adm.enviar("PUT", "/api/adm/matriculas/" + id + "/periodo", "{\"inicio\":\"2026-01-01T00:00:00Z\"}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("matrícula repetida responde 409; depois de cancelar, o aluno pode ser matriculado de novo")
    void matriculaRepetida() {
        String email = criarUsuario(Papel.ALUNO).email();
        long primeira = helper.matricula(turma, email, null, null);

        assertThat(helper.matricular(turma, email, null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O aluno já tem matrícula nesta turma. Altere o período da matrícula atual.");

        helper.cancelar(primeira);
        assertThat(helper.matricular(turma, email, null, null)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("turma encerrada, usuário inexistente e transação repetida são recusados")
    void recusas() {
        String email = criarUsuario(Papel.ALUNO).email();
        assertThat(helper.matricular(turma, "ninguem-" + stamp() + "@teste.celke.uno", null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Usuário não encontrado.");
        assertThat(helper.matricular(999_999_999L, email, null, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Turma não encontrada.");

        String transacao = "{\"email\":\"%s\",\"formaPagamento\":\"CARTAO\",\"transacao\":\"tx-" + turma + "\"}";
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/matriculas", transacao.formatted(email)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(adm.enviar(
                        "POST",
                        "/api/adm/turmas/" + turma + "/matriculas",
                        transacao.formatted(criarUsuario(Papel.ALUNO).email())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta transação já gerou uma matrícula.");

        adm.status("/api/adm/turmas/" + turma + "/status", "ENCERRADA");
        assertThat(helper.matricular(turma, criarUsuario(Papel.ALUNO).email(), null, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma está encerrada e não recebe novas matrículas.");
    }

    @Test
    @DisplayName("período inválido e corpo inválido respondem 400")
    void validacao() {
        String email = criarUsuario(Papel.ALUNO).email();
        assertThat(helper.matricular(
                        turma, email, Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O fim da matrícula deve ser depois do início.");
        assertThat(adm.enviar("POST", "/api/adm/turmas/" + turma + "/matriculas", "{\"email\":\"x\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactlyInAnyOrder("email", "formaPagamento");
    }

    @Test
    @DisplayName("turma com matrículas não é excluída, mesmo sem módulos")
    void turmaComMatriculas() {
        String slug = stamp();
        long semModulos = adm.criarTurma(adm.criarCurso("IT Curso " + slug, slug), "Sem módulos");
        helper.matricula(semModulos, criarUsuario(Papel.ALUNO).email(), null, null);

        assertThat(adm.enviar("DELETE", "/api/adm/turmas/" + semModulos, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A turma tem matrículas. Encerre a turma.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/AcessoConteudoIT.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: matriz de acesso ao conteúdo da AcessoAulaPolicy nas rotas da turma e da aula — matrícula
 * vigente, sem matrícula, vencida, cancelada, ainda não iniciada; aula gratuita sem matrícula (e em
 * curso não publicado); aula e módulo inativos; só conteúdo ativo na turma.
 * Não cobre: as combinações de datas caso a caso (AcessoAulaPolicyTest, MatriculaTest).
 * Dado criado: a cada caso, curso it-<stamp> publicado e um aluno próprio.
 */
class AcessoConteudoIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private MatriculaITHelper helper;
    private MatriculaITHelper.Estrutura estrutura;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarCursoEAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        estrutura = helper.cursoPublicado(stamp());
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult get(String uri) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    private MvcTestResult turma() {
        return get("/api/aluno/turmas/" + estrutura.turma());
    }

    private MvcTestResult aula(long aula) {
        return get("/api/aluno/aulas/" + aula);
    }

    /** Situação da matrícula do aluno: nenhuma, vencida, cancelada ou ainda não iniciada. */
    static Stream<Arguments> semMatriculaVigente() {
        return Stream.of(
                Arguments.of("sem matrícula"),
                Arguments.of("vencida"),
                Arguments.of("cancelada"),
                Arguments.of("ainda não iniciada"));
    }

    private void prepararMatricula(String situacao) {
        Instant agora = Instant.now();
        switch (situacao) {
            case "vencida" ->
                helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA.multipliedBy(2)), agora.minus(DIA));
            case "cancelada" ->
                helper.cancelar(helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), null));
            case "ainda não iniciada" -> helper.matricula(estrutura.turma(), aluno.email(), agora.plus(DIA), null);
            default -> {}
        }
    }

    @Test
    @DisplayName("matrícula vigente: turma com o conteúdo e aula paga liberadas")
    void matriculaVigente() {
        Instant agora = Instant.now();
        helper.matricula(estrutura.turma(), aluno.email(), agora.minus(DIA), agora.plus(DIA));

        assertThat(turma()).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"turmaId":%d,"turmaNome":"Turma","modulos":[{"id":%d,"titulo":"Módulo","aulas":[
                          {"id":%d,"titulo":"Aula paga","gratuita":false},
                          {"id":%d,"titulo":"Aula gratuita","gratuita":true}]}]}
                        """.formatted(
                        estrutura.turma(), estrutura.modulo(), estrutura.aulaPaga(), estrutura.aulaGratuita()));
        assertThat(aula(estrutura.aulaPaga()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"id\":%d,\"turmaId\":%d,\"titulo\":\"Aula paga\",\"gratuita\":false}"
                        .formatted(estrutura.aulaPaga(), estrutura.turma()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semMatriculaVigente")
    @DisplayName("sem matrícula vigente: turma e aula paga 403; aula gratuita liberada")
    void semAcesso(String situacao) {
        prepararMatricula(situacao);

        assertThat(turma())
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta turma. Verifique o período da sua matrícula.");
        assertThat(aula(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Você não tem acesso a esta aula. Verifique o período da sua matrícula.");
        assertThat(aula(estrutura.aulaGratuita())).hasStatusOk();
    }

    @Test
    @DisplayName("aula gratuita de curso que voltou para rascunho não abre sem matrícula")
    void gratuitaDeCursoNaoPublicado() {
        helper.adm().status("/api/adm/cursos/" + estrutura.curso() + "/status", "RASCUNHO");

        assertThat(aula(estrutura.aulaGratuita())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("aula inativa ou de módulo inativo responde 404 mesmo com matrícula vigente; a turma esconde as duas")
    void conteudoInativo() {
        helper.matricula(estrutura.turma(), aluno.email(), Instant.now().minus(DIA), null);
        long outroModulo = helper.adm().criarModulo(estrutura.turma(), "Inativo");
        long aulaDoModuloInativo = helper.adm().criarAula(outroModulo, "Escondida", false);
        helper.adm().status("/api/adm/modulos/" + outroModulo + "/status", "INATIVO");
        helper.adm().status("/api/adm/aulas/" + estrutura.aulaPaga() + "/status", "INATIVO");

        assertThat(aula(estrutura.aulaPaga()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Aula não encontrada.");
        assertThat(aula(aulaDoModuloInativo)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(turma())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.modulos[*].aulas[*].id")
                .asArray()
                .containsExactly((int) estrutura.aulaGratuita());
    }

    @Test
    @DisplayName("matrícula em outra turma não dá acesso a esta")
    void matriculaEmOutraTurma() {
        var outra = helper.cursoPublicado(stamp());
        helper.matricula(outra.turma(), aluno.email(), Instant.now().minus(DIA), null);

        assertThat(turma()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(aula(estrutura.aulaPaga())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(aula(outra.aulaPaga())).hasStatusOk();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/matricula/AlunoCursosIT.java`:

`````java
package br.com.celke.cursos.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: "meus cursos" — abas ativos, arquivados e expirados; matrícula futura em ativos; cancelada
 * fora de todas; arquivar e desarquivar; matrícula de outro aluno (404, nada muda).
 * Não cobre: acesso ao conteúdo (AcessoConteudoIT).
 * Dado criado: cursos it-<stamp> e alunos próprios.
 */
class AlunoCursosIT extends IntegrationTest {

    private static final Duration DIA = Duration.ofDays(1);

    private MatriculaITHelper helper;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarAluno() {
        helper = new MatriculaITHelper(
                new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA))));
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult chamar(String metodo, String uri, String tokenDe) {
        return mvc.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenDe))
                .exchange();
    }

    private MvcTestResult aba(String situacao) {
        return chamar("GET", "/api/aluno/cursos?situacao=" + situacao, token);
    }

    @Test
    @DisplayName("abas: vigente e futura em ativos, vencida em expirados, cancelada em nenhuma; arquivar move de aba")
    void abas() {
        Instant agora = Instant.now();
        String slugVigente = "b-" + stamp();
        String slugFutura = "a-" + stamp();
        String slugVencida = stamp();
        String slugCancelada = stamp();
        long vigente =
                helper.matricula(helper.cursoPublicado(slugVigente).turma(), aluno.email(), agora.minus(DIA), null);
        helper.matricula(helper.cursoPublicado(slugFutura).turma(), aluno.email(), agora.plus(DIA), null);
        helper.matricula(
                helper.cursoPublicado(slugVencida).turma(),
                aluno.email(),
                agora.minus(DIA.multipliedBy(3)),
                agora.minus(DIA));
        helper.cancelar(
                helper.matricula(helper.cursoPublicado(slugCancelada).turma(), aluno.email(), agora.minus(DIA), null));

        assertThat(chamar("GET", "/api/aluno/cursos", token))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"cursoSlug":"%s","situacao":"AGUARDANDO_INICIO","arquivada":false},
                         {"cursoSlug":"%s","situacao":"VIGENTE","arquivada":false,"turmaNome":"Turma","capaUrl":null}]
                        """.formatted(slugFutura, slugVigente));
        assertThat(aba("EXPIRADOS")).bodyJson().isLenientlyEqualTo("""
                        [{"cursoTitulo":"IT Curso %s","cursoSlug":"%s","turmaNome":"Turma","capaUrl":null,
                          "situacao":"VENCIDA","arquivada":false}]
                        """.formatted(slugVencida, slugVencida));
        assertThat(aba("EXPIRADOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .containsExactly(slugVencida);
        assertThat(aba("ARQUIVADOS")).bodyJson().extractingPath("$").asArray().isEmpty();

        assertThat(chamar("PUT", "/api/aluno/matriculas/" + vigente + "/arquivamento", token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(aba("ARQUIVADOS"))
                .bodyJson()
                .isLenientlyEqualTo("[{\"matriculaId\":%d,\"arquivada\":true}]".formatted(vigente));
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .containsExactly(slugFutura);

        assertThat(chamar("DELETE", "/api/aluno/matriculas/" + vigente + "/arquivamento", token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .extractingPath("$[*].cursoSlug")
                .asArray()
                .hasSize(2);
    }

    @Test
    @DisplayName("outro aluno não arquiva a matrícula e não vê o curso na lista dele")
    void matriculaDeOutroAluno() {
        String slug = stamp();
        long matricula = helper.matricula(helper.cursoPublicado(slug).turma(), aluno.email(), null, null);
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String tokenOutro = login(outro.email(), outro.senha());

        assertThat(chamar("PUT", "/api/aluno/matriculas/" + matricula + "/arquivamento", tokenOutro))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Matrícula não encontrada.");
        assertThat(chamar("DELETE", "/api/aluno/matriculas/" + matricula + "/arquivamento", tokenOutro))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(chamar("GET", "/api/aluno/cursos", tokenOutro))
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
        assertThat(aba("ATIVOS"))
                .bodyJson()
                .isLenientlyEqualTo("[{\"matriculaId\":%d,\"arquivada\":false}]".formatted(matricula));
    }

    @Test
    @DisplayName("aba inválida responde 400")
    void abaInvalida() {
        assertThat(aba("TODOS")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
`````

### 11. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Adm.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;

/** Gestão pela área do ADM com o token de um admin: cria registros e troca status, conferindo a resposta. */
public final class Adm {

    private final String token;

    private Adm(String token) {
        this.token = token;
    }

    /** Login com o admin do seed da stack de e2e. */
    public static Adm doSeed() {
        return new Adm(
                Api.login(E2eConfig.ADMIN_SEED, E2eConfig.SENHA_SEED).jsonPath().getString("accessToken"));
    }

    public RequestSpecification requisicao() {
        return Api.adm().header("Authorization", Api.bearer(token));
    }

    /** {@code POST} que cria um registro; confere o 201 e devolve o id. */
    public long criar(String rota, String corpo) {
        return requisicao()
                .body(corpo)
                .post(rota)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    /** {@code PATCH <rota>} com o novo status; confere o 200. */
    public void alterarStatus(String rota, String status) {
        requisicao()
                .body("{\"status\":\"%s\"}".formatted(status))
                .patch(rota)
                .then()
                .statusCode(200)
                .body("status", equalTo(status));
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/matricula/MatriculaE2E.java`:

`````java
package br.com.celke.cursos.e2e.matricula;

import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import br.com.celke.cursos.e2e.support.Dados;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: aluno com conta nova vê só a aula gratuita; o ADM matricula e o aluno passa a ver a turma
 * e as aulas; com a matrícula vencida, o acesso termina e o curso vai para os expirados.
 */
class MatriculaE2E {

    @Test
    @DisplayName("sem matrícula 403, matriculado vê a turma, matrícula vencida 403")
    void acessoPelaMatricula() {
        Adm adm = Adm.doSeed();
        String slug = Dados.stamp();
        long curso = adm.criar(
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Matrícula e2e\"}".formatted(slug, slug));
        long turma = adm.criar("/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        adm.alterarStatus("/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = adm.criar("/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Módulo\"}");
        long gratuita =
                adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Degustação\",\"gratuita\":true}");
        long paga = adm.criar("/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Conteúdo pago\"}");
        adm.alterarStatus("/api/adm/cursos/" + curso + "/status", "PUBLICADO");

        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");

        // 1. Sem matrícula: só a aula gratuita
        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + gratuita).then().statusCode(200).body("titulo", equalTo("Degustação"));

        // 2. O ADM matricula por 30 dias
        Instant agora = Instant.now();
        long matricula = adm.criar(
                "/api/adm/turmas/" + turma + "/matriculas",
                "{\"email\":\"%s\",\"formaPagamento\":\"CORTESIA\",\"fim\":\"%s\"}"
                        .formatted(email, agora.plus(Duration.ofDays(30))));

        aluno(token)
                .get("/api/aluno/cursos")
                .then()
                .statusCode(200)
                .body("cursoSlug", equalTo(List.of(slug)))
                .body("[0].situacao", equalTo("VIGENTE"));
        aluno(token)
                .get("/api/aluno/turmas/" + turma)
                .then()
                .statusCode(200)
                .body("modulos[0].aulas.titulo", equalTo(List.of("Degustação", "Conteúdo pago")));
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(200).body("titulo", equalTo("Conteúdo pago"));

        // 3. O período passa a ter terminado ontem: o acesso acaba na hora
        adm.requisicao()
                .body("{\"inicio\":\"%s\",\"fim\":\"%s\"}"
                        .formatted(agora.minus(Duration.ofDays(30)), agora.minus(Duration.ofDays(1))))
                .put("/api/adm/matriculas/" + matricula + "/periodo")
                .then()
                .statusCode(200)
                .body("situacao", equalTo("VENCIDA"));

        aluno(token).get("/api/aluno/turmas/" + turma).then().statusCode(403);
        aluno(token).get("/api/aluno/aulas/" + paga).then().statusCode(403);
        aluno(token).get("/api/aluno/cursos").then().statusCode(200).body("size()", equalTo(0));
        aluno(token)
                .get("/api/aluno/cursos?situacao=EXPIRADOS")
                .then()
                .statusCode(200)
                .body("cursoSlug", equalTo(List.of(slug)));
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

## Antes de executar

Fase 2 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/matricula-acesso
```

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 292 testes unitários e 255 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 13 jornadas passam, entre elas a `MatriculaE2E`.

No ambiente local, pelo Swagger, com um curso publicado e uma turma ativa:

- Cadastre um aluno e, sem matrícula, confira 403 em `GET /api/aluno/turmas/{id}` e 200 numa aula
  gratuita.
- Como admin, matricule o aluno pelo e-mail; a turma passa a responder 200 e o curso aparece em
  `GET /api/aluno/cursos`.
- Altere o período para terminar ontem: a turma volta a responder 403 e o curso vai para
  `?situacao=EXPIRADOS`.

## Depois de executar

```bash
git add backend/
git commit -m "Matrícula e política única de acesso"
git push -u origin feature/matricula-acesso
```

Abra o Pull Request de `feature/matricula-acesso` para `homolog`.

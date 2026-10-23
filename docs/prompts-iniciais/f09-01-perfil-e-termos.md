# Perfil e termos

**Fase:** 9
**Branch:** `feature/perfil-termos`

## Prompt

Vamos criar os módulos `perfil` e `termo` do backend: o usuário cuida dos próprios dados e só usa a
área do aluno depois de aceitar os termos vigentes. Siga o `backend/CLAUDE.md` e o padrão das skills de
teste. Não faça commit.

O que esta etapa entrega:

- **`MarkdownSeguro` em `shared/texto`** (sai do fórum, que passa a importá-lo de lá): os termos também
  são escritos em Markdown.
- **Perfil** (`/api/perfil`, para qualquer usuário autenticado, sempre pelo token): `GET` sem senha nem
  hash; `PUT` do nome; `PUT /senha` (exige a atual; política do papel — aluno 10, admin 12 —; diferente
  da atual; revoga todas as sessões e limpa o cookie de refresh); foto (migration `V14`: `foto_chave`)
  com `POST /foto/envio`, `PUT /foto` (chave `uploads/fotos/<usuario>/<uuid>` do próprio usuário,
  regravada em JPEG sem metadados até 400 × 400, anterior apagada) e `DELETE /foto`.
- **Termos** (migration `V14`): `termos` (tipo `TERMOS_USO` ou `POLITICA_PRIVACIDADE`, versão crescente,
  Markdown e HTML filtrado) e `aceites_termos` (com IP e navegador). A vigente de cada tipo é a maior
  versão. `GET /api/termos/vigentes` público; `GET /api/termos/pendentes`; `POST /api/termos/{id}/aceite`
  (só a vigente; antiga, 409); ADM: `POST /api/adm/termos` e `GET /api/adm/termos?tipo=`.
- **Bloqueio**: `TermoPendenteInterceptor` (registrado por `TermoWebConfig`) em `/api/aluno/**` e
  `/api/aulas/**` responde 403 com `termosPendentes` enquanto houver vigente sem aceite. Para levar a
  lista ao ProblemDetail, `RegraNegocioException` ganha `propriedades()` e o `ManipuladorErros` as copia.
- **Testes compartilhando estado**: o `TermoIT` apaga termos e aceites ao fim de cada teste (um termo
  vigente bloquearia os alunos das outras classes); no e2e, `Api.login` aceita os termos pendentes depois
  do login, como o front fará, e `Api.loginSemAceite` serve à jornada do bloqueio.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado. Antes, mova o
`MarkdownSeguro` e o teste dele de `forum/domain` para `shared/texto` (`git mv`).

### 1. Arquivos alterados (substitua o conteúdo inteiro)

`backend/src/main/java/br/com/celke/cursos/shared/erro/RegraNegocioException.java`:

`````java
package br.com.celke.cursos.shared.erro;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com mensagem para o usuário. A mensagem vai no {@code detail} do ProblemDetail,
 * por isso deve ser escrita em português e nunca conter dados internos.
 */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Campos extras do ProblemDetail (ex.: os termos pendentes); vazio por padrão. */
    public Map<String, Object> propriedades() {
        return Map.of();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/erro/ManipuladorErros.java`:

`````java
package br.com.celke.cursos.shared.erro;

import br.com.celke.cursos.shared.web.IdRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção em ProblemDetail (RFC 9457). Erros do Spring MVC (404, 405, JSON inválido...)
 * vêm da classe base; aqui ficam validação, regra de negócio e o erro inesperado, que nunca expõe a
 * causa ao cliente.
 */
@RestControllerAdvice
public class ManipuladorErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManipuladorErros.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroCampo(erro.getField(), erro.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo).thenComparing(ErroCampo::mensagem))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<Object> regraNegocio(RegraNegocioException ex, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        ex.propriedades().forEach(problema::setProperty);
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof MuitasTentativasException limite) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        }
        return handleExceptionInternal(ex, problema, headers, ex.getStatus(), request);
    }

    /**
     * Erro inesperado: registra a causa no log e devolve só o id da requisição. Exceções de
     * segurança voltam para o Spring Security, que decide entre 401 e 403.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> inesperado(Exception ex, WebRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        LOG.error("Erro inesperado", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Se o problema continuar, informe o código " + MDC.get(IdRequisicao.CHAVE_MDC) + ".");
        return handleExceptionInternal(ex, problema, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Completa todo ProblemDetail com o caminho da requisição e o id de correlação. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problema) {
            if (request instanceof ServletWebRequest servlet) {
                HttpServletRequest http = servlet.getRequest();
                problema.setInstance(URI.create(http.getRequestURI()));
            }
            problema.setProperty("requestId", MDC.get(IdRequisicao.CHAVE_MDC));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`:

`````java
package br.com.celke.cursos.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Segurança da API: tudo exige autenticação, exceto as rotas públicas declaradas aqui com o
 * método HTTP. A API é stateless: o access token (JWT) vem no cabeçalho {@code Authorization}, sem
 * sessão, formulário ou HTTP Basic. Só existe na aplicação web: o worker não atende HTTP.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim do JWT com os papéis do usuário (ALUNO, ADMIN), convertidos em ROLE_ALUNO, ROLE_ADMIN. */
    public static final String CLAIM_PAPEIS = "papeis";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        RespostasSeguranca respostas = new RespostasSeguranca(jsonMapper);
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 e 403 no formato ProblemDetail
                .exceptionHandling(
                        erros -> erros.authenticationEntryPoint(respostas).accessDeniedHandler(respostas))
                .oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorPapeis()))
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas))
                .authorizeHttpRequests(rotas -> rotas
                        // Health check e probes (Docker e Caddy)
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Documentação: só responde quando DOCS_ENABLED=true
                        .requestMatchers(
                                HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Autenticação: login e renovação/saída pelo cookie de refresh
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        // Conta: cadastro, confirmação de e-mail e recuperação de senha
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/cadastro",
                                "/api/auth/confirmar-email",
                                "/api/auth/reenviar-confirmacao",
                                "/api/auth/recuperar-senha",
                                "/api/auth/redefinir-senha")
                        .permitAll()
                        // Catálogo: cursos publicados, detalhe e capa
                        .requestMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/*", "/api/cursos/*/capa")
                        .permitAll()
                        // Termos de uso e política de privacidade vigentes (páginas do site)
                        .requestMatchers(HttpMethod.GET, "/api/termos/vigentes")
                        .permitAll()
                        // Validação pública do certificado pelo código (página "validar certificado" do site)
                        .requestMatchers(HttpMethod.GET, "/api/certificados/*")
                        .permitAll()
                        // Área do ADM: o papel é exigido aqui e repetido com @PreAuthorize nos controllers
                        .requestMatchers("/api/adm/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated());
        return http.build();
    }

    private static JwtAuthenticationConverter conversorPapeis() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(CLAIM_PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_papeis", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20)
    private Set<Papel> papeis = EnumSet.noneOf(Papel.class);

    /** Nulo enquanto o dono não confirmar o e-mail. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    /** Foto de perfil no bucket ({@code fotos/<usuario>/<uuid>.jpg}); nula sem foto. */
    @Column(name = "foto_chave", length = 200)
    private String fotoChave;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    public Usuario(
            String nome, String email, String senhaHash, Set<Papel> papeis, boolean emailConfirmado, Instant agora) {
        this.nome = nome.strip();
        this.email = normalizarEmail(email);
        this.senhaHash = senhaHash;
        this.ativo = true;
        this.papeis = EnumSet.copyOf(papeis);
        this.emailConfirmadoEm = emailConfirmado ? agora : null;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** E-mail sempre em minúsculas e sem espaços nas pontas (o banco também exige minúsculas). */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Confirma o e-mail; confirmar de novo não muda a data da primeira confirmação. */
    public void confirmarEmail(Instant agora) {
        if (emailConfirmadoEm == null) {
            this.emailConfirmadoEm = agora;
            this.atualizadoEm = agora;
        }
    }

    public boolean isEmailConfirmado() {
        return emailConfirmadoEm != null;
    }

    public void trocarHashSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    /** Troca a foto (nula remove) e devolve a chave da anterior, a apagar do bucket. */
    public String trocarFoto(String chave, Instant agora) {
        String anterior = this.fotoChave;
        this.fotoChave = chave;
        this.atualizadoEm = agora;
        return anterior;
    }

    public String getFotoChave() {
        return fotoChave;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Papel> getPapeis() {
        return Set.copyOf(papeis);
    }
}
`````

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

    /** A senha informada é a atual do usuário (troca de senha no perfil). */
    public boolean senhaConfere(Usuario usuario, String senha) {
        return senha != null && passwordEncoder.matches(senha, usuario.getSenhaHash());
    }

    @Transactional
    public Usuario renomear(Long usuarioId, String nome) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        usuario.renomear(nome, clock.instant());
        return repository.save(usuario);
    }

    /** Troca a foto de perfil (nula remove); devolve a chave da anterior. */
    @Transactional
    public Optional<String> trocarFoto(Long usuarioId, String chave) {
        Usuario usuario = repository.findById(usuarioId).orElseThrow();
        String anterior = usuario.trocarFoto(chave, clock.instant());
        repository.save(usuario);
        return Optional.ofNullable(anterior);
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

`backend/src/main/java/br/com/celke/cursos/forum/domain/Topico.java`:

`````java
package br.com.celke.cursos.forum.domain;

import br.com.celke.cursos.shared.texto.MarkdownSeguro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pergunta no fórum da turma, geral ou de uma aula. Guarda o Markdown escrito e o HTML já filtrado;
 * só o HTML filtrado é entregue a quem lê.
 */
@Entity
@Table(name = "forum_topicos")
public class Topico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turma_id", nullable = false, updatable = false)
    private Long turmaId;

    @Column(name = "aula_id", updatable = false)
    private Long aulaId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false)
    private boolean oculto;

    @Column(nullable = false)
    private boolean fixado;

    /** Já tem resposta do instrutor (destaque na lista). */
    @Column(name = "respondido_instrutor", nullable = false)
    private boolean respondidoInstrutor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Topico() {}

    public Topico(Long turmaId, Long aulaId, Long autorId, String titulo, String corpoMarkdown, Instant agora) {
        this.turmaId = turmaId;
        this.aulaId = aulaId;
        this.autorId = autorId;
        this.titulo = titulo.strip();
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Moderação: só os campos preenchidos mudam. */
    public void moderar(Boolean oculto, Boolean fixado, Instant agora) {
        if (oculto != null) {
            this.oculto = oculto;
        }
        if (fixado != null) {
            this.fixado = fixado;
        }
        this.atualizadoEm = agora;
    }

    public void registrarRespostaInstrutor(Instant agora) {
        this.respondidoInstrutor = true;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTurmaId() {
        return turmaId;
    }

    public Long getAulaId() {
        return aulaId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isOculto() {
        return oculto;
    }

    public boolean isFixado() {
        return fixado;
    }

    public boolean isRespondidoInstrutor() {
        return respondidoInstrutor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/domain/Resposta.java`:

`````java
package br.com.celke.cursos.forum.domain;

import br.com.celke.cursos.shared.texto.MarkdownSeguro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Resposta a um tópico, de aluno ou do instrutor (ADM). */
@Entity
@Table(name = "forum_respostas")
public class Resposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topico_id", nullable = false, updatable = false)
    private Long topicoId;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(name = "corpo_markdown", nullable = false, columnDefinition = "TEXT")
    private String corpoMarkdown;

    @Column(name = "corpo_html", nullable = false, columnDefinition = "TEXT")
    private String corpoHtml;

    @Column(nullable = false, updatable = false)
    private boolean instrutor;

    @Column(nullable = false)
    private boolean oculto;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Resposta() {}

    public Resposta(Long topicoId, Long autorId, String corpoMarkdown, boolean instrutor, Instant agora) {
        this.topicoId = topicoId;
        this.autorId = autorId;
        this.corpoMarkdown = corpoMarkdown.strip();
        this.corpoHtml = MarkdownSeguro.paraHtml(this.corpoMarkdown);
        this.instrutor = instrutor;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void moderar(boolean oculto, Instant agora) {
        this.oculto = oculto;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public Long getAutorId() {
        return autorId;
    }

    public String getCorpoMarkdown() {
        return corpoMarkdown;
    }

    public String getCorpoHtml() {
        return corpoHtml;
    }

    public boolean isInstrutor() {
        return instrutor;
    }

    public boolean isOculto() {
        return oculto;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
`````

`backend/src/e2e/java/br/com/celke/cursos/e2e/support/Api.java`:

`````java
package br.com.celke.cursos.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Requisições à plataforma pelo Caddy, como um navegador faria. Todos os sites respondem na mesma
 * porta; o cabeçalho Host escolhe qual (sem depender da resolução de {@code *.localhost}).
 */
public final class Api {

    private Api() {}

    public static RequestSpecification aluno() {
        return site(E2eConfig.HOST_ALUNO);
    }

    public static RequestSpecification adm() {
        return site(E2eConfig.HOST_ADM);
    }

    public static RequestSpecification site(String host) {
        return given().baseUri("http://localhost")
                .port(E2eConfig.PORTA)
                .header("Host", host + ":" + E2eConfig.PORTA)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    /**
     * Login pela área do aluno; confere o 200, aceita os termos vigentes ainda pendentes (como o front faz
     * no primeiro acesso) e devolve a resposta do login (access token e cookie).
     */
    public static Response login(String email, String senha) {
        Response resposta = loginSemAceite(email, senha);
        aceitarTermos(resposta.jsonPath().getString("accessToken"));
        return resposta;
    }

    /** Login sem tocar nos termos (jornada que confere o bloqueio por termo pendente). */
    public static Response loginSemAceite(String email, String senha) {
        return aluno().body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
    }

    /** Aceita todos os termos vigentes que o usuário do token ainda não aceitou. */
    public static void aceitarTermos(String accessToken) {
        var pendentes = aluno().header("Authorization", bearer(accessToken))
                .get("/api/termos/pendentes")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id", Long.class);
        for (Long id : pendentes) {
            aluno().header("Authorization", bearer(accessToken))
                    .post("/api/termos/" + id + "/aceite")
                    .then()
                    .statusCode(204);
        }
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
`````

### 2. Markdown seguro em shared (movido do fórum)

`backend/src/main/java/br/com/celke/cursos/shared/texto/MarkdownSeguro.java`:

`````java
package br.com.celke.cursos.shared.texto;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Converte o Markdown (fórum, termos) em HTML seguro, em duas camadas: o commonmark escapa todo HTML escrito
 * pelo usuário e descarta links com esquema perigoso ({@code javascript:}); o sanitizador OWASP deixa
 * passar só uma lista fechada de elementos (texto, listas, citações, código, links e tabelas), sem
 * atributos de evento nem estilo. Links ganham {@code rel="nofollow noopener noreferrer"}.
 */
public final class MarkdownSeguro {

    private static final Parser PARSER = Parser.builder().build();

    private static final HtmlRenderer RENDERER =
            HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    private static final PolicyFactory POLITICA = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "hr", "br")
                    .allowElements("a")
                    .allowAttributes("href")
                    .onElements("a")
                    .allowUrlProtocols("http", "https", "mailto")
                    .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
                    .toFactory());

    private MarkdownSeguro() {}

    public static String paraHtml(String markdown) {
        String html = RENDERER.render(PARSER.parse(markdown == null ? "" : markdown));
        return POLITICA.sanitize(html).strip();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/texto/MarkdownSeguroTest.java`:

`````java
package br.com.celke.cursos.shared.texto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkdownSeguroTest {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Test
    @DisplayName("Markdown comum vira HTML: ênfase, listas, código, citação e tabela")
    void markdownComum() {
        String html = MarkdownSeguro.paraHtml("""
                Olá **turma**, veja o *exemplo*:

                - item 1
                - item 2

                ```
                int x = 1;
                ```

                > citação
                """);

        assertThat(html)
                .contains("<strong>turma</strong>")
                .contains("<em>exemplo</em>")
                .contains("<li>item 1</li>")
                .contains("<pre><code>int x")
                .contains("<blockquote>");
    }

    @Test
    @DisplayName("link http vira <a> com rel nofollow noopener noreferrer")
    void link() {
        assertThat(MarkdownSeguro.paraHtml("[docs](https://docs.oracle.com)"))
                .contains("href=\"https://docs.oracle.com\"")
                .contains("rel=\"nofollow noopener noreferrer\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<a href=\"javascript:alert(1)\">clique</a>",
                "[clique](javascript:alert(1))",
                "[clique](JaVaScRiPt:alert(1))",
                "<iframe src=\"https://mal.example\"></iframe>",
                "<svg onload=alert(1)>",
                "<p style=\"background:url(javascript:alert(1))\">x</p>",
                "![x](javascript:alert(1))",
                "<div onclick=\"alert(1)\">x</div>",
                "[x](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)",
            })
    @DisplayName("XSS: nenhum script, evento, iframe, estilo ou esquema perigoso sobrevive")
    void xss(String ataque) {
        String html = MarkdownSeguro.paraHtml(ataque);

        // O que o usuário escreveu pode aparecer escapado, como texto; o que importa são as tags geradas
        List<String> tags =
                TAG.matcher(html).results().map(m -> m.group().toLowerCase()).toList();
        assertThat(tags)
                .noneMatch(tag -> tag.matches("<(script|img|iframe|svg|object|embed|style)\\b.*"))
                .noneMatch(tag -> tag.matches(".*\\son\\w+\\s*=.*"))
                .noneMatch(tag -> tag.contains("style="))
                .noneMatch(tag -> tag.contains("javascript:"))
                .noneMatch(tag -> tag.contains("data:"));
    }

    @Test
    @DisplayName("HTML escrito pelo usuário aparece como texto, escapado")
    void htmlViraTexto() {
        assertThat(MarkdownSeguro.paraHtml("<b>negrito?</b>")).contains("&lt;b&gt;negrito?&lt;/b&gt;");
    }

    @Test
    @DisplayName("vazio ou nulo vira texto vazio")
    void vazio() {
        assertThat(MarkdownSeguro.paraHtml(null)).isEmpty();
        assertThat(MarkdownSeguro.paraHtml("   ")).isEmpty();
    }
}
`````

### 3. Migration

`backend/src/main/resources/db/migration/V14__perfil_e_termos.sql`:

`````sql
-- Foto de perfil (JPEG sem metadados no bucket)
ALTER TABLE usuarios ADD COLUMN foto_chave VARCHAR(200);

-- Termos de uso e política de privacidade versionados: a versão vigente de cada tipo é a maior.
-- Uma versão publicada não muda; mudar o texto é publicar outra versão.
CREATE TABLE termos (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo               VARCHAR(30)  NOT NULL,
    versao             INT          NOT NULL,
    conteudo_markdown  TEXT         NOT NULL,
    conteudo_html      TEXT         NOT NULL,
    publicado_por      BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    publicado_em       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_termos_tipo CHECK (tipo IN ('TERMOS_USO', 'POLITICA_PRIVACIDADE')),
    CONSTRAINT ck_termos_versao CHECK (versao > 0),
    CONSTRAINT uq_termos_tipo_versao UNIQUE (tipo, versao)
);

-- Aceite de uma versão por um usuário, com de onde veio (prova do consentimento)
CREATE TABLE aceites_termos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    termo_id    BIGINT       NOT NULL REFERENCES termos (id) ON DELETE RESTRICT,
    ip          VARCHAR(45)  NOT NULL,
    user_agent  VARCHAR(255),
    aceito_em   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_aceites_termos UNIQUE (usuario_id, termo_id)
);
`````

### 4. Perfil

`backend/src/main/java/br/com/celke/cursos/perfil/application/PerfilService.java`:

`````java
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
`````

`backend/src/main/java/br/com/celke/cursos/perfil/api/PerfilController.java`:

`````java
package br.com.celke.cursos.perfil.api;

import br.com.celke.cursos.auth.api.CookieRefresh;
import br.com.celke.cursos.perfil.application.PerfilService;
import br.com.celke.cursos.perfil.application.PerfilService.EnvioFoto;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do próprio usuário (aluno ou admin), sempre pelo id do token. */
@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final CookieRefresh cookieRefresh;

    public PerfilController(PerfilService perfilService, CookieRefresh cookieRefresh) {
        this.perfilService = perfilService;
        this.cookieRefresh = cookieRefresh;
    }

    @GetMapping
    @Operation(summary = "Meu perfil", description = "A URL da foto é temporária (vale uma hora).")
    public PerfilResponse buscar(@AuthenticationPrincipal Jwt jwt) {
        return resposta(perfilService.buscar(usuario(jwt)));
    }

    @PutMapping
    @Operation(summary = "Atualizar o nome")
    public PerfilResponse atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AtualizarPerfil request) {
        return resposta(perfilService.renomear(usuario(jwt), request.nome()));
    }

    @PutMapping("/senha")
    @Operation(
            summary = "Trocar a senha",
            description = "Exige a senha atual. Encerra todas as sessões (o cookie de refresh é limpo).")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrocarSenha request) {
        perfilService.trocarSenha(usuario(jwt), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }

    @PostMapping("/foto/envio")
    @Operation(
            summary = "Iniciar o envio da foto",
            description = "JPEG ou PNG até 5 MB. Devolve a URL para enviar direto ao armazenamento.")
    public EnvioFotoResponse iniciarEnvioFoto(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IniciarEnvioFoto request) {
        return EnvioFotoResponse.de(
                perfilService.iniciarEnvioFoto(usuario(jwt), request.contentType(), request.tamanho()));
    }

    @PutMapping("/foto")
    @Operation(summary = "Confirmar a foto", description = "Reduz para até 400 × 400 e regrava em JPEG sem metadados.")
    public PerfilResponse confirmarFoto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmarFoto request) {
        return resposta(perfilService.confirmarFoto(usuario(jwt), request.chave()));
    }

    @DeleteMapping("/foto")
    @Operation(summary = "Remover a foto")
    public ResponseEntity<Void> removerFoto(@AuthenticationPrincipal Jwt jwt) {
        perfilService.removerFoto(usuario(jwt));
        return ResponseEntity.noContent().build();
    }

    private PerfilResponse resposta(Usuario usuario) {
        return PerfilResponse.de(
                usuario, perfilService.urlFoto(usuario).map(URL::toString).orElse(null));
    }

    private static Long usuario(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    public record AtualizarPerfil(@NotBlank @Size(max = 120) String nome) {}

    /** O mínimo da nova senha depende do papel (aluno 10, admin 12) e é conferido no serviço. */
    public record TrocarSenha(
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String senhaAtual,
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String novaSenha) {}

    public record IniciarEnvioFoto(
            @NotBlank String contentType, @Positive long tamanho) {}

    public record ConfirmarFoto(@NotBlank @Size(max = 200) String chave) {}

    /** Nunca inclui senha, hash ou tokens. */
    public record PerfilResponse(Long id, String nome, String email, List<Papel> papeis, String fotoUrl) {

        static PerfilResponse de(Usuario usuario, String fotoUrl) {
            return new PerfilResponse(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getEmail(),
                    usuario.getPapeis().stream().sorted().toList(),
                    fotoUrl);
        }
    }

    public record EnvioFotoResponse(String chave, String url, String contentType, Instant expiraEm) {

        static EnvioFotoResponse de(EnvioFoto envio) {
            return new EnvioFotoResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }
}
`````

### 5. Termos

`backend/src/main/java/br/com/celke/cursos/termo/domain/TipoTermo.java`:

`````java
package br.com.celke.cursos.termo.domain;

public enum TipoTermo {
    TERMOS_USO,
    POLITICA_PRIVACIDADE
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/domain/Termo.java`:

`````java
package br.com.celke.cursos.termo.domain;

import br.com.celke.cursos.shared.texto.MarkdownSeguro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Versão publicada de um termo. Não muda depois de publicada: mudar o texto é publicar outra versão. */
@Entity
@Table(name = "termos")
public class Termo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoTermo tipo;

    @Column(nullable = false, updatable = false)
    private int versao;

    @Column(name = "conteudo_markdown", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudoMarkdown;

    @Column(name = "conteudo_html", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudoHtml;

    @Column(name = "publicado_por", nullable = false, updatable = false)
    private Long publicadoPor;

    @Column(name = "publicado_em", nullable = false, updatable = false)
    private Instant publicadoEm;

    protected Termo() {}

    public Termo(TipoTermo tipo, int versao, String conteudoMarkdown, Long publicadoPor, Instant agora) {
        this.tipo = tipo;
        this.versao = versao;
        this.conteudoMarkdown = conteudoMarkdown.strip();
        this.conteudoHtml = MarkdownSeguro.paraHtml(this.conteudoMarkdown);
        this.publicadoPor = publicadoPor;
        this.publicadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public TipoTermo getTipo() {
        return tipo;
    }

    public int getVersao() {
        return versao;
    }

    public String getConteudoHtml() {
        return conteudoHtml;
    }

    public Instant getPublicadoEm() {
        return publicadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/domain/AceiteTermo.java`:

`````java
package br.com.celke.cursos.termo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Aceite de uma versão de termo por um usuário, com IP e navegador (prova do consentimento). */
@Entity
@Table(name = "aceites_termos")
public class AceiteTermo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "termo_id", nullable = false, updatable = false)
    private Long termoId;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private Instant aceitoEm;

    protected AceiteTermo() {}

    public AceiteTermo(Long usuarioId, Long termoId, String ip, String userAgent, Instant aceitoEm) {
        this.usuarioId = usuarioId;
        this.termoId = termoId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.aceitoEm = aceitoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTermoId() {
        return termoId;
    }

    public String getIp() {
        return ip;
    }

    public Instant getAceitoEm() {
        return aceitoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/infra/TermoRepository.java`:

`````java
package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TermoRepository extends JpaRepository<Termo, Long> {

    /** A maior versão de cada tipo. */
    @Query("""
            select t from Termo t
             where t.versao = (select max(t2.versao) from Termo t2 where t2.tipo = t.tipo)
             order by t.tipo
            """)
    List<Termo> vigentes();

    /** Versões vigentes que o usuário ainda não aceitou. */
    @Query("""
            select t from Termo t
             where t.versao = (select max(t2.versao) from Termo t2 where t2.tipo = t.tipo)
               and not exists (select a from AceiteTermo a where a.termoId = t.id and a.usuarioId = :usuarioId)
             order by t.tipo
            """)
    List<Termo> pendentes(Long usuarioId);

    Optional<Termo> findFirstByTipoOrderByVersaoDesc(TipoTermo tipo);

    List<Termo> findByTipoOrderByVersaoDesc(TipoTermo tipo);
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/infra/AceiteTermoRepository.java`:

`````java
package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.termo.domain.AceiteTermo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AceiteTermoRepository extends JpaRepository<AceiteTermo, Long> {

    boolean existsByUsuarioIdAndTermoId(Long usuarioId, Long termoId);
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/application/TermoService.java`:

`````java
package br.com.celke.cursos.termo.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.domain.AceiteTermo;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Termos de uso e política de privacidade versionados. A versão vigente de cada tipo é a maior; o
 * usuário precisa ter aceitado todas as vigentes para usar a área do aluno (ver
 * {@code TermoPendenteInterceptor}). Publicar uma versão nova exige um aceite novo de todos.
 */
@Service
public class TermoService {

    private final TermoRepository termos;
    private final AceiteTermoRepository aceites;
    private final Clock clock;

    public TermoService(TermoRepository termos, AceiteTermoRepository aceites, Clock clock) {
        this.termos = termos;
        this.aceites = aceites;
        this.clock = clock;
    }

    /** Publica a próxima versão do tipo. Duas publicações simultâneas: a segunda recebe 409. */
    @Transactional
    public Termo publicar(Long adminId, TipoTermo tipo, String conteudoMarkdown) {
        int versao = termos.findFirstByTipoOrderByVersaoDesc(tipo)
                        .map(Termo::getVersao)
                        .orElse(0)
                + 1;
        try {
            return termos.saveAndFlush(new Termo(tipo, versao, conteudoMarkdown, adminId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Outra versão deste termo foi publicada ao mesmo tempo. Tente de novo.");
        }
    }

    @Transactional(readOnly = true)
    public List<Termo> vigentes() {
        return termos.vigentes();
    }

    @Transactional(readOnly = true)
    public List<Termo> historico(TipoTermo tipo) {
        return termos.findByTipoOrderByVersaoDesc(tipo);
    }

    @Transactional(readOnly = true)
    public List<Termo> pendentes(Long usuarioId) {
        return termos.pendentes(usuarioId);
    }

    /**
     * Registra o aceite de uma versão vigente; aceitar de novo não muda nada. Versão antiga: 409. Sem
     * transação própria: o aceite simultâneo repetido falha só no próprio {@code save} e é ignorado.
     */
    public void aceitar(Long usuarioId, Long termoId, OrigemRequisicao origem) {
        Termo termo = termos.findById(termoId).orElseThrow(() -> new NaoEncontradoException("Termo não encontrado."));
        boolean vigente = termos.vigentes().stream().anyMatch(t -> t.getId().equals(termo.getId()));
        if (!vigente) {
            throw new ConflitoException("Esta versão não é mais a vigente. Aceite a versão atual.");
        }
        if (aceites.existsByUsuarioIdAndTermoId(usuarioId, termoId)) {
            return;
        }
        try {
            aceites.save(new AceiteTermo(usuarioId, termoId, origem.ip(), origem.userAgent(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Aceite repetido ao mesmo tempo: o primeiro já vale
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/application/TermoPendenteException.java`:

`````java
package br.com.celke.cursos.termo.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.termo.domain.Termo;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Há termo vigente sem aceite: 403 com a lista {@code termosPendentes} ({@code id}, {@code tipo},
 * {@code versao}) para o front pedir o aceite.
 */
public class TermoPendenteException extends RegraNegocioException {

    private final transient List<Map<String, Object>> pendentes;

    public TermoPendenteException(List<Termo> termos) {
        super(HttpStatus.FORBIDDEN, "Aceite os termos atualizados para continuar.");
        this.pendentes = termos.stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "tipo", t.getTipo(), "versao", t.getVersao()))
                .toList();
    }

    @Override
    public Map<String, Object> propriedades() {
        return Map.of("termosPendentes", pendentes);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/api/TermoPendenteInterceptor.java`:

`````java
package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.termo.application.TermoPendenteException;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bloqueia a área do aluno e a entrega de vídeo enquanto houver termo vigente sem aceite (primeiro
 * acesso ou versão nova): 403 com a lista dos termos pendentes. Perfil, sessão e as próprias rotas de
 * termos continuam liberados, para o aceite ser possível.
 */
public class TermoPendenteInterceptor implements HandlerInterceptor {

    private final TermoService termoService;

    public TermoPendenteInterceptor(TermoService termoService) {
        this.termoService = termoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            List<Termo> pendentes =
                    termoService.pendentes(Long.valueOf(jwt.getToken().getSubject()));
            if (!pendentes.isEmpty()) {
                throw new TermoPendenteException(pendentes);
            }
        }
        return true;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/api/TermoWebConfig.java`:

`````java
package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.termo.application.TermoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga o {@link TermoPendenteInterceptor} às rotas de conteúdo do aluno. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TermoWebConfig implements WebMvcConfigurer {

    private final TermoService termoService;

    public TermoWebConfig(TermoService termoService) {
        this.termoService = termoService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TermoPendenteInterceptor(termoService))
                .addPathPatterns("/api/aluno/**", "/api/aulas/**");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/api/TermoController.java`:

`````java
package br.com.celke.cursos.termo.api;

import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Termos")
public class TermoController {

    private final TermoService termoService;

    public TermoController(TermoService termoService) {
        this.termoService = termoService;
    }

    @GetMapping("/api/termos/vigentes")
    @Operation(summary = "Termos vigentes", description = "Público: a versão atual de cada termo (páginas do site).")
    public List<TermoResponse> vigentes() {
        return termoService.vigentes().stream().map(TermoResponse::de).toList();
    }

    @GetMapping("/api/termos/pendentes")
    @Operation(
            summary = "Termos a aceitar",
            description = "Versões vigentes que o usuário da sessão ainda não aceitou.")
    public List<TermoResponse> pendentes(@AuthenticationPrincipal Jwt jwt) {
        return termoService.pendentes(Long.valueOf(jwt.getSubject())).stream()
                .map(TermoResponse::de)
                .toList();
    }

    @PostMapping("/api/termos/{id}/aceite")
    @Operation(summary = "Aceitar termo", description = "Só a versão vigente; registra IP e navegador.")
    public ResponseEntity<Void> aceitar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, HttpServletRequest request) {
        termoService.aceitar(Long.valueOf(jwt.getSubject()), id, OrigemRequisicao.de(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/adm/termos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Publicar nova versão",
            description =
                    "Texto em Markdown. Todos os usuários precisam aceitar a versão nova para usar a área do aluno.")
    public ResponseEntity<TermoResponse> publicar(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Publicar request) {
        TermoResponse termo = TermoResponse.de(
                termoService.publicar(Long.valueOf(jwt.getSubject()), request.tipo(), request.conteudo()));
        return ResponseEntity.created(URI.create("/api/adm/termos?tipo=" + termo.tipo()))
                .body(termo);
    }

    @GetMapping("/api/adm/termos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Versões publicadas", description = "Da mais nova para a mais antiga.")
    public List<TermoResponse> historico(@RequestParam TipoTermo tipo) {
        return termoService.historico(tipo).stream().map(TermoResponse::de).toList();
    }

    public record Publicar(
            @NotNull TipoTermo tipo,
            @NotBlank @Size(max = 100_000) String conteudo) {}

    /** O texto sai como HTML filtrado. */
    public record TermoResponse(Long id, TipoTermo tipo, int versao, String conteudoHtml, Instant publicadoEm) {

        static TermoResponse de(Termo t) {
            return new TermoResponse(t.getId(), t.getTipo(), t.getVersao(), t.getConteudoHtml(), t.getPublicadoEm());
        }
    }
}
`````

### 6. Testes unitários

`backend/src/test/java/br/com/celke/cursos/termo/application/TermoServiceTest.java`:

`````java
package br.com.celke.cursos.termo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.termo.domain.AceiteTermo;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.9", "navegador");

    @Mock
    private TermoRepository termos;

    @Mock
    private AceiteTermoRepository aceites;

    private TermoService service;

    @BeforeEach
    void criar() {
        service = new TermoService(termos, aceites, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Termo termo(long id, TipoTermo tipo, int versao) {
        Termo termo = new Termo(tipo, versao, "Texto **v" + versao + "**", 1L, AGORA);
        ReflectionTestUtils.setField(termo, "id", id);
        return termo;
    }

    @Test
    @DisplayName("primeira publicação do tipo é a versão 1; as seguintes somam 1 à maior")
    void versoes() {
        when(termos.findFirstByTipoOrderByVersaoDesc(TipoTermo.TERMOS_USO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(termo(1, TipoTermo.TERMOS_USO, 3)));
        when(termos.saveAndFlush(any(Termo.class))).thenAnswer(i -> i.getArgument(0));

        Termo primeiro = service.publicar(1L, TipoTermo.TERMOS_USO, "  Texto <script>x</script> ");
        Termo quarto = service.publicar(1L, TipoTermo.TERMOS_USO, "Nova");

        assertThat(primeiro.getVersao()).isEqualTo(1);
        assertThat(primeiro.getConteudoHtml()).doesNotContain("<script");
        assertThat(primeiro.getPublicadoEm()).isEqualTo(AGORA);
        assertThat(quarto.getVersao()).isEqualTo(4);
    }

    @Test
    @DisplayName("publicações simultâneas: a segunda recebe 409")
    void publicacaoSimultanea() {
        when(termos.findFirstByTipoOrderByVersaoDesc(TipoTermo.POLITICA_PRIVACIDADE))
                .thenReturn(Optional.empty());
        when(termos.saveAndFlush(any(Termo.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.publicar(1L, TipoTermo.POLITICA_PRIVACIDADE, "T"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Outra versão deste termo foi publicada ao mesmo tempo. Tente de novo.");
    }

    @Test
    @DisplayName("aceite da versão vigente grava usuário, IP, navegador e instante")
    void aceita() {
        Termo vigente = termo(5, TipoTermo.TERMOS_USO, 2);
        when(termos.findById(5L)).thenReturn(Optional.of(vigente));
        when(termos.vigentes()).thenReturn(List.of(vigente));
        when(aceites.existsByUsuarioIdAndTermoId(7L, 5L)).thenReturn(false);

        service.aceitar(7L, 5L, ORIGEM);

        ArgumentCaptor<AceiteTermo> captor = ArgumentCaptor.forClass(AceiteTermo.class);
        verify(aceites).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(captor.getValue().getTermoId()).isEqualTo(5L);
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.9");
        assertThat(captor.getValue().getAceitoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("aceitar de novo não grava outro aceite; aceite simultâneo repetido é ignorado")
    void aceiteRepetido() {
        Termo vigente = termo(5, TipoTermo.TERMOS_USO, 2);
        when(termos.findById(5L)).thenReturn(Optional.of(vigente));
        when(termos.vigentes()).thenReturn(List.of(vigente));
        when(aceites.existsByUsuarioIdAndTermoId(7L, 5L)).thenReturn(true).thenReturn(false);
        when(aceites.save(any(AceiteTermo.class))).thenThrow(new DataIntegrityViolationException("uq"));

        service.aceitar(7L, 5L, ORIGEM);
        service.aceitar(7L, 5L, ORIGEM);

        verify(aceites).save(any(AceiteTermo.class));
    }

    @Test
    @DisplayName("versão antiga não é aceita (409); termo inexistente responde 404")
    void versaoAntiga() {
        Termo antigo = termo(4, TipoTermo.TERMOS_USO, 1);
        when(termos.findById(4L)).thenReturn(Optional.of(antigo));
        when(termos.findById(9L)).thenReturn(Optional.empty());
        when(termos.vigentes()).thenReturn(List.of(termo(5, TipoTermo.TERMOS_USO, 2)));

        assertThatThrownBy(() -> service.aceitar(7L, 4L, ORIGEM))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Esta versão não é mais a vigente. Aceite a versão atual.");
        assertThatThrownBy(() -> service.aceitar(7L, 9L, ORIGEM))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Termo não encontrado.");
        verify(aceites, never()).save(any());
    }

    @Test
    @DisplayName("termo pendente vira 403 com a lista dos termos")
    void excecao() {
        var excecao = new TermoPendenteException(List.of(termo(5, TipoTermo.TERMOS_USO, 2)));

        assertThat(excecao.getStatus().value()).isEqualTo(403);
        assertThat(excecao.getMessage()).isEqualTo("Aceite os termos atualizados para continuar.");
        assertThat(excecao.propriedades()).containsKey("termosPendentes");
        assertThat((List<?>) excecao.propriedades().get("termosPendentes")).hasSize(1);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/termo/api/TermoPendenteInterceptorTest.java`:

`````java
package br.com.celke.cursos.termo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.termo.application.TermoPendenteException;
import br.com.celke.cursos.termo.application.TermoService;
import br.com.celke.cursos.termo.domain.Termo;
import br.com.celke.cursos.termo.domain.TipoTermo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermoPendenteInterceptorTest {

    @Mock
    private TermoService termoService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(long usuarioId) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(String.valueOf(usuarioId))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private boolean chamar() throws Exception {
        return new TermoPendenteInterceptor(termoService)
                .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("sem termo pendente, a requisição segue")
    void semPendencia() throws Exception {
        autenticar(7);
        when(termoService.pendentes(7L)).thenReturn(List.of());

        assertThat(chamar()).isTrue();
    }

    @Test
    @DisplayName("com termo pendente, 403 com a lista")
    void comPendencia() {
        autenticar(7);
        Termo termo = new Termo(TipoTermo.TERMOS_USO, 1, "T", 1L, Instant.now());
        ReflectionTestUtils.setField(termo, "id", 5L);
        when(termoService.pendentes(7L)).thenReturn(List.of(termo));

        assertThatThrownBy(this::chamar).isInstanceOf(TermoPendenteException.class);
    }

    @Test
    @DisplayName("sem autenticação por token (a segurança já respondeu 401), não consulta termos")
    void semToken() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "k", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(chamar()).isTrue();
        verify(termoService, never()).pendentes(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/perfil/application/PerfilServiceTest.java`:

`````java
package br.com.celke.cursos.perfil.application;

import static br.com.celke.cursos.support.fixtures.UsuarioFixture.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
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

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String UPLOAD = "uploads/fotos/7/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b";

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private PerfilService service;
    private final Usuario aluno = usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    private final Usuario admin = usuario(1, "adm@celke.uno", "hash", Papel.ADMIN);

    @BeforeEach
    void criar() {
        service = new PerfilService(
                usuarioService, refreshTokenService, storage, processador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    class Senha {

        @Test
        @DisplayName("troca conferindo a atual e encerra todas as sessões")
        void troca() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "senha-atual-123")).thenReturn(true);
            when(usuarioService.senhaConfere(aluno, "senha-nova-456")).thenReturn(false);

            service.trocarSenha(7L, "senha-atual-123", "senha-nova-456");

            verify(usuarioService).trocarSenha(aluno, "senha-nova-456");
            verify(refreshTokenService).revogarTodas(7L);
        }

        @Test
        @DisplayName("senha atual errada: 400 e nada muda")
        void atualErrada() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "errada")).thenReturn(false);

            assertThatThrownBy(() -> service.trocarSenha(7L, "errada", "senha-nova-456"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A senha atual não confere.");
            verify(usuarioService, never()).trocarSenha(any(), anyString());
            verify(refreshTokenService, never()).revogarTodas(any());
        }

        @Test
        @DisplayName("nova senha curta para o papel: aluno 10, admin 12")
        void politicaPorPapel() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.buscarPorId(1L)).thenReturn(Optional.of(admin));
            when(usuarioService.senhaConfere(any(), eq("atual"))).thenReturn(true);

            assertThatThrownBy(() -> service.trocarSenha(7L, "atual", "123456789"))
                    .hasMessage("A senha deve ter pelo menos 10 caracteres.");
            assertThatThrownBy(() -> service.trocarSenha(1L, "atual", "12345678901"))
                    .hasMessage("A senha deve ter pelo menos 12 caracteres.");
            verify(usuarioService, never()).trocarSenha(any(), anyString());
        }

        @Test
        @DisplayName("nova senha igual à atual é recusada")
        void igual() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.senhaConfere(aluno, "mesma-senha-123")).thenReturn(true);

            assertThatThrownBy(() -> service.trocarSenha(7L, "mesma-senha-123", "mesma-senha-123"))
                    .hasMessage("A nova senha precisa ser diferente da atual.");
        }

        @Test
        @DisplayName("usuário do token que não existe mais: sessão inválida")
        void semUsuario() {
            when(usuarioService.buscarPorId(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscar(9L))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Sessão inválida. Entre novamente.");
        }
    }

    @Nested
    class Foto {

        @Test
        @DisplayName("envio em uploads/fotos/<usuario>/<uuid> para JPEG ou PNG até 5 MB")
        void iniciar() throws Exception {
            when(storage.urlUpload(startsWith("uploads/fotos/7/"), eq("image/jpeg"), eq(Duration.ofMinutes(10))))
                    .thenReturn(URI.create("http://media.localhost/put").toURL());

            assertThat(service.iniciarEnvioFoto(7L, "image/jpeg", PerfilService.TAMANHO_MAXIMO_FOTO)
                            .chave())
                    .matches("uploads/fotos/7/[0-9a-f-]{36}");
            assertThatThrownBy(() -> service.iniciarEnvioFoto(7L, "image/gif", 1))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envie uma imagem JPEG ou PNG.");
            assertThatThrownBy(() -> service.iniciarEnvioFoto(7L, "image/png", PerfilService.TAMANHO_MAXIMO_FOTO + 1))
                    .hasMessage("A foto pode ter no máximo 5 MB.");
        }

        @Test
        @DisplayName("confirma: regrava 400 × 400 em JPEG, apaga o envio e a foto anterior")
        void confirma() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(storage.info(UPLOAD)).thenReturn(Optional.of(new InfoArquivo(100, "image/png")));
            when(storage.baixar(UPLOAD)).thenReturn(new byte[] {1});
            when(processador.normalizar(new byte[] {1}, 400, 400)).thenReturn(new byte[] {9});
            when(usuarioService.trocarFoto(eq(7L), startsWith("fotos/7/")))
                    .thenReturn(Optional.of("fotos/7/antiga.jpg"));

            service.confirmarFoto(7L, UPLOAD);

            verify(storage).gravar(startsWith("fotos/7/"), eq(new byte[] {9}), eq("image/jpeg"));
            verify(storage).excluir(UPLOAD);
            verify(storage).excluir("fotos/7/antiga.jpg");
        }

        @Test
        @DisplayName("IDOR: chave enviada por outro usuário é recusada sem tocar no bucket")
        void chaveDeOutro() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));

            assertThatThrownBy(() -> service.confirmarFoto(7L, "uploads/fotos/8/0b6b6b6e-1c1d-4e4f-8a8b-9c9d0e0f1a1b"))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envio de foto inválido. Comece um novo envio.");
            verify(storage, never()).info(anyString());
        }

        @Test
        @DisplayName("remover apaga a foto atual")
        void remover() {
            when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(aluno));
            when(usuarioService.trocarFoto(7L, null)).thenReturn(Optional.of("fotos/7/x.jpg"));

            service.removerFoto(7L);

            verify(storage).excluir("fotos/7/x.jpg");
        }

        @Test
        @DisplayName("URL da foto só quando há foto")
        void url() throws Exception {
            assertThat(service.urlFoto(aluno)).isEmpty();
            aluno.trocarFoto("fotos/7/x.jpg", AGORA);
            when(storage.urlDownload("fotos/7/x.jpg", Duration.ofHours(1)))
                    .thenReturn(URI.create("http://media.localhost/x").toURL());

            assertThat(service.urlFoto(aluno)).isPresent();
        }
    }
}
`````

### 7. Testes de integração

`backend/src/test/java/br/com/celke/cursos/perfil/PerfilIT.java`:

`````java
package br.com.celke.cursos.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: perfil do próprio usuário — dados sem senha nem hash, troca de nome, troca de senha (atual
 * errada, política, igual à atual, sucesso encerrando as sessões) e foto (reduzida, sem EXIF, troca e
 * remoção; chave de outro usuário recusada); anônimo 401.
 * Não cobre: regras caso a caso (PerfilServiceTest).
 * Dado criado: usuários próprios (o seed é só leitura), fotos em uploads/fotos/ e fotos/.
 */
class PerfilIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private UsuarioRepository usuarios;

    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarUsuario() {
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    private MvcTestResult chamar(HttpMethod metodo, String uri, String corpo, String tokenUsado) {
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private String enviarFoto(byte[] imagem, String contentType, String tokenUsado) throws Exception {
        String corpo = corpo(chamar(
                HttpMethod.POST,
                "/api/perfil/foto/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, imagem.length),
                tokenUsado));
        var put = http.send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(corpo, "$.url")))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(imagem))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    private static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("mostra e atualiza o nome, sem expor senha nem hash")
    void dados() {
        MvcTestResult perfil = chamar(HttpMethod.GET, "/api/perfil", null, token);
        assertThat(perfil).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"email":"%s","papeis":["ALUNO"],"fotoUrl":null}
                """.formatted(aluno.email()));
        assertThat(corpo(perfil)).doesNotContain("senha").doesNotContain("hash").doesNotContain("$argon2");

        assertThat(chamar(HttpMethod.PUT, "/api/perfil", "{\"nome\":\"  Ana Paula  \"}", token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.nome")
                .isEqualTo("Ana Paula");
        assertThat(chamar(HttpMethod.PUT, "/api/perfil", "{\"nome\":\" \"}", token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("nome"));
    }

    @Test
    @DisplayName("troca de senha: recusa a atual errada, a curta e a igual; troca e encerra as sessões")
    void trocaDeSenha() {
        String rota = "/api/perfil/senha";
        Cookie refresh = entrar(aluno.email(), aluno.senha()).getResponse().getCookie("refresh_token");

        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"errada-123456\",\"novaSenha\":\"nova-senha-forte\"}",
                        token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A senha atual não confere.");
        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"%s\",\"novaSenha\":\"curta\"}".formatted(aluno.senha()),
                        token))
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A senha deve ter pelo menos 10 caracteres.");
        assertThat(chamar(
                        HttpMethod.PUT,
                        rota,
                        "{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(aluno.senha(), aluno.senha()),
                        token))
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A nova senha precisa ser diferente da atual.");

        MvcTestResult trocada = chamar(
                HttpMethod.PUT,
                rota,
                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"nova-senha-forte\"}".formatted(aluno.senha()),
                token);
        assertThat(trocada).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(trocada.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=")
                .contains("Max-Age=0");

        // A sessão antiga não renova mais; a senha nova entra
        assertThat(mvc.post()
                        .uri("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(refresh)
                        .exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        entrar(aluno.email(), "nova-senha-forte");
    }

    @Test
    @DisplayName("foto: reduzida e sem EXIF, trocada (apaga a anterior) e removida")
    void foto() throws Exception {
        String envio = enviarFoto(ImagemFixture.jpegComExif(1200, 900, "GPS -23.5505"), "image/jpeg", token);
        MvcTestResult confirmada =
                chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(envio), token);
        assertThat(confirmada).hasStatusOk();
        assertThat(storage.info(envio)).as("envio apagado").isEmpty();
        String primeira = usuarios.findById(aluno.id()).orElseThrow().getFotoChave();
        assertThat(primeira).matches("fotos/" + aluno.id() + "/[0-9a-f-]{36}\\.jpg");
        byte[] gravada = storage.baixar(primeira);
        var imagem = ImageIO.read(new ByteArrayInputStream(gravada));
        assertThat(imagem.getWidth()).isLessThanOrEqualTo(400);
        assertThat(imagem.getHeight()).isLessThanOrEqualTo(400);
        assertThat(new String(gravada, StandardCharsets.ISO_8859_1)).doesNotContain("GPS -23.5505");
        String url = JsonPath.read(corpo(confirmada), "$.fotoUrl");
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);

        String segunda = enviarFoto(ImagemFixture.png(50, 50), "image/png", token);
        assertThat(chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(segunda), token))
                .hasStatusOk();
        assertThat(storage.info(primeira)).as("foto anterior apagada").isEmpty();

        assertThat(chamar(HttpMethod.DELETE, "/api/perfil/foto", null, token)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, token))
                .bodyJson()
                .extractingPath("$.fotoUrl")
                .isNull();
    }

    @Test
    @DisplayName("foto enviada por outro usuário não vira a foto deste (IDOR)")
    void fotoDeOutro() throws Exception {
        UsuarioTeste outro = criarUsuario(Papel.ALUNO);
        String alheia = enviarFoto(ImagemFixture.png(20, 20), "image/png", login(outro.email(), outro.senha()));

        assertThat(chamar(HttpMethod.PUT, "/api/perfil/foto", "{\"chave\":\"%s\"}".formatted(alheia), token))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de foto inválido. Comece um novo envio.");
        assertThat(storage.info(alheia)).isPresent();
    }

    @Test
    @DisplayName("rotas do perfil exigem token")
    void anonimo() {
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.PUT, "/api/perfil/senha", "{}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/perfil/foto/envio", "{}", null))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/termo/TermoIT.java`:

`````java
package br.com.celke.cursos.termo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.CursoITHelper;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.termo.infra.AceiteTermoRepository;
import br.com.celke.cursos.termo.infra.TermoRepository;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: termos versionados — publicação pelo ADM (versões, HTML filtrado, matriz de papéis), vigentes
 * públicos, pendentes do usuário, bloqueio da área do aluno e da entrega de vídeo até o aceite (403 com
 * a lista), aceite (com IP), versão antiga recusada, perfil e sessão liberados durante a pendência.
 * Não cobre: regras caso a caso (TermoServiceTest, TermoPendenteInterceptorTest).
 * Dado criado: termos e aceites — apagados ao fim de cada teste, porque um termo vigente bloquearia os
 * alunos das outras classes de teste, que compartilham o banco.
 */
class TermoIT extends IntegrationTest {

    @Autowired
    private TermoRepository termos;

    @Autowired
    private AceiteTermoRepository aceites;

    private String tokenAdmin;
    private UsuarioTeste aluno;
    private String token;

    @BeforeEach
    void criarAluno() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        aluno = criarUsuario(Papel.ALUNO);
        token = login(aluno.email(), aluno.senha());
    }

    @AfterEach
    void apagarTermos() {
        aceites.deleteAll();
        termos.deleteAll();
    }

    private MvcTestResult chamar(HttpMethod metodo, String uri, String corpo, String tokenUsado) {
        var requisicao = mvc.method(metodo).uri(uri);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        if (tokenUsado != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(tokenUsado));
        }
        return requisicao.exchange();
    }

    private long publicar(String tipo, String conteudo) {
        return CursoITHelper.idCriado(chamar(
                HttpMethod.POST,
                "/api/adm/termos",
                "{\"tipo\":\"%s\",\"conteudo\":\"%s\"}".formatted(tipo, conteudo),
                tokenAdmin));
    }

    @Test
    @DisplayName("termo novo bloqueia a área do aluno até o aceite; aceite libera; versão nova bloqueia de novo")
    void bloqueioAteOAceite() {
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token))
                .as("sem termos, livre")
                .hasStatusOk();

        long uso = publicar("TERMOS_USO", "Uso **v1** <script>x</script>");
        long privacidade = publicar("POLITICA_PRIVACIDADE", "Privacidade v1");

        MvcTestResult bloqueado = chamar(HttpMethod.GET, "/api/aluno/cursos", null, token);
        assertThat(bloqueado).hasStatus(HttpStatus.FORBIDDEN);
        var json = assertThat(bloqueado).bodyJson();
        json.extractingPath("$.detail").isEqualTo("Aceite os termos atualizados para continuar.");
        json.extractingPath("$.termosPendentes[*].id").isEqualTo(List.of((int) privacidade, (int) uso));
        assertThat(chamar(HttpMethod.GET, "/api/aulas/1/video.m3u8", null, token))
                .hasStatus(HttpStatus.FORBIDDEN);

        // Perfil, sessão e as rotas de termos continuam livres para o aceite ser possível
        assertThat(chamar(HttpMethod.GET, "/api/perfil", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/auth/me", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/termos/pendentes", null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].conteudoHtml")
                .asString()
                .doesNotContain("<script");

        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token))
                .as("ainda falta a privacidade")
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + privacidade + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatusOk();
        assertThat(aceites.existsByUsuarioIdAndTermoId(aluno.id(), uso)).isTrue();

        // Versão 2 dos termos de uso: bloqueia de novo; a versão 1 não pode mais ser aceita
        long uso2 = publicar("TERMOS_USO", "Uso v2");
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso + "/aceite", null, token))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Esta versão não é mais a vigente. Aceite a versão atual.");
        assertThat(chamar(HttpMethod.POST, "/api/termos/" + uso2 + "/aceite", null, token))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(chamar(HttpMethod.GET, "/api/aluno/cursos", null, token)).hasStatusOk();
    }

    @Test
    @DisplayName("vigentes são públicos; o histórico do ADM lista as versões da mais nova para a mais antiga")
    void vigentesEHistorico() {
        publicar("TERMOS_USO", "v1");
        publicar("TERMOS_USO", "v2");

        assertThat(chamar(HttpMethod.GET, "/api/termos/vigentes", null, null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].versao")
                .isEqualTo(List.of(2));
        String historico = corpo(chamar(HttpMethod.GET, "/api/adm/termos?tipo=TERMOS_USO", null, tokenAdmin));
        assertThat(JsonPath.<List<Integer>>read(historico, "$[*].versao")).containsExactly(2, 1);
    }

    @Test
    @DisplayName("publicar e listar versões é só do ADM; aceitar exige token; validação 400")
    void acesso() {
        String corpo = "{\"tipo\":\"TERMOS_USO\",\"conteudo\":\"x\"}";

        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", corpo, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", corpo, token)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, "/api/adm/termos?tipo=TERMOS_USO", null, token))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(chamar(HttpMethod.GET, "/api/termos/pendentes", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/termos/1/aceite", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.POST, "/api/adm/termos", "{\"conteudo\":\" \"}", tokenAdmin))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .isEqualTo(List.of("conteudo", "tipo"));
        assertThat(chamar(HttpMethod.POST, "/api/termos/999999999/aceite", null, token))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static String corpo(MvcTestResult resposta) {
        return CursoITHelper.corpo(resposta);
    }
}
`````

### 8. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/perfil/PerfilTermosE2E.java`:

`````java
package br.com.celke.cursos.e2e.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.celke.cursos.e2e.support.Adm;
import br.com.celke.cursos.e2e.support.Api;
import br.com.celke.cursos.e2e.support.Contas;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Jornada: o ADM publica uma versão nova dos termos de uso e a área do aluno fica bloqueada até o aceite;
 * depois o aluno atualiza o perfil e troca a senha, encerrando a sessão antiga.
 */
class PerfilTermosE2E {

    @Test
    @DisplayName("novo termo publicado → rotas do aluno bloqueadas até o aceite; perfil e troca de senha")
    void termosEPerfil() {
        Adm adm = Adm.doSeed();
        String email = Contas.criarConfirmada();
        String token = Api.login(email, Contas.SENHA).jsonPath().getString("accessToken");
        aluno(token).get("/api/aluno/cursos").then().statusCode(200);

        // 1. Versão nova dos termos de uso: a área do aluno responde 403 com o termo pendente
        long termo = adm.criar(
                "/api/adm/termos", "{\"tipo\":\"TERMOS_USO\",\"conteudo\":\"Termos de uso **atualizados**.\"}");
        aluno(token)
                .get("/api/aluno/cursos")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Aceite os termos atualizados para continuar."))
                .body("termosPendentes.id", equalTo(List.of((int) termo)));
        Api.site("aluno.localhost")
                .get("/api/termos/vigentes")
                .then()
                .statusCode(200)
                .body(
                        "find { it.id == %d }.conteudoHtml".formatted(termo),
                        equalTo("<p>Termos de uso <strong>atualizados</strong>.</p>"));

        // 2. Aceite libera
        aluno(token).post("/api/termos/" + termo + "/aceite").then().statusCode(204);
        aluno(token).get("/api/aluno/cursos").then().statusCode(200);

        // 3. Perfil: nome novo
        aluno(token)
                .body("{\"nome\":\"Aluna Renomeada\"}")
                .put("/api/perfil")
                .then()
                .statusCode(200)
                .body("nome", equalTo("Aluna Renomeada"));

        // 4. Troca de senha encerra a sessão: o refresh antigo não vale mais, a senha nova entra
        var sessao = Api.loginSemAceite(email, Contas.SENHA);
        String refresh = sessao.getCookie("refresh_token");
        aluno(token)
                .body("{\"senhaAtual\":\"%s\",\"novaSenha\":\"senha-nova-da-jornada\"}".formatted(Contas.SENHA))
                .put("/api/perfil/senha")
                .then()
                .statusCode(204);
        Api.aluno()
                .header("Origin", "http://aluno.localhost:8088")
                .cookie("refresh_token", refresh)
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
        assertThat(Api.loginSemAceite(email, "senha-nova-da-jornada").jsonPath().getString("accessToken"))
                .isNotBlank();
    }

    private static RequestSpecification aluno(String token) {
        return Api.aluno().header("Authorization", Api.bearer(token));
    }
}
`````

## Antes de executar

Fase 8 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/perfil-termos
```

## Verificação

Dentro de `backend/` (Docker rodando e `ffmpeg` no PATH):

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 720 testes unitários e 353 de integração, e a cobertura mínima atendida.

Na raiz do repositório, as jornadas e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 18 jornadas passam, entre elas a `PerfilTermosE2E`.

No ambiente local, pelo Swagger:

- Como admin, publique em `POST /api/adm/termos` uma versão dos termos de uso; como aluno,
  `GET /api/aluno/cursos` responde 403 com `termosPendentes` até o `POST /api/termos/{id}/aceite`.
- Troque a senha em `PUT /api/perfil/senha`: o refresh da sessão antiga passa a responder 401.

## Depois de executar

```bash
git add backend/ infra/ docs/
git commit -m "feat(perfil): adicionar perfil e termos"
git push -u origin feature/perfil-termos
```

Abra o Pull Request de `feature/perfil-termos` para `homolog`.

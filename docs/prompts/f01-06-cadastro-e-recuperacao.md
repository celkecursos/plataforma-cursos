# Cadastro, confirmação de e-mail e recuperação de senha

**Fase:** 1 — etapa 1.6
**Branch:** `feature/base-projeto`

## Prompt

Vamos permitir que o aluno crie a conta, confirme o e-mail e recupere a senha sozinho, seguindo o
`backend/CLAUDE.md` e o padrão das skills de teste. Não faça commit.

O que esta etapa entrega:

- **Confirmação de e-mail**: coluna `usuarios.email_confirmado_em` (contas que já existem ficam
  confirmadas). Contas do comando `criar-admin` e dos seeds nascem confirmadas (`criarConfirmado`); o
  cadastro do aluno nasce pendente (`cadastrarAluno`). Login com a senha certa e e-mail pendente recebe 403
  "Confirme o seu e-mail antes de entrar..." e fica na auditoria como `EMAIL_NAO_CONFIRMADO`; com a senha
  errada, continua a mensagem genérica.
- **Tokens de uso único** (`tokens_uso_unico`): 32 bytes aleatórios, só o hash SHA-256 no banco
  (`TokenSeguro`, também usado pelo refresh). Um token novo invalida os anteriores do mesmo tipo; cada
  conta recebe no máximo 3 e-mails por hora por tipo. Confirmação vale 48 h, recuperação vale 1 h.
- **Rotas públicas (POST, com limite por IP)** em `/api/auth`: `cadastro` (202), `confirmar-email`,
  `reenviar-confirmacao` (202), `recuperar-senha` (202) e `redefinir-senha`. As que recebem um e-mail
  respondem sempre o mesmo, exista ou não a conta: e-mail novo cria a conta pendente; cadastro pendente
  recebe o link de novo; conta ativa recebe o aviso "Tentativa de cadastro com o seu e-mail" (no máximo
  um por hora). Link inválido, usado ou expirado responde 400 "Link inválido ou expirado. Peça um novo.".
- **Redefinir a senha** troca a senha, confirma o e-mail (o link chegou à caixa do dono) e encerra todas
  as sessões.
- **Senha de aluno** com 10 a 128 caracteres.
- **E-mails** com templates Thymeleaf em `templates/email/`, links para `app.conta.url-aluno`
  (`FRONT_ALUNO_URL`), enviados pela fila e entregues pelo worker.
- **Healthcheck do worker** por arquivo de batimento: `BatimentoWorker` regrava `/tmp/worker-batimento`
  a cada 10 s (separado do consumo da fila) e o compose confere se ele mudou no último minuto.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

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
`````

`backend/src/test/resources/application-test.yaml`:

`````yaml
# Profile dos testes de integração. Endereços dos containers vêm de support/Containers.
spring:
  mail:
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
      mail.smtp.starttls.required: false

app:
  auth:
    # Chave só dos testes (48 bytes em Base64)
    jwt-segredo: dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUt
    origens-permitidas: http://localhost:5173
  conta:
    url-aluno: http://localhost:5173
  limites:
    # Todas as requisições da suíte saem do mesmo IP: o limite por IP fica fora do caminho
    requisicoes-por-ip-por-minuto: 100000
  armazenamento:
    cors-origens: http://localhost:5173
  email:
    remetente: nao-responda@teste.celke.uno
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

`backend/src/main/java/br/com/celke/cursos/auth/api/LimitePorIpFilter.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.LimitesProperties;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.erro.ProblemDetailJson;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requisições por IP nas rotas de autenticação (token bucket do Bucket4j). Conta toda
 * requisição, até as inválidas, e roda antes da segurança e do Spring MVC: o excesso é barrado
 * sem calcular hash nem consultar o banco.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LimitePorIpFilter extends OncePerRequestFilter {

    /** Rotas limitadas (sempre POST). */
    static final Set<String> ROTAS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/cadastro",
            "/api/auth/confirmar-email",
            "/api/auth/reenviar-confirmacao",
            "/api/auth/recuperar-senha",
            "/api/auth/redefinir-senha");

    static final String CABECALHO_RESTANTE = "X-RateLimit-Remaining";

    private static final Logger LOG = LoggerFactory.getLogger(LimitePorIpFilter.class);

    private final int limitePorMinuto;
    private final JsonMapper jsonMapper;
    private final TimeMeter relogio;
    private final Cache<String, Bucket> baldes = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public LimitePorIpFilter(LimitesProperties props, JsonMapper jsonMapper, Clock clock) {
        this.limitePorMinuto = props.requisicoesPorIpPorMinuto();
        this.jsonMapper = jsonMapper;
        this.relogio = new RelogioBucket(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !ROTAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        ConsumptionProbe consumo = baldes.get(ip, chave -> novoBalde()).tryConsumeAndReturnRemaining(1);
        response.setHeader(CABECALHO_RESTANTE, String.valueOf(consumo.getRemainingTokens()));
        if (consumo.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        MuitasTentativasException limite =
                new MuitasTentativasException(Duration.ofNanos(consumo.getNanosToWaitForRefill()));
        LOG.warn("Limite de requisições por IP atingido em {}", request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosDeEspera()));
        ProblemDetailJson.escrever(jsonMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, limite.getMessage());
    }

    private Bucket novoBalde() {
        return Bucket.builder()
                .addLimit(
                        limite -> limite.capacity(limitePorMinuto).refillGreedy(limitePorMinuto, Duration.ofMinutes(1)))
                .withCustomTimePrecision(relogio)
                .build();
    }

    /** Relógio do Bucket4j a partir do {@link Clock} da aplicação (testável). */
    record RelogioBucket(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.millis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
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

`backend/src/main/java/br/com/celke/cursos/usuario/domain/PoliticaSenha.java`:

`````java
package br.com.celke.cursos.usuario.domain;

/**
 * Regras de senha: tamanho mínimo e máximo (o máximo evita hash de textos enormes). Não exige
 * mistura de caracteres: comprimento protege mais e incomoda menos.
 */
public final class PoliticaSenha {

    public static final int MINIMO_ALUNO = 10;
    public static final int MINIMO_ADMIN = 12;
    public static final int MAXIMO = 128;

    private PoliticaSenha() {}

    /** Devolve a mensagem do problema, ou {@code null} se a senha atende à política. */
    public static String validar(String senha, int minimo) {
        if (senha == null || senha.isBlank()) {
            return "A senha é obrigatória.";
        }
        if (senha.length() < minimo) {
            return "A senha deve ter pelo menos " + minimo + " caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha deve ter no máximo " + MAXIMO + " caracteres.";
        }
        return null;
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
import java.util.Optional;
import java.util.Set;
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

`backend/src/main/java/br/com/celke/cursos/usuario/application/CriarAdminService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Cria um administrador. Usado pelo comando {@code criar-admin}, nunca por migration. */
@Service
public class CriarAdminService {

    private final UsuarioService usuarioService;

    public CriarAdminService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    public Usuario criar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        String problemaSenha = PoliticaSenha.validar(senha, PoliticaSenha.MINIMO_ADMIN);
        if (problemaSenha != null) {
            throw new IllegalArgumentException(problemaSenha);
        }
        return usuarioService.criarConfirmado(nome, email, senha, Set.of(Papel.ADMIN));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/ResultadoLogin.java`:

`````java
package br.com.celke.cursos.auth.domain;

/**
 * Resultado registrado na auditoria. Nas falhas, o cliente só recebe "e-mail ou senha inválidos";
 * {@code EMAIL_NAO_CONFIRMADO} só acontece com a senha certa.
 */
public enum ResultadoLogin {
    SUCESSO,
    EMAIL_DESCONHECIDO,
    SENHA_INVALIDA,
    USUARIO_INATIVO,
    EMAIL_NAO_CONFIRMADO,
    BLOQUEADO
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuthService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login (com bloqueio por falhas seguidas e auditoria), renovação e saída. */
@Service
public class AuthService {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioLoginService bloqueioLoginService;
    private final AuditoriaLoginService auditoriaLoginService;

    /**
     * Hash de uma senha aleatória: conferido quando o e-mail não existe, para o login levar o mesmo
     * tempo nos dois casos e não revelar quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioService usuarioService,
            PasswordEncoder passwordEncoder,
            TokenAcessoService tokenAcessoService,
            RefreshTokenService refreshTokenService,
            BloqueioLoginService bloqueioLoginService,
            AuditoriaLoginService auditoriaLoginService) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueioLoginService = bloqueioLoginService;
        this.auditoriaLoginService = auditoriaLoginService;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Confere o bloqueio antes de tudo (sem calcular hash), depois a senha. Falha e sucesso
     * atualizam os contadores de bloqueio e ficam na auditoria. Toda falha recebe a mesma mensagem;
     * só quem acertou a senha fica sabendo que falta confirmar o e-mail.
     */
    @Transactional
    public Sessao entrar(String email, String senha, OrigemRequisicao origem) {
        try {
            bloqueioLoginService.verificar(email, origem.ip());
        } catch (MuitasTentativasException e) {
            auditoriaLoginService.registrar(email, null, ResultadoLogin.BLOQUEADO, origem);
            throw e;
        }
        Optional<Usuario> encontrado = usuarioService.buscarPorEmail(email);
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        ResultadoLogin resultado = resultado(encontrado, senhaConfere);
        auditoriaLoginService.registrar(email, encontrado.orElse(null), resultado, origem);
        if (resultado == ResultadoLogin.EMAIL_NAO_CONFIRMADO) {
            // Quem chegou aqui sabe a senha: pode saber que falta confirmar o e-mail
            bloqueioLoginService.registrarSucesso(email, origem.ip());
            throw new EmailNaoConfirmadoException();
        }
        if (resultado != ResultadoLogin.SUCESSO) {
            bloqueioLoginService.registrarFalha(email, origem.ip());
            throw new CredenciaisInvalidasException();
        }
        bloqueioLoginService.registrarSucesso(email, origem.ip());
        Usuario usuario = encontrado.get();
        usuarioService.atualizarHashSeNecessario(usuario, senha);
        return abrirSessao(usuario, refreshTokenService.emitir(usuario.getId()));
    }

    private static ResultadoLogin resultado(Optional<Usuario> encontrado, boolean senhaConfere) {
        if (encontrado.isEmpty()) {
            return ResultadoLogin.EMAIL_DESCONHECIDO;
        }
        if (!senhaConfere) {
            return ResultadoLogin.SENHA_INVALIDA;
        }
        if (!encontrado.get().isAtivo()) {
            return ResultadoLogin.USUARIO_INATIVO;
        }
        return encontrado.get().isEmailConfirmado() ? ResultadoLogin.SUCESSO : ResultadoLogin.EMAIL_NAO_CONFIRMADO;
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. Sem transação própria: a
     * revogação de uma família por reutilização precisa ficar gravada mesmo com a sessão recusada.
     */
    public Sessao renovar(String refreshToken) {
        RefreshEmitido novo = refreshTokenService.rotacionar(refreshToken).orElseThrow(SessaoInvalidaException::new);
        Usuario usuario = usuarioService
                .buscarPorId(novo.usuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(SessaoInvalidaException::new);
        return abrirSessao(usuario, novo);
    }

    public void sair(String refreshToken) {
        refreshTokenService.revogarFamiliaDoToken(refreshToken);
    }

    public void sairDeTodos(Long usuarioId) {
        refreshTokenService.revogarTodas(usuarioId);
    }

    private Sessao abrirSessao(Usuario usuario, RefreshEmitido refresh) {
        return new Sessao(usuario, tokenAcessoService.emitir(usuario), refresh);
    }

    public record Sessao(Usuario usuario, TokenAcesso acesso, RefreshEmitido refresh) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/RefreshTokenService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens opacos com rotação: cada uso marca o token como usado e emite outro na mesma
 * família. Um token já usado que reaparece indica roubo; a família inteira é revogada.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final AuthProperties props;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Abre uma família nova (login). Devolve o token em texto, que só existe no cookie. */
    @Transactional
    public RefreshEmitido emitir(Long usuarioId) {
        return emitirNaFamilia(usuarioId, UUID.randomUUID(), clock.instant());
    }

    /**
     * Troca o token por um novo da mesma família. Vazio quando o token é desconhecido, expirado,
     * revogado ou reutilizado (neste caso a família é revogada).
     */
    @Transactional
    public Optional<RefreshEmitido> rotacionar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        Optional<RefreshToken> encontrado = repository.findByHash(TokenSeguro.hash(token));
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken atual = encontrado.get();
        if (atual.estaRevogado() || atual.estaExpirado(agora)) {
            return Optional.empty();
        }
        if (atual.foiUsado()) {
            LOG.warn(
                    "Refresh token reutilizado: família {} do usuário {} revogada",
                    atual.getFamilia(),
                    atual.getUsuarioId());
            repository.revogarFamilia(atual.getFamilia(), agora);
            return Optional.empty();
        }
        atual.marcarUsado(agora);
        return Optional.of(emitirNaFamilia(atual.getUsuarioId(), atual.getFamilia(), agora));
    }

    /** Logout: revoga a família do token (se ele existir). */
    @Transactional
    public void revogarFamiliaDoToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        repository
                .findByHash(TokenSeguro.hash(token))
                .ifPresent(t -> repository.revogarFamilia(t.getFamilia(), clock.instant()));
    }

    /** "Sair de todos os dispositivos": revoga todas as famílias do usuário. */
    @Transactional
    public void revogarTodas(Long usuarioId) {
        repository.revogarTodasDoUsuario(usuarioId, clock.instant());
    }

    private RefreshEmitido emitirNaFamilia(Long usuarioId, UUID familia, Instant agora) {
        String token = TokenSeguro.gerar();
        Instant expiraEm = agora.plus(props.validadeRefresh());
        repository.save(new RefreshToken(usuarioId, familia, TokenSeguro.hash(token), expiraEm, agora));
        return new RefreshEmitido(token, usuarioId, expiraEm);
    }

    public record RefreshEmitido(String token, Long usuarioId, Instant expiraEm) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/support/IntegrationTest.java`:

`````java
package br.com.celke.cursos.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.shared.fila.ProcessadorFila;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Base de todos os testes de integração (*IT). Não altere para atender um teste específico:
 * qualquer variação cria outro contexto Spring e deixa a suíte lenta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Containers.class)
public abstract class IntegrationTest {

    /** Senha dos usuários criados por {@link #criarUsuario(Papel...)}. */
    protected static final String SENHA_TESTE = "senha-do-usuario-de-teste";

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcessadorFila processadorFila;

    /** Identificador único em runtime para os dados criados pelo teste (prefixo {@code it-}). */
    protected static String stamp() {
        return "it-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
    }

    /** Faz login, confere o 200 e devolve a resposta (corpo com o access token e cookie de refresh). */
    protected MvcTestResult entrar(String email, String senha) {
        MvcTestResult resposta = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
        assertThat(resposta).hasStatusOk();
        return resposta;
    }

    /** Faz login e devolve o access token. */
    protected String login(String email, String senha) {
        return accessToken(entrar(email, senha));
    }

    protected static String accessToken(MvcTestResult resposta) {
        try {
            return JsonPath.read(resposta.getResponse().getContentAsString(), "$.accessToken");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Processa todos os jobs pendentes (no worker isso é feito pelo consumo agendado). */
    protected void processarFila() {
        while (processadorFila.processarProximo()) {
            // continua enquanto houver job disponível
        }
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Cria um usuário próprio do teste (e-mail com {@code stamp}), para fluxos que alteram a sessão
     * ou os dados do usuário — os usuários do seed são só leitura.
     */
    protected UsuarioTeste criarUsuario(Papel... papeis) {
        String email = stamp() + "@teste.celke.uno";
        var usuario = usuarioService.criarConfirmado("Usuário " + email, email, SENHA_TESTE, Set.of(papeis));
        return new UsuarioTeste(usuario.getId(), email, SENHA_TESTE);
    }

    protected record UsuarioTeste(Long id, String email, String senha) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/support/Mailpit.java`:

`````java
package br.com.celke.cursos.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;

/** Consulta os e-mails capturados pelo Mailpit (API HTTP do container). */
public class Mailpit {

    private static final Pattern TOKEN_NO_LINK = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private final RestClient http;

    public Mailpit(String urlBase) {
        this.http = RestClient.create(urlBase);
    }

    /** Espera chegar um e-mail para o destinatário e devolve o mais recente. */
    public EmailRecebido aguardarEmailPara(String destinatario) {
        String id = await("e-mail para " + destinatario)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> idMaisRecente(destinatario), resultado -> resultado != null);
        return http.get().uri("/api/v1/message/{id}", id).retrieve().body(EmailRecebido.class);
    }

    /** Quantos e-mails o destinatário recebeu até agora. */
    public int quantidadePara(String destinatario) {
        Busca busca = buscar(destinatario);
        return busca == null ? 0 : busca.messages().size();
    }

    /** Token do link no HTML de um e-mail ({@code ...?token=<valor>}). */
    public static String tokenDoLink(EmailRecebido email) {
        Matcher matcher = TOKEN_NO_LINK.matcher(email.HTML());
        if (!matcher.find()) {
            throw new AssertionError("O e-mail não tem link com token: " + email.Subject());
        }
        return matcher.group(1);
    }

    private Busca buscar(String destinatario) {
        return http.get()
                .uri(u -> u.path("/api/v1/search")
                        .queryParam("query", "to:\"" + destinatario + "\"")
                        .build())
                .retrieve()
                .body(Busca.class);
    }

    private String idMaisRecente(String destinatario) {
        Busca busca = buscar(destinatario);
        return busca == null || busca.messages().isEmpty()
                ? null
                : busca.messages().getFirst().ID();
    }

    record Busca(List<Resumo> messages) {}

    record Resumo(String ID) {}

    public record Endereco(String Name, String Address) {}

    /** Campos do e-mail na API do Mailpit (nomes em PascalCase, como a API devolve). */
    public record EmailRecebido(String Subject, Endereco From, List<Endereco> To, String HTML, String Text) {}
}
`````

`backend/src/test/java/br/com/celke/cursos/support/SeedTeste.java`:

`````java
package br.com.celke.cursos.support;

import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seed do profile {@code test}: um admin e um aluno, com as credenciais de {@link UsuariosSeed}. */
@Component
@Profile("test")
class SeedTeste implements ApplicationRunner {

    private final UsuarioService usuarioService;

    SeedTeste(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExiste("Admin do Seed", UsuariosSeed.ADMIN_EMAIL, Papel.ADMIN);
        criarSeNaoExiste("Aluno do Seed", UsuariosSeed.ALUNO_EMAIL, Papel.ALUNO);
    }

    private void criarSeNaoExiste(String nome, String email, Papel papel) {
        if (usuarioService.buscarPorEmail(email).isEmpty()) {
            usuarioService.criarConfirmado(nome, email, UsuariosSeed.SENHA, Set.of(papel));
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/fixtures/UsuarioFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Instant;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de usuários para testes unitários (sem banco: o id é preenchido aqui). */
public final class UsuarioFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private UsuarioFixture() {}

    public static Usuario usuario(long id, String email, String senhaHash, Papel... papeis) {
        Usuario usuario = new Usuario("Usuário " + id, email, senhaHash, Set.of(papeis), true, CRIADO_EM);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    public static Usuario naoConfirmado(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "emailConfirmadoEm", null);
        return usuario;
    }

    public static Usuario inativo(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "ativo", false);
        return usuario;
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/domain/UsuarioTest.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsuarioTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({
        "ana@celke.uno,          ana@celke.uno",
        "Ana@Celke.UNO,          ana@celke.uno",
        "'  ana@celke.uno  ',    ana@celke.uno",
    })
    @DisplayName("normaliza o e-mail para minúsculas e sem espaços nas pontas")
    void normalizaEmail(String recebido, String esperado) {
        assertThat(Usuario.normalizarEmail(recebido)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("novo usuário nasce ativo, com nome sem espaços nas pontas e e-mail normalizado")
    void novoUsuario() {
        Usuario usuario = new Usuario("  Ana  ", " Ana@Celke.uno ", "{argon2}hash", Set.of(Papel.ALUNO), true, AGORA);

        assertThat(usuario.getNome()).isEqualTo("Ana");
        assertThat(usuario.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getPapeis()).containsExactly(Papel.ALUNO);
        assertThat(usuario.isEmailConfirmado()).isTrue();
    }

    @Test
    @DisplayName("cadastro com e-mail pendente é confirmado uma vez; confirmar de novo não muda nada")
    void confirmarEmail() {
        Usuario usuario = new Usuario("Ana", "ana@celke.uno", "{argon2}hash", Set.of(Papel.ALUNO), false, AGORA);
        assertThat(usuario.isEmailConfirmado()).isFalse();

        usuario.confirmarEmail(AGORA.plusSeconds(60));
        usuario.confirmarEmail(AGORA.plusSeconds(120));

        assertThat(usuario.isEmailConfirmado()).isTrue();
        assertThat(usuario).extracting("emailConfirmadoEm").isEqualTo(AGORA.plusSeconds(60));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/application/UsuarioServiceTest.java`:

`````java
package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;

    @BeforeEach
    void criar() {
        service = new UsuarioService(repository, passwordEncoder, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("cria o usuário confirmado com o e-mail normalizado e a senha em hash")
    void cria() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(false);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = service.criarConfirmado("Ana", " Ana@Celke.uno ", "senha-da-ana-123", Set.of(Papel.ALUNO));

        assertThat(criado.getEmail()).isEqualTo("ana@celke.uno");
        assertThat(criado.getSenhaHash()).isEqualTo("{argon2}hash");
        assertThat(criado.getPapeis()).containsExactly(Papel.ALUNO);
        assertThat(criado.isEmailConfirmado()).isTrue();
    }

    @Test
    @DisplayName("cadastro do aluno cria conta ALUNO com o e-mail pendente")
    void cadastraAluno() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(false);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Usuario criado = service.cadastrarAluno("Ana", "ana@celke.uno", "senha-da-ana-123");

        assertThat(criado.getPapeis()).containsExactly(Papel.ALUNO);
        assertThat(criado.isEmailConfirmado()).isFalse();
    }

    @Test
    @DisplayName("confirma o e-mail e grava")
    void confirmaEmail() {
        Usuario usuario = UsuarioFixture.naoConfirmado(UsuarioFixture.usuario(1, "ana@celke.uno", "hash", Papel.ALUNO));

        service.confirmarEmail(usuario);

        assertThat(usuario.isEmailConfirmado()).isTrue();
        verify(repository).save(usuario);
    }

    @Test
    @DisplayName("troca a senha gravando o novo hash")
    void trocaSenha() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "{argon2}antigo", Papel.ALUNO);
        when(passwordEncoder.encode("nova-senha-da-ana")).thenReturn("{argon2}novo");

        service.trocarSenha(usuario, "nova-senha-da-ana");

        assertThat(usuario.getSenhaHash()).isEqualTo("{argon2}novo");
        verify(repository).save(usuario);
    }

    @Test
    @DisplayName("rejeita e-mail já cadastrado sem gravar nem calcular hash")
    void emailDuplicado() {
        when(repository.existsByEmail("ana@celke.uno")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrarAluno("Ana", "ANA@celke.uno", "senha-da-ana-123"))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um usuário com este e-mail.");
        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("busca por e-mail usa o e-mail normalizado")
    void buscaPorEmail() {
        service.buscarPorEmail(" Ana@Celke.uno ");

        verify(repository).findByEmail("ana@celke.uno");
    }

    @Test
    @DisplayName("regrava o hash quando ele usa algoritmo antigo")
    void regravaHashAntigo() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "$2y$10$antigo", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("$2y$10$antigo")).thenReturn(true);
        when(passwordEncoder.encode("senha-da-ana-123")).thenReturn("{argon2}novo");

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("{argon2}novo");
    }

    @Test
    @DisplayName("não regrava o hash que já está no algoritmo atual")
    void mantemHashAtual() {
        Usuario usuario = UsuarioFixture.usuario(1, "ana@celke.uno", "{argon2}atual", Papel.ALUNO);
        when(passwordEncoder.upgradeEncoding("{argon2}atual")).thenReturn(false);

        service.atualizarHashSeNecessario(usuario, "senha-da-ana-123");

        verify(repository, never()).save(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/usuario/application/CriarAdminServiceTest.java`:

`````java
package br.com.celke.cursos.usuario.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.celke.cursos.usuario.domain.Papel;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CriarAdminServiceTest {

    @Mock
    private UsuarioService usuarioService;

    private CriarAdminService service;

    @BeforeEach
    void criar() {
        service = new CriarAdminService(usuarioService);
    }

    @Test
    @DisplayName("cria o usuário só com o papel ADMIN")
    void criaAdmin() {
        service.criar("Admin", "admin@celke.uno", "senha-longa-do-admin");

        verify(usuarioService).criarConfirmado("Admin", "admin@celke.uno", "senha-longa-do-admin", Set.of(Papel.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nome em branco,      '  ',  admin@celke.uno, senha-longa-do-admin, O nome é obrigatório.",
        "e-mail sem arroba,   Admin, admin.celke.uno, senha-longa-do-admin, E-mail inválido.",
        "senha curta,         Admin, admin@celke.uno, curta-11chr,          A senha deve ter pelo menos 12 caracteres.",
    })
    void rejeitaDadosInvalidos(String caso, String nome, String email, String senha, String mensagem) {
        assertThatThrownBy(() -> service.criar(nome, email, senha))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensagem);
        verify(usuarioService, never()).criarConfirmado(any(), any(), any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/AuthServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.application.RefreshTokenService.RefreshEmitido;
import br.com.celke.cursos.auth.application.TokenAcessoService.TokenAcesso;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.shared.erro.MuitasTentativasException;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant EXPIRA = Instant.parse("2026-01-10T12:10:00Z");
    private static final String HASH_FICTICIO = "{argon2}ficticio";
    private static final OrigemRequisicao ORIGEM = new OrigemRequisicao("203.0.113.7", "Navegador/1.0");

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private BloqueioLoginService bloqueioLoginService;

    @Mock
    private AuditoriaLoginService auditoriaLoginService;

    private AuthService service;

    @BeforeEach
    void criar() {
        // O construtor calcula o hash fictício usado quando o e-mail não existe
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        service = new AuthService(
                usuarioService,
                passwordEncoder,
                tokenAcessoService,
                refreshTokenService,
                bloqueioLoginService,
                auditoriaLoginService);
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "{argon2}hash-da-ana", Papel.ALUNO);
    }

    @Test
    @DisplayName("login correto emite os tokens, zera o bloqueio, audita SUCESSO e regrava hash antigo se preciso")
    void loginCorreto() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);
        when(refreshTokenService.emitir(7L)).thenReturn(new RefreshEmitido("refresh", 7L, EXPIRA));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.entrar("ana@celke.uno", "senha-certa", ORIGEM);

        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
        assertThat(sessao.refresh().token()).isEqualTo("refresh");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SUCESSO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(usuarioService).atualizarHashSeNecessario(ana, "senha-certa");
    }

    @Test
    @DisplayName("senha errada conta falha, audita SENHA_INVALIDA e não emite tokens")
    void senhaErrada() {
        Usuario ana = ana();
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", ana, ResultadoLogin.SENHA_INVALIDA, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
        verify(usuarioService, never()).atualizarHashSeNecessario(any(), any());
    }

    @Test
    @DisplayName("e-mail inexistente confere a senha contra o hash fictício, conta falha e audita EMAIL_DESCONHECIDO")
    void emailInexistente() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ninguem@celke.uno", "qualquer", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(passwordEncoder).matches("qualquer", HASH_FICTICIO);
        verify(auditoriaLoginService).registrar("ninguem@celke.uno", null, ResultadoLogin.EMAIL_DESCONHECIDO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ninguem@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("usuário inativo com a senha certa é recusado e auditado como USUARIO_INATIVO")
    void usuarioInativo() {
        Usuario inativa = UsuarioFixture.inativo(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(inativa));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(auditoriaLoginService).registrar("ana@celke.uno", inativa, ResultadoLogin.USUARIO_INATIVO, ORIGEM);
        verify(bloqueioLoginService).registrarFalha("ana@celke.uno", "203.0.113.7");
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("senha certa com e-mail não confirmado recebe 403 próprio, sem tokens, e zera o bloqueio")
    void emailNaoConfirmado() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(pendente));
        when(passwordEncoder.matches("senha-certa", "{argon2}hash-da-ana")).thenReturn(true);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(EmailNaoConfirmadoException.class)
                .hasMessage("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", pendente, ResultadoLogin.EMAIL_NAO_CONFIRMADO, ORIGEM);
        verify(bloqueioLoginService).registrarSucesso("ana@celke.uno", "203.0.113.7");
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
        verify(refreshTokenService, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("e-mail não confirmado com a senha errada recebe a mensagem genérica")
    void emailNaoConfirmadoSenhaErrada() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(passwordEncoder.matches("senha-errada", "{argon2}hash-da-ana")).thenReturn(false);

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-errada", ORIGEM))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("login bloqueado é recusado antes de buscar o usuário ou calcular hash, e auditado como BLOQUEADO")
    void bloqueado() {
        doThrow(new MuitasTentativasException(Duration.ofMinutes(15)))
                .when(bloqueioLoginService)
                .verificar("ana@celke.uno", "203.0.113.7");

        assertThatThrownBy(() -> service.entrar("ana@celke.uno", "senha-certa", ORIGEM))
                .isInstanceOf(MuitasTentativasException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 15 minutos.");
        verify(auditoriaLoginService).registrar("ana@celke.uno", null, ResultadoLogin.BLOQUEADO, ORIGEM);
        verify(usuarioService, never()).buscarPorEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(bloqueioLoginService, never()).registrarFalha(anyString(), anyString());
    }

    @Test
    @DisplayName("renovar emite novo access token para o dono do refresh")
    void renovar() {
        Usuario ana = ana();
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));
        when(tokenAcessoService.emitir(ana)).thenReturn(new TokenAcesso("jwt", EXPIRA));

        var sessao = service.renovar("refresh");

        assertThat(sessao.refresh().token()).isEqualTo("novo");
        assertThat(sessao.acesso().valor()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("refresh recusado vira sessão inválida")
    void refreshRecusado() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("refresh"))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão expirada. Entre novamente.");
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("refresh de usuário desativado vira sessão inválida")
    void renovarUsuarioInativo() {
        when(refreshTokenService.rotacionar("refresh")).thenReturn(Optional.of(new RefreshEmitido("novo", 7L, EXPIRA)));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.renovar("refresh")).isInstanceOf(SessaoInvalidaException.class);
        verify(tokenAcessoService, never()).emitir(any());
    }

    @Test
    @DisplayName("sair e sair de todos delegam a revogação")
    void sair() {
        service.sair("refresh");
        service.sairDeTodos(7L);

        verify(refreshTokenService).revogarFamiliaDoToken("refresh");
        verify(refreshTokenService).revogarTodas(7L);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/RefreshTokenServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.RefreshToken;
import br.com.celke.cursos.auth.infra.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final UUID FAMILIA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TOKEN = "token-do-cookie";

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void criar() {
        var props = new AuthProperties(
                "segredo", "plataforma-cursos", Duration.ofMinutes(10), Duration.ofDays(14), true, List.of());
        service = new RefreshTokenService(repository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static RefreshToken tokenGravado(Instant expiraEm) {
        return new RefreshToken(7L, FAMILIA, TokenSeguro.hash(TOKEN), expiraEm, AGORA.minusSeconds(60));
    }

    @Test
    @DisplayName("emitir grava só o hash, numa família nova, com validade de 14 dias")
    void emitir() {
        var emitido = service.emitir(7L);

        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getHash())
                .isEqualTo(TokenSeguro.hash(emitido.token()))
                .isNotEqualTo(emitido.token());
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofDays(14)));
        assertThat(emitido.token()).hasSize(43); // 32 bytes em Base64 URL sem padding
    }

    @Test
    @DisplayName("cada emissão gera um token diferente")
    void tokensDiferentes() {
        assertThat(service.emitir(7L).token()).isNotEqualTo(service.emitir(7L).token());
    }

    @Test
    @DisplayName("rotacionar marca o token como usado e emite outro na mesma família")
    void rotacionar() {
        RefreshToken atual = tokenGravado(AGORA.plusSeconds(1));
        when(repository.findByHash(TokenSeguro.hash(TOKEN))).thenReturn(Optional.of(atual));

        var novo = service.rotacionar(TOKEN);

        assertThat(novo).isPresent();
        assertThat(atual.foiUsado()).isTrue();
        ArgumentCaptor<RefreshToken> salvo = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getFamilia()).isEqualTo(FAMILIA);
        assertThat(salvo.getValue().getUsuarioId()).isEqualTo(7L);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("token ausente não consulta o banco")
    void tokenAusente(String token) {
        assertThat(service.rotacionar(token)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        when(repository.findByHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token expira no instante exato da validade")
    void expiradoNoInstanteExato() {
        when(repository.findByHash(anyString())).thenReturn(Optional.of(tokenGravado(AGORA)));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("token revogado é recusado sem revogar de novo")
    void revogado() {
        RefreshToken revogado = tokenGravado(AGORA.plusSeconds(60));
        ReflectionTestUtils.setField(revogado, "revogadoEm", AGORA.minusSeconds(1));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(revogado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository, never()).save(any());
        verify(repository, never()).revogarFamilia(any(), any());
    }

    @Test
    @DisplayName("token já usado revoga a família inteira e não emite outro")
    void reutilizado() {
        RefreshToken usado = tokenGravado(AGORA.plusSeconds(60));
        usado.marcarUsado(AGORA.minusSeconds(5));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(usado));

        assertThat(service.rotacionar(TOKEN)).isEmpty();
        verify(repository).revogarFamilia(FAMILIA, AGORA);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("logout revoga a família do token")
    void logout() {
        when(repository.findByHash(TokenSeguro.hash(TOKEN)))
                .thenReturn(Optional.of(tokenGravado(AGORA.plusSeconds(60))));

        service.revogarFamiliaDoToken(TOKEN);

        verify(repository).revogarFamilia(FAMILIA, AGORA);
    }

    @Test
    @DisplayName("logout sem token não consulta o banco")
    void logoutSemToken() {
        service.revogarFamiliaDoToken(null);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("sair de todos revoga todas as famílias do usuário")
    void sairDeTodos() {
        service.revogarTodas(7L);

        verify(repository).revogarTodasDoUsuario(7L, AGORA);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthAcessoIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Cobre: matriz de papéis das rotas de autenticação — públicas (login, refresh, logout) e privadas
 * (me, sair-de-todos) — para anônimo, ALUNO e ADMIN.
 * Não cobre: comportamento de cada rota (AuthLoginIT, AuthRefreshIT).
 * Dado criado: usuários it-<stamp>@teste.celke.uno para sair-de-todos (o seed é só leitura).
 */
class AuthAcessoIT extends IntegrationTest {

    static Stream<Arguments> rotasPublicas() {
        // Sem token não pode dar 401 da segurança: o resultado vem da própria rota
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/auth/login", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/logout", HttpStatus.NO_CONTENT),
                Arguments.of(HttpMethod.POST, "/api/auth/cadastro", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/confirmar-email", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/reenviar-confirmacao", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/recuperar-senha", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.POST, "/api/auth/redefinir-senha", HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPublicas")
    @DisplayName("rotas públicas respondem sem token")
    void rotasPublicasSemToken(HttpMethod metodo, String rota, HttpStatus esperado) {
        assertThat(mvc.method(metodo)
                        .uri(rota)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(esperado);
    }

    @Test
    @DisplayName("refresh sem token responde com a regra da sessão, não com o bloqueio da segurança")
    void refreshPublico() {
        assertThat(mvc.post().uri("/api/auth/refresh"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Sessão expirada. Entre novamente.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasPrivadas")
    @DisplayName("rotas privadas exigem token")
    void rotasPrivadasSemToken(HttpMethod metodo, String rota) {
        assertThat(mvc.method(metodo).uri(rota))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    static Stream<Arguments> rotasPrivadas() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/auth/me"), Arguments.of(HttpMethod.POST, "/api/auth/sair-de-todos"));
    }

    @Test
    @DisplayName("métodos não declarados nas rotas públicas exigem token")
    void metodoNaoDeclarado() {
        assertThat(mvc.get().uri("/api/auth/login")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.delete().uri("/api/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("usuariosSeed")
    @DisplayName("/api/auth/me responde para ALUNO e ADMIN")
    void mePorPapel(String email) {
        assertThat(mvc.get()
                        .uri("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(email, UsuariosSeed.SENHA))))
                .hasStatusOk();
    }

    static Stream<String> usuariosSeed() {
        return Stream.of(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.ADMIN_EMAIL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("papeis")
    @DisplayName("sair-de-todos responde para ALUNO e ADMIN")
    void sairDeTodosPorPapel(Papel papel) {
        UsuarioTeste usuario = criarUsuario(papel);

        assertThat(mvc.post()
                        .uri("/api/auth/sair-de-todos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(usuario.email(), usuario.senha()))))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    static Stream<Papel> papeis() {
        return Stream.of(Papel.ALUNO, Papel.ADMIN);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuthBloqueioIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.usuario.domain.Papel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: bloqueio do login depois de falhas seguidas (429 com Retry-After), liberação em outro IP,
 * auditoria de cada tentativa e o limite por IP ligado nas rotas de autenticação.
 * Não cobre: regras de tempo e contagem caso a caso (BloqueioLoginServiceTest, LimitePorIpFilterTest).
 * Dado criado: usuários it-<stamp>@teste.celke.uno e as tentativas deles na auditoria.
 */
class AuthBloqueioIT extends IntegrationTest {

    private static final String IP_ATACANTE = "203.0.113.50";
    private static final String IP_DONO = "198.51.100.20";

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cinco senhas erradas bloqueiam o e-mail naquele IP, mesmo com a senha certa depois")
    void bloqueiaDepoisDeCincoFalhas() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            assertThat(tentar(usuario.email(), "senha-errada", IP_ATACANTE)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult bloqueado = tentar(usuario.email(), usuario.senha(), IP_ATACANTE);

        assertThat(bloqueado)
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(HttpHeaders.RETRY_AFTER, "900")
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Muitas tentativas. Tente novamente em 15 minutos.");
    }

    @Test
    @DisplayName("o dono da conta entra de outro IP enquanto o IP do atacante está bloqueado")
    void outroIpEntra() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 5; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(tentar(usuario.email(), usuario.senha(), IP_DONO)).hasStatusOk();
    }

    @Test
    @DisplayName("toda tentativa fica na auditoria com resultado, IP e user-agent")
    void auditoria() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String desconhecido = stamp() + "@teste.celke.uno";

        tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        tentar(usuario.email(), usuario.senha(), IP_DONO);
        tentar(desconhecido, "qualquer", IP_ATACANTE);

        assertThat(auditoria(usuario.email()))
                .containsExactly(
                        new Tentativa("SENHA_INVALIDA", usuario.id(), IP_ATACANTE, "Teste/1.0"),
                        new Tentativa("SUCESSO", usuario.id(), IP_DONO, "Teste/1.0"));
        assertThat(auditoria(desconhecido))
                .containsExactly(new Tentativa("EMAIL_DESCONHECIDO", null, IP_ATACANTE, "Teste/1.0"));
    }

    @Test
    @DisplayName("tentativa barrada pelo bloqueio também fica na auditoria")
    void auditoriaDoBloqueio() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        for (int i = 0; i < 6; i++) {
            tentar(usuario.email(), "senha-errada", IP_ATACANTE);
        }

        assertThat(auditoria(usuario.email()))
                .extracting(Tentativa::resultado)
                .containsExactly(
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "SENHA_INVALIDA",
                        "BLOQUEADO");
    }

    @Test
    @DisplayName("o limite por IP vale para as rotas de autenticação e conta, e não para o logout")
    void limitePorIpLigado() {
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .headers()
                .containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/refresh")).headers().containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post()
                        .uri("/api/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .headers()
                .containsHeader("X-RateLimit-Remaining");
        assertThat(mvc.post().uri("/api/auth/logout")).headers().doesNotContainHeader("X-RateLimit-Remaining");
    }

    private MvcTestResult tentar(String email, String senha, String ip) {
        return mvc.post()
                .uri("/api/auth/login")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .header(HttpHeaders.USER_AGENT, "Teste/1.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
                .exchange();
    }

    private List<Tentativa> auditoria(String email) {
        return jdbc.sql("""
                        SELECT resultado, usuario_id, ip, user_agent FROM auditoria_login
                         WHERE email = :email ORDER BY id
                        """)
                .param("email", email)
                .query((rs, n) -> new Tentativa(
                        rs.getString("resultado"),
                        (Long) rs.getObject("usuario_id"),
                        rs.getString("ip"),
                        rs.getString("user_agent")))
                .list();
    }

    private record Tentativa(String resultado, Long usuarioId, String ip, String userAgent) {}
}
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

`infra/.env.example`:

`````dotenv
# Copie para infra/.env (ou rode ./infra/scripts/gerar-env.sh, que gera os segredos).
# Nunca faça commit do infra/.env.

# PostgreSQL
POSTGRES_DB=plataforma_cursos
POSTGRES_USER=plataforma_cursos
POSTGRES_PASSWORD=                 # openssl rand -hex 24

# Autenticação: chave do JWT em Base64, com pelo menos 32 bytes
JWT_SEGREDO=                       # openssl rand -base64 48
# Origens aceitas no refresh e no logout (padrão: a própria API e http://localhost:5173)
# AUTH_ORIGENS=http://localhost:8080,http://localhost:5173
# Área do aluno, base dos links enviados por e-mail (padrão: http://localhost:5173)
# FRONT_ALUNO_URL=http://localhost:5173

# Garage (S3)
GARAGE_RPC_SECRET=                 # openssl rand -hex 32
GARAGE_ADMIN_TOKEN=                # openssl rand -hex 32
S3_BUCKET=midia
S3_ACCESS_KEY=                     # GK + openssl rand -hex 12
S3_SECRET_KEY=                     # openssl rand -hex 32
# Origens do front que podem enviar e baixar direto do bucket (separadas por vírgula)
S3_CORS_ORIGENS=http://localhost:5173

# E-mail
EMAIL_REMETENTE=nao-responda@celke.uno

# Portas no host (opcional; só mude se a porta padrão já estiver em uso)
# API_PORTA=8080
# POSTGRES_PORTA=5432
# S3_PORTA=3900
# MAILPIT_PORTA=8025
`````

### 2. Migration

`backend/src/main/resources/db/migration/V5__confirmacao_email_e_tokens.sql`:

`````sql
-- Confirmação de e-mail: nulo enquanto o dono não confirmar. Contas que já existiam foram criadas
-- por quem administra a plataforma e ficam confirmadas.
ALTER TABLE usuarios ADD COLUMN email_confirmado_em TIMESTAMPTZ;
UPDATE usuarios SET email_confirmado_em = criado_em;

-- Tokens de uso único enviados por e-mail (confirmação de e-mail, recuperação de senha).
-- Só o hash SHA-256 é gravado; o token em texto existe apenas no link do e-mail.
CREATE TABLE tokens_uso_unico (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo       VARCHAR(30) NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    expira_em  TIMESTAMPTZ NOT NULL,
    usado_em   TIMESTAMPTZ,
    criado_em  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tokens_uso_unico_hash UNIQUE (hash),
    CONSTRAINT ck_tokens_uso_unico_tipo CHECK (tipo IN ('CONFIRMACAO_EMAIL', 'RECUPERACAO_SENHA'))
);

CREATE INDEX ix_tokens_uso_unico_usuario_tipo ON tokens_uso_unico (usuario_id, tipo, criado_em);

-- Novo resultado na auditoria: senha certa, e-mail ainda não confirmado
ALTER TABLE auditoria_login DROP CONSTRAINT ck_auditoria_login_resultado;
ALTER TABLE auditoria_login ADD CONSTRAINT ck_auditoria_login_resultado
    CHECK (resultado IN ('SUCESSO', 'EMAIL_DESCONHECIDO', 'SENHA_INVALIDA', 'USUARIO_INATIVO',
                         'EMAIL_NAO_CONFIRMADO', 'BLOQUEADO'));
`````

### 3. Tokens de uso único

`backend/src/main/java/br/com/celke/cursos/auth/application/TokenSeguro.java`:

`````java
package br.com.celke.cursos.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (refresh, links de e-mail): 32 bytes aleatórios em Base64 URL. O banco guarda só o
 * hash SHA-256; quem vê o banco não consegue usar o token.
 */
public final class TokenSeguro {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenSeguro() {}

    public static String gerar() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/TipoToken.java`:

`````java
package br.com.celke.cursos.auth.domain;

public enum TipoToken {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/domain/TokenUsoUnico.java`:

`````java
package br.com.celke.cursos.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Token de link enviado por e-mail, gravado só pelo hash. Vale uma vez e até expirar. */
@Entity
@Table(name = "tokens_uso_unico")
public class TokenUsoUnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoToken tipo;

    @Column(nullable = false, length = 64)
    private String hash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    /** Preenchido quando o token é usado ou invalidado por um token mais novo. */
    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected TokenUsoUnico() {}

    public TokenUsoUnico(Long usuarioId, TipoToken tipo, String hash, Instant expiraEm, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.hash = hash;
        this.expiraEm = expiraEm;
        this.criadoEm = agora;
    }

    /** Ainda não usado, do tipo certo e dentro da validade (expira no instante exato). */
    public boolean podeSerUsado(TipoToken tipoEsperado, Instant agora) {
        return usadoEm == null && tipo == tipoEsperado && agora.isBefore(expiraEm);
    }

    public void marcarUsado(Instant agora) {
        this.usadoEm = agora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public TipoToken getTipo() {
        return tipo;
    }

    public String getHash() {
        return hash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/TokenUsoUnicoRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TokenUsoUnicoRepository extends JpaRepository<TokenUsoUnico, Long> {

    /** Trava a linha: o mesmo link usado duas vezes ao mesmo tempo só vale uma. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenUsoUnico> findByHash(String hash);

    long countByUsuarioIdAndTipoAndCriadoEmAfter(Long usuarioId, TipoToken tipo, Instant desde);

    /** Invalida os links ainda não usados (quando um novo é enviado). */
    @Modifying
    @Query("""
            UPDATE TokenUsoUnico t SET t.usadoEm = :agora
             WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usadoEm IS NULL
            """)
    int invalidarPendentes(Long usuarioId, TipoToken tipo, Instant agora);
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/ContaProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cadastro, confirmação de e-mail e recuperação de senha.
 *
 * @param urlAluno endereço da área do aluno, base dos links enviados por e-mail
 * @param validadeConfirmacao validade do link de confirmação de e-mail
 * @param validadeRecuperacao validade do link de recuperação de senha
 * @param emailsPorHora e-mails do mesmo tipo por conta a cada hora (contra uso da plataforma para lotar a caixa de alguém)
 */
@Validated
@ConfigurationProperties("app.conta")
public record ContaProperties(
        @NotBlank String urlAluno,
        @NotNull Duration validadeConfirmacao,
        @NotNull Duration validadeRecuperacao,
        @Min(1) int emailsPorHora) {

    public ContaProperties {
        if (urlAluno != null && urlAluno.endsWith("/")) {
            urlAluno = urlAluno.substring(0, urlAluno.length() - 1);
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/TokenUsoUnicoService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import br.com.celke.cursos.auth.infra.TokenUsoUnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens dos links enviados por e-mail. Emitir um novo invalida os anteriores do mesmo tipo; cada
 * conta recebe no máximo {@code emailsPorHora} tokens de um tipo por hora.
 */
@Service
@EnableConfigurationProperties(ContaProperties.class)
public class TokenUsoUnicoService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenUsoUnicoService.class);

    private final TokenUsoUnicoRepository repository;
    private final ContaProperties props;
    private final Clock clock;

    public TokenUsoUnicoService(TokenUsoUnicoRepository repository, ContaProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Devolve o token em texto, ou vazio se a conta já recebeu o máximo de e-mails na última hora. */
    @Transactional
    public Optional<String> emitir(Long usuarioId, TipoToken tipo) {
        Instant agora = clock.instant();
        long naUltimaHora =
                repository.countByUsuarioIdAndTipoAndCriadoEmAfter(usuarioId, tipo, agora.minus(Duration.ofHours(1)));
        if (naUltimaHora >= props.emailsPorHora()) {
            LOG.warn("Limite de e-mails de {} atingido para o usuário {}", tipo, usuarioId);
            return Optional.empty();
        }
        repository.invalidarPendentes(usuarioId, tipo, agora);
        String token = TokenSeguro.gerar();
        repository.save(new TokenUsoUnico(usuarioId, tipo, TokenSeguro.hash(token), agora.plus(validade(tipo)), agora));
        return Optional.of(token);
    }

    /** Usa o token e devolve o id do dono; vazio se o token não existe, já foi usado, expirou ou é de outro tipo. */
    @Transactional
    public Optional<Long> consumir(String token, TipoToken tipo) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant agora = clock.instant();
        return repository
                .findByHash(TokenSeguro.hash(token))
                .filter(t -> t.podeSerUsado(tipo, agora))
                .map(t -> {
                    t.marcarUsado(agora);
                    return t.getUsuarioId();
                });
    }

    public Duration validade(TipoToken tipo) {
        return switch (tipo) {
            case CONFIRMACAO_EMAIL -> props.validadeConfirmacao();
            case RECUPERACAO_SENHA -> props.validadeRecuperacao();
        };
    }
}
`````

### 4. Cadastro e recuperação

`backend/src/main/java/br/com/celke/cursos/auth/application/LinkInvalidoException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Link de e-mail inexistente, já usado ou expirado: mesma mensagem nos três casos. */
public class LinkInvalidoException extends RegraNegocioException {

    public LinkInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "Link inválido ou expirado. Peça um novo.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/EmailNaoConfirmadoException.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Senha certa, mas o e-mail ainda não foi confirmado (só quem sabe a senha recebe esta resposta). */
public class EmailNaoConfirmadoException extends RegraNegocioException {

    public EmailNaoConfirmadoException() {
        super(HttpStatus.FORBIDDEN, "Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/Validade.java`:

`````java
package br.com.celke.cursos.auth.application;

import java.time.Duration;

/** Validade de um link escrita para o e-mail: "1 hora", "48 horas", "30 minutos". */
final class Validade {

    private Validade() {}

    static String texto(Duration validade) {
        long minutos = validade.toMinutes();
        if (minutos >= 60 && minutos % 60 == 0) {
            long horas = minutos / 60;
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return minutos + (minutos == 1 ? " minuto" : " minutos");
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/CadastroService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro do aluno e confirmação do e-mail. A resposta ao cliente é a mesma para e-mail novo e já
 * cadastrado; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
 */
@Service
public class CadastroService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final ContaProperties props;

    /** Contas que já receberam o aviso de "cadastro com e-mail existente" na última hora. */
    private final Cache<Long, Boolean> avisosEnviados = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public CadastroService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.props = props;
    }

    /**
     * E-mail novo: cria a conta pendente e envia o link de confirmação. E-mail com cadastro pendente:
     * reenvia o link (a senha informada é ignorada). E-mail de conta ativa: avisa o dono.
     */
    @Transactional
    public void cadastrar(String nome, String email, String senha) {
        Optional<Usuario> existente = usuarioService.buscarPorEmail(email);
        if (existente.isPresent()) {
            tratarEmailExistente(existente.get());
            return;
        }
        try {
            enviarConfirmacao(usuarioService.cadastrarAluno(nome, email, senha));
        } catch (ConflitoException e) {
            // Outro cadastro com o mesmo e-mail chegou junto: a resposta continua a mesma
        }
    }

    @Transactional
    public void reenviarConfirmacao(String email) {
        usuarioService
                .buscarPorEmail(email)
                .filter(u -> u.isAtivo() && !u.isEmailConfirmado())
                .ifPresent(this::enviarConfirmacao);
    }

    @Transactional
    public void confirmarEmail(String token) {
        Long usuarioId = tokens.consumir(token, TipoToken.CONFIRMACAO_EMAIL).orElseThrow(LinkInvalidoException::new);
        Usuario usuario = usuarioService.buscarPorId(usuarioId).orElseThrow(LinkInvalidoException::new);
        usuarioService.confirmarEmail(usuario);
    }

    private void tratarEmailExistente(Usuario usuario) {
        if (!usuario.isAtivo()) {
            return;
        }
        if (!usuario.isEmailConfirmado()) {
            enviarConfirmacao(usuario);
            return;
        }
        if (avisosEnviados.asMap().putIfAbsent(usuario.getId(), Boolean.TRUE) == null) {
            emailService.enviar(
                    usuario.getEmail(),
                    "Tentativa de cadastro com o seu e-mail",
                    "cadastro-existente",
                    Map.of(
                            "nome", usuario.getNome(),
                            "linkEntrar", props.urlAluno() + "/entrar",
                            "linkRecuperar", props.urlAluno() + "/recuperar-senha"));
        }
    }

    private void enviarConfirmacao(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.CONFIRMACAO_EMAIL)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/confirmar-email?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)))));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha por link enviado ao e-mail. A resposta ao cliente é a mesma exista ou não a
 * conta. Ao redefinir, todas as sessões abertas são encerradas.
 */
@Service
public class RecuperacaoSenhaService {

    private final UsuarioService usuarioService;
    private final TokenUsoUnicoService tokens;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final ContaProperties props;

    public RecuperacaoSenhaService(
            UsuarioService usuarioService,
            TokenUsoUnicoService tokens,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            ContaProperties props) {
        this.usuarioService = usuarioService;
        this.tokens = tokens;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.props = props;
    }

    @Transactional
    public void solicitar(String email) {
        usuarioService.buscarPorEmail(email).filter(Usuario::isAtivo).ifPresent(this::enviarLink);
    }

    /**
     * Troca a senha pelo link. Também confirma o e-mail (o link chegou à caixa do dono) e encerra
     * todas as sessões, inclusive a de quem tiver descoberto a senha antiga.
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        Long usuarioId = tokens.consumir(token, TipoToken.RECUPERACAO_SENHA).orElseThrow(LinkInvalidoException::new);
        Usuario usuario =
                usuarioService.buscarPorId(usuarioId).filter(Usuario::isAtivo).orElseThrow(LinkInvalidoException::new);
        usuarioService.trocarSenha(usuario, novaSenha);
        usuarioService.confirmarEmail(usuario);
        refreshTokenService.revogarTodas(usuario.getId());
    }

    private void enviarLink(Usuario usuario) {
        tokens.emitir(usuario.getId(), TipoToken.RECUPERACAO_SENHA)
                .ifPresent(token -> emailService.enviar(
                        usuario.getEmail(),
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", usuario.getNome(),
                                "link", props.urlAluno() + "/redefinir-senha?token=" + token,
                                "validade", Validade.texto(tokens.validade(TipoToken.RECUPERACAO_SENHA)))));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/ContaRequests.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos das rotas de cadastro, confirmação e recuperação de senha. */
public final class ContaRequests {

    private ContaRequests() {}

    public record Cadastro(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 254) String email,

            @NotBlank @Size(min = PoliticaSenha.MINIMO_ALUNO, max = PoliticaSenha.MAXIMO)
            String senha) {}

    public record EmailInformado(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record Token(@NotBlank @Size(max = 100) String token) {}

    public record RedefinirSenha(
            @NotBlank @Size(max = 100) String token,

            @NotBlank @Size(min = PoliticaSenha.MINIMO_ALUNO, max = PoliticaSenha.MAXIMO)
            String senha) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/MensagemResponse.java`:

`````java
package br.com.celke.cursos.auth.api;

/** Resposta com uma mensagem para mostrar ao usuário. */
public record MensagemResponse(String mensagem) {}
`````

`backend/src/main/java/br/com/celke/cursos/auth/api/ContaController.java`:

`````java
package br.com.celke.cursos.auth.api;

import br.com.celke.cursos.auth.application.CadastroService;
import br.com.celke.cursos.auth.application.RecuperacaoSenhaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro, confirmação de e-mail e recuperação de senha. As rotas que recebem um e-mail respondem
 * sempre o mesmo, exista ou não a conta: ninguém descobre por aqui quais e-mails estão cadastrados.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Conta")
public class ContaController {

    static final String MENSAGEM_CADASTRO = "Cadastro recebido. Confira o seu e-mail para continuar.";
    static final String MENSAGEM_REENVIO = "Se houver um cadastro pendente para este e-mail, enviaremos um novo link.";
    static final String MENSAGEM_RECUPERACAO =
            "Se houver uma conta com este e-mail, enviaremos um link para criar uma nova senha.";

    private final CadastroService cadastroService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public ContaController(CadastroService cadastroService, RecuperacaoSenhaService recuperacaoSenhaService) {
        this.cadastroService = cadastroService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    @PostMapping("/cadastro")
    @Operation(summary = "Criar conta de aluno", description = "Envia o link de confirmação para o e-mail.")
    public ResponseEntity<MensagemResponse> cadastrar(@Valid @RequestBody ContaRequests.Cadastro request) {
        cadastroService.cadastrar(request.nome(), request.email(), request.senha());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_CADASTRO));
    }

    @PostMapping("/confirmar-email")
    @Operation(summary = "Confirmar o e-mail com o token do link")
    public MensagemResponse confirmarEmail(@Valid @RequestBody ContaRequests.Token request) {
        cadastroService.confirmarEmail(request.token());
        return new MensagemResponse("E-mail confirmado. Você já pode entrar.");
    }

    @PostMapping("/reenviar-confirmacao")
    @Operation(summary = "Reenviar o link de confirmação")
    public ResponseEntity<MensagemResponse> reenviarConfirmacao(
            @Valid @RequestBody ContaRequests.EmailInformado request) {
        cadastroService.reenviarConfirmacao(request.email());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_REENVIO));
    }

    @PostMapping("/recuperar-senha")
    @Operation(summary = "Pedir o link para criar uma nova senha")
    public ResponseEntity<MensagemResponse> recuperarSenha(@Valid @RequestBody ContaRequests.EmailInformado request) {
        recuperacaoSenhaService.solicitar(request.email());
        return ResponseEntity.accepted().body(new MensagemResponse(MENSAGEM_RECUPERACAO));
    }

    @PostMapping("/redefinir-senha")
    @Operation(summary = "Criar uma nova senha com o token do link", description = "Encerra todas as sessões.")
    public MensagemResponse redefinirSenha(@Valid @RequestBody ContaRequests.RedefinirSenha request) {
        recuperacaoSenhaService.redefinir(request.token(), request.senha());
        return new MensagemResponse("Senha alterada. Entre com a nova senha.");
    }
}
`````

### 5. Templates de e-mail

`backend/src/main/resources/templates/email/confirmacao-email.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Confirme o seu e-mail</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;">Confirme o seu e-mail</h1>
    <p>Olá, <span th:text="${nome}">aluno</span>!</p>
    <p>Para ativar a sua conta na Plataforma de Cursos, confirme este endereço de e-mail.</p>
    <p style="margin:24px 0;">
        <a th:href="${link}" href="#"
           style="background:#2563eb;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;display:inline-block;">
            Confirmar e-mail
        </a>
    </p>
    <p>O link vale por <span th:text="${validade}">48 horas</span> e só pode ser usado uma vez.</p>
    <p style="color:#6b7280;font-size:13px;">Se você não criou esta conta, ignore este e-mail.</p>
</div>
</body>
</html>
`````

`backend/src/main/resources/templates/email/recuperacao-senha.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Crie uma nova senha</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;">Crie uma nova senha</h1>
    <p>Olá, <span th:text="${nome}">aluno</span>!</p>
    <p>Recebemos um pedido para criar uma nova senha para a sua conta na Plataforma de Cursos.</p>
    <p style="margin:24px 0;">
        <a th:href="${link}" href="#"
           style="background:#2563eb;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none;display:inline-block;">
            Criar nova senha
        </a>
    </p>
    <p>O link vale por <span th:text="${validade}">1 hora</span> e só pode ser usado uma vez. Ao criar a
        nova senha, você sai de todos os dispositivos.</p>
    <p style="color:#6b7280;font-size:13px;">Se você não fez esse pedido, ignore este e-mail: a sua senha
        continua a mesma.</p>
</div>
</body>
</html>
`````

`backend/src/main/resources/templates/email/cadastro-existente.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Tentativa de cadastro com o seu e-mail</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;">Você já tem uma conta</h1>
    <p>Olá, <span th:text="${nome}">aluno</span>!</p>
    <p>Alguém tentou criar uma conta na Plataforma de Cursos com este e-mail, mas ele já está cadastrado.</p>
    <p>Se foi você, <a th:href="${linkEntrar}" href="#">entre na sua conta</a> ou
        <a th:href="${linkRecuperar}" href="#">crie uma nova senha</a>.</p>
    <p style="color:#6b7280;font-size:13px;">Se não foi você, ignore este e-mail. Nenhuma alteração foi feita
        na sua conta.</p>
</div>
</body>
</html>
`````

### 6. Batimento do worker

`backend/src/main/java/br/com/celke/cursos/shared/fila/BatimentoWorker.java`:

`````java
package br.com.celke.cursos.shared.fila;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sinal de vida do worker, que não tem servidor HTTP para health check: regrava um arquivo a cada
 * 10 segundos e o healthcheck do Docker confere se ele mudou no último minuto. Roda separado do
 * consumo da fila, para um job demorado (como uma transcodificação) não parecer travamento.
 */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class BatimentoWorker {

    private final Path arquivo;
    private final Clock clock;

    public BatimentoWorker(@Value("${app.fila.arquivo-batimento}") Path arquivo, Clock clock) {
        this.arquivo = arquivo;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 10_000)
    public void bater() {
        try {
            if (Files.notExists(arquivo)) {
                Files.createFile(arquivo);
            }
            Files.setLastModifiedTime(arquivo, FileTime.from(clock.instant()));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o batimento em " + arquivo, e);
        }
    }
}
`````

### 7. Testes unitários

`backend/src/test/java/br/com/celke/cursos/auth/application/TokenSeguroTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenSeguroTest {

    @Test
    @DisplayName("hash é o SHA-256 em hexadecimal")
    void hashSha256() {
        assertThat(TokenSeguro.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("token tem 32 bytes em Base64 URL sem padding e cada um é diferente")
    void gerar() {
        String token = TokenSeguro.gerar();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(TokenSeguro.gerar()).isNotEqualTo(token);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/TokenUsoUnicoServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.auth.domain.TokenUsoUnico;
import br.com.celke.cursos.auth.infra.TokenUsoUnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUsoUnicoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");
    private static final String TOKEN = "token-do-link";

    @Mock
    private TokenUsoUnicoRepository repository;

    private TokenUsoUnicoService service;

    @BeforeEach
    void criar() {
        var props = new ContaProperties("http://localhost:5173", Duration.ofHours(48), Duration.ofHours(1), 3);
        service = new TokenUsoUnicoService(repository, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static TokenUsoUnico gravado(TipoToken tipo, Instant expiraEm) {
        return new TokenUsoUnico(7L, tipo, TokenSeguro.hash(TOKEN), expiraEm, AGORA.minusSeconds(60));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CONFIRMACAO_EMAIL, 48", "RECUPERACAO_SENHA, 1"})
    @DisplayName("emitir invalida os pendentes, grava só o hash e usa a validade do tipo")
    void emitir(TipoToken tipo, long horas) {
        when(repository.countByUsuarioIdAndTipoAndCriadoEmAfter(7L, tipo, AGORA.minus(Duration.ofHours(1))))
                .thenReturn(2L);

        String token = service.emitir(7L, tipo).orElseThrow();

        verify(repository).invalidarPendentes(7L, tipo, AGORA);
        ArgumentCaptor<TokenUsoUnico> salvo = ArgumentCaptor.forClass(TokenUsoUnico.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getHash())
                .isEqualTo(TokenSeguro.hash(token))
                .isNotEqualTo(token);
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofHours(horas)));
        assertThat(salvo.getValue().getTipo()).isEqualTo(tipo);
    }

    @Test
    @DisplayName("com 3 e-mails na última hora não emite nem invalida nada")
    void limitePorHora() {
        when(repository.countByUsuarioIdAndTipoAndCriadoEmAfter(any(), any(), any()))
                .thenReturn(3L);

        assertThat(service.emitir(7L, TipoToken.RECUPERACAO_SENHA)).isEmpty();
        verify(repository, never()).invalidarPendentes(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("consumir marca o token como usado e devolve o dono")
    void consumir() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(1));
        when(repository.findByHash(TokenSeguro.hash(TOKEN))).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).contains(7L);
        assertThat(token.getUsadoEm()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("token expira no instante exato da validade")
    void expirado() {
        when(repository.findByHash(anyString())).thenReturn(Optional.of(gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA)));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token já usado (ou invalidado por um mais novo) é recusado")
    void jaUsado() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(60));
        token.marcarUsado(AGORA.minusSeconds(1));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token de confirmação não serve para redefinir senha")
    void outroTipo() {
        TokenUsoUnico token = gravado(TipoToken.CONFIRMACAO_EMAIL, AGORA.plusSeconds(60));
        when(repository.findByHash(anyString())).thenReturn(Optional.of(token));

        assertThat(service.consumir(TOKEN, TipoToken.RECUPERACAO_SENHA)).isEmpty();
        assertThat(token.getUsadoEm()).isNull();
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void desconhecido() {
        when(repository.findByHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.consumir(TOKEN, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("token em branco não consulta o banco")
    void emBranco() {
        assertThat(service.consumir(" ", TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
        assertThat(service.consumir(null, TipoToken.CONFIRMACAO_EMAIL)).isEmpty();
        verifyNoInteractions(repository);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/ValidadeTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidadeTest {

    @ParameterizedTest(name = "{0} minutos")
    @CsvSource({
        "1,    1 minuto",
        "30,   30 minutos",
        "60,   1 hora",
        "90,   90 minutos",
        "120,  2 horas",
        "2880, 48 horas",
    })
    void texto(long minutos, String esperado) {
        assertThat(Validade.texto(Duration.ofMinutes(minutos))).isEqualTo(esperado);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/CadastroServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CadastroServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    private CadastroService service;

    @BeforeEach
    void criar() {
        service = new CadastroService(
                usuarioService,
                tokens,
                emailService,
                new ContaProperties("http://localhost:5173/", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    private void esperarToken(String token) {
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(token));
        when(tokens.validade(TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Duration.ofHours(48));
    }

    @Test
    @DisplayName("e-mail novo: cria a conta pendente e envia o link de confirmação")
    void emailNovo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno("Ana", "ana@celke.uno", "senha-da-ana"))
                .thenReturn(UsuarioFixture.naoConfirmado(ana()));
        esperarToken("abc");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Confirme o seu e-mail",
                        "confirmacao-email",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/confirmar-email?token=abc",
                                "validade", "48 horas"));
    }

    @Test
    @DisplayName("e-mail de conta ativa: não cria nada e avisa o dono só uma vez por hora")
    void emailExistente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));

        service.cadastrar("Outra Pessoa", "ana@celke.uno", "outra-senha-qualquer");
        service.cadastrar("Outra Pessoa", "ana@celke.uno", "outra-senha-qualquer");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService, times(1))
                .enviar(
                        "ana@celke.uno",
                        "Tentativa de cadastro com o seu e-mail",
                        "cadastro-existente",
                        Map.of(
                                "nome", "Usuário 7",
                                "linkEntrar", "http://localhost:5173/entrar",
                                "linkRecuperar", "http://localhost:5173/recuperar-senha"));
    }

    @Test
    @DisplayName("e-mail com cadastro pendente: reenvia o link, sem criar outra conta")
    void emailPendente() {
        when(usuarioService.buscarPorEmail("ana@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        esperarToken("novo");

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(usuarioService, never()).cadastrarAluno(any(), any(), any());
        verify(emailService).enviar(eq("ana@celke.uno"), eq("Confirme o seu e-mail"), eq("confirmacao-email"), any());
    }

    @Test
    @DisplayName("e-mail de conta inativa: nada acontece")
    void contaInativa() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: a conta é criada, mas o link não é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any())).thenReturn(UsuarioFixture.naoConfirmado(ana()));
        when(tokens.emitir(7L, TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("cadastro simultâneo com o mesmo e-mail não vira erro para o cliente")
    void cadastroSimultaneo() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.cadastrarAluno(any(), any(), any()))
                .thenThrow(new ConflitoException("Já existe um usuário com este e-mail."));

        assertThatCode(() -> service.cadastrar("Ana", "ana@celke.uno", "senha-da-ana"))
                .doesNotThrowAnyException();
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reenviar confirmação só envia para conta ativa com e-mail pendente")
    void reenviar() {
        when(usuarioService.buscarPorEmail("pendente@celke.uno"))
                .thenReturn(Optional.of(UsuarioFixture.naoConfirmado(ana())));
        when(usuarioService.buscarPorEmail("confirmada@celke.uno")).thenReturn(Optional.of(ana()));
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        esperarToken("abc");

        service.reenviarConfirmacao("pendente@celke.uno");
        service.reenviarConfirmacao("confirmada@celke.uno");
        service.reenviarConfirmacao("ninguem@celke.uno");

        verify(emailService, times(1)).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("confirmar com token válido confirma o e-mail do dono")
    void confirmar() {
        Usuario pendente = UsuarioFixture.naoConfirmado(ana());
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(pendente));

        service.confirmarEmail("abc");

        verify(usuarioService).confirmarEmail(pendente);
    }

    @Test
    @DisplayName("confirmar com token inválido é recusado sem confirmar nada")
    void confirmarTokenInvalido() {
        when(tokens.consumir("abc", TipoToken.CONFIRMACAO_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarEmail("abc"))
                .isInstanceOf(LinkInvalidoException.class)
                .hasMessage("Link inválido ou expirado. Peça um novo.");
        verify(usuarioService, never()).confirmarEmail(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/application/RecuperacaoSenhaServiceTest.java`:

`````java
package br.com.celke.cursos.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.auth.domain.TipoToken;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.support.fixtures.UsuarioFixture;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenUsoUnicoService tokens;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private RecuperacaoSenhaService service;

    @BeforeEach
    void criar() {
        service = new RecuperacaoSenhaService(
                usuarioService,
                tokens,
                emailService,
                refreshTokenService,
                new ContaProperties("http://localhost:5173", Duration.ofHours(48), Duration.ofHours(1), 3));
    }

    private static Usuario ana() {
        return UsuarioFixture.usuario(7, "ana@celke.uno", "hash", Papel.ALUNO);
    }

    @Test
    @DisplayName("conta ativa recebe o link de 1 hora para criar nova senha")
    void solicitar() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of("abc"));
        when(tokens.validade(TipoToken.RECUPERACAO_SENHA)).thenReturn(Duration.ofHours(1));

        service.solicitar("ana@celke.uno");

        verify(emailService)
                .enviar(
                        "ana@celke.uno",
                        "Crie uma nova senha",
                        "recuperacao-senha",
                        Map.of(
                                "nome", "Usuário 7",
                                "link", "http://localhost:5173/redefinir-senha?token=abc",
                                "validade", "1 hora"));
    }

    @Test
    @DisplayName("e-mail sem conta ou de conta inativa não recebe nada e não gera token")
    void semConta() {
        when(usuarioService.buscarPorEmail("ninguem@celke.uno")).thenReturn(Optional.empty());
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        service.solicitar("ninguem@celke.uno");
        service.solicitar("ana@celke.uno");

        verify(tokens, never()).emitir(anyLong(), any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("limite de e-mails atingido: nada é enviado")
    void limiteDeEmails() {
        when(usuarioService.buscarPorEmail("ana@celke.uno")).thenReturn(Optional.of(ana()));
        when(tokens.emitir(7L, TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        service.solicitar("ana@celke.uno");

        verify(emailService, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("redefinir troca a senha, confirma o e-mail e encerra todas as sessões")
    void redefinir() {
        Usuario ana = ana();
        when(tokens.consumir("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(ana));

        service.redefinir("abc", "nova-senha-da-ana");

        verify(usuarioService).trocarSenha(ana, "nova-senha-da-ana");
        verify(usuarioService).confirmarEmail(ana);
        verify(refreshTokenService).revogarTodas(7L);
    }

    @Test
    @DisplayName("token inválido é recusado sem trocar a senha nem encerrar sessões")
    void tokenInvalido() {
        when(tokens.consumir("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
        verify(refreshTokenService, never()).revogarTodas(anyLong());
    }

    @Test
    @DisplayName("conta desativada depois do pedido não troca a senha")
    void contaInativa() {
        when(tokens.consumir("abc", TipoToken.RECUPERACAO_SENHA)).thenReturn(Optional.of(7L));
        when(usuarioService.buscarPorId(7L)).thenReturn(Optional.of(UsuarioFixture.inativo(ana())));

        assertThatThrownBy(() -> service.redefinir("abc", "nova-senha-da-ana"))
                .isInstanceOf(LinkInvalidoException.class);
        verify(usuarioService, never()).trocarSenha(any(), any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/api/ContaRequestsTest.java`:

`````java
package br.com.celke.cursos.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContaRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static ContaRequests.Cadastro cadastroValido() {
        return new ContaRequests.Cadastro("Ana", "ana@celke.uno", "senha-ok-10");
    }

    @Test
    @DisplayName("cadastro válido não tem erros")
    void cadastroValidoSemErros() {
        assertThat(camposInvalidos(cadastroValido())).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "nove caracteres é curto demais,  9,   senha",
        "dez caracteres é o mínimo,       10,  ",
        "128 caracteres é o máximo,       128, ",
        "129 caracteres passa do máximo,  129, senha",
    })
    void tamanhoDaSenha(String caso, int tamanho, String campoInvalido) {
        var request = new ContaRequests.Cadastro("Ana", "ana@celke.uno", "a".repeat(tamanho));
        Set<String> invalidos = camposInvalidos(request);
        if (campoInvalido == null) {
            assertThat(invalidos).isEmpty();
        } else {
            assertThat(invalidos).containsExactly(campoInvalido);
        }
    }

    @Test
    @DisplayName("cadastro exige nome até 120 caracteres e e-mail válido")
    void nomeEEmail() {
        assertThat(camposInvalidos(new ContaRequests.Cadastro(" ", "ana@celke.uno", "senha-ok-10")))
                .containsExactly("nome");
        assertThat(camposInvalidos(new ContaRequests.Cadastro("a".repeat(121), "ana@celke.uno", "senha-ok-10")))
                .containsExactly("nome");
        assertThat(camposInvalidos(new ContaRequests.Cadastro("a".repeat(120), "ana@celke.uno", "senha-ok-10")))
                .isEmpty();
        assertThat(camposInvalidos(new ContaRequests.Cadastro("Ana", "ana.celke.uno", "senha-ok-10")))
                .containsExactly("email");
    }

    @Test
    @DisplayName("pedidos por e-mail, token e nova senha validam os campos")
    void outrosPedidos() {
        assertThat(camposInvalidos(new ContaRequests.EmailInformado("invalido")))
                .containsExactly("email");
        assertThat(camposInvalidos(new ContaRequests.Token(" "))).containsExactly("token");
        assertThat(camposInvalidos(new ContaRequests.Token("a".repeat(101)))).containsExactly("token");
        assertThat(camposInvalidos(new ContaRequests.RedefinirSenha("abc", "curta")))
                .containsExactly("senha");
        assertThat(camposInvalidos(new ContaRequests.RedefinirSenha("abc", "senha-ok-10")))
                .isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/fila/BatimentoWorkerTest.java`:

`````java
package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.RelogioAjustavel;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatimentoWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-01-10T12:00:00Z");

    @TempDir
    Path pasta;

    @Test
    @DisplayName("cria o arquivo no primeiro batimento e atualiza a data a cada batimento")
    void bate() throws Exception {
        Path arquivo = pasta.resolve("batimento");
        RelogioAjustavel relogio = new RelogioAjustavel(AGORA);
        BatimentoWorker batimento = new BatimentoWorker(arquivo, relogio);

        batimento.bater();
        assertThat(Files.getLastModifiedTime(arquivo).toInstant()).isEqualTo(AGORA);

        relogio.avancar(Duration.ofSeconds(10));
        batimento.bater();
        assertThat(Files.getLastModifiedTime(arquivo).toInstant()).isEqualTo(AGORA.plusSeconds(10));
    }

    @Test
    @DisplayName("falha de escrita aparece como erro, em vez de passar em silêncio")
    void falhaDeEscrita() {
        Path dentroDeArquivoInexistente = pasta.resolve("nao-existe").resolve("batimento");
        BatimentoWorker batimento = new BatimentoWorker(dentroDeArquivoInexistente, new RelogioAjustavel(AGORA));

        assertThatThrownBy(batimento::bater).isInstanceOf(UncheckedIOException.class);
    }
}
`````

### 8. Testes de integração

`backend/src/test/java/br/com/celke/cursos/auth/CadastroIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: cadastro → e-mail de confirmação → confirmação → login; mesma resposta para e-mail novo e já
 * cadastrado; reenvio do link; link de uso único; limite de e-mails por hora.
 * Não cobre: regras caso a caso (CadastroServiceTest, TokenUsoUnicoServiceTest).
 * Dado criado: contas it-<stamp>@teste.celke.uno e os e-mails delas no Mailpit.
 */
class CadastroIT extends IntegrationTest {

    private static final String SENHA = "senha-do-cadastro";
    private static final String MENSAGEM = "Cadastro recebido. Confira o seu e-mail para continuar.";

    @Autowired
    private Mailpit mailpit;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("cadastro, confirmação pelo link do e-mail e login")
    void jornadaCompleta() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(cadastrar("Ana Souza", email, SENHA))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(email);
        assertThat(recebido.Subject()).isEqualTo("Confirme o seu e-mail");
        assertThat(recebido.HTML())
                .contains("Olá, <span>Ana Souza</span>!")
                .contains("http://localhost:5173/confirmar-email?token=")
                .contains("48 horas");

        // Antes de confirmar, a senha certa recebe a orientação de confirmar o e-mail
        assertThat(tentarLogin(email, SENHA))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Confirme o seu e-mail antes de entrar. Se não recebeu o link, peça um novo.");

        assertThat(confirmar(Mailpit.tokenDoLink(recebido)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("E-mail confirmado. Você já pode entrar.");

        assertThat(entrar(email, SENHA))
                .bodyJson()
                .extractingPath("$.usuario.papeis")
                .asArray()
                .containsExactly("ALUNO");
    }

    @Test
    @DisplayName("o link de confirmação vale uma vez só")
    void linkDeUsoUnico() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(confirmar(token)).hasStatusOk();
        assertLinkInvalido(confirmar(token));
    }

    @Test
    @DisplayName("e-mail já cadastrado recebe a mesma resposta, a conta não muda e o dono é avisado")
    void emailJaCadastrado() {
        UsuarioTeste existente = criarUsuario(Papel.ALUNO);

        assertThat(cadastrar("Outra Pessoa", existente.email(), "outra-senha-qualquer"))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        assertThat(mailpit.aguardarEmailPara(existente.email()).Subject())
                .isEqualTo("Tentativa de cadastro com o seu e-mail");
        assertThat(contasCom(existente.email())).isEqualTo(1);
        entrar(existente.email(), existente.senha());
        assertThat(tentarLogin(existente.email(), "outra-senha-qualquer")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("reenviar confirmação manda um link novo e invalida o anterior")
    void reenviarInvalidaAnterior() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        processarFila();
        String primeiro = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        processarFila();
        String segundo = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(segundo).isNotEqualTo(primeiro);
        assertLinkInvalido(confirmar(primeiro));
        assertThat(confirmar(segundo)).hasStatusOk();
    }

    @Test
    @DisplayName("reenviar para e-mail sem cadastro responde igual e não envia nada")
    void reenviarSemCadastro() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(reenviar(email))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("Se houver um cadastro pendente para este e-mail, enviaremos um novo link.");
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isZero();
    }

    @Test
    @DisplayName("no máximo 3 e-mails de confirmação por hora para a mesma conta")
    void limiteDeEmailsPorHora() {
        String email = stamp() + "@teste.celke.uno";
        cadastrar("Ana", email, SENHA);
        for (int i = 0; i < 3; i++) {
            assertThat(reenviar(email)).hasStatus(HttpStatus.ACCEPTED);
        }
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isEqualTo(3);
    }

    @Test
    @DisplayName("senha curta é recusada com o campo indicado")
    void senhaCurta() {
        assertThat(cadastrar("Ana", stamp() + "@teste.celke.uno", "curta"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactly("senha");
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        assertLinkInvalido(confirmar("token-que-nunca-existiu"));
    }

    private MvcTestResult cadastrar(String nome, String email, String senha) {
        return post(
                "/api/auth/cadastro",
                "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(nome, email, senha));
    }

    private MvcTestResult confirmar(String token) {
        return post("/api/auth/confirmar-email", "{\"token\":\"%s\"}".formatted(token));
    }

    private MvcTestResult reenviar(String email) {
        return post("/api/auth/reenviar-confirmacao", "{\"email\":\"%s\"}".formatted(email));
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return post("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha));
    }

    private MvcTestResult post(String rota, String json) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private int contasCom(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM usuarios WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
    }

    private static void assertLinkInvalido(MvcTestResult resposta) {
        assertThat(resposta)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Link inválido ou expirado. Peça um novo.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/auth/RecuperacaoSenhaIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Cobre: pedido de recuperação → e-mail com link → nova senha → sessões antigas encerradas; mesma
 * resposta para e-mail sem conta; link de uso único; e-mail confirmado pela redefinição.
 * Não cobre: regras caso a caso (RecuperacaoSenhaServiceTest, TokenUsoUnicoServiceTest).
 * Dado criado: contas it-<stamp>@teste.celke.uno e os e-mails delas no Mailpit.
 */
class RecuperacaoSenhaIT extends IntegrationTest {

    private static final String NOVA_SENHA = "minha-nova-senha";
    private static final String MENSAGEM =
            "Se houver uma conta com este e-mail, enviaremos um link para criar uma nova senha.";

    @Autowired
    private Mailpit mailpit;

    @Test
    @DisplayName("nova senha pelo link do e-mail: a antiga deixa de valer e as sessões abertas são encerradas")
    void jornadaCompleta() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        String cookieAntigo = entrar(usuario.email(), usuario.senha())
                .getResponse()
                .getCookie("refresh_token")
                .getValue();

        assertThat(solicitar(usuario.email()))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();
        Mailpit.EmailRecebido recebido = mailpit.aguardarEmailPara(usuario.email());
        assertThat(recebido.Subject()).isEqualTo("Crie uma nova senha");
        assertThat(recebido.HTML())
                .contains("http://localhost:5173/redefinir-senha?token=")
                .contains("1 hora");

        assertThat(redefinir(Mailpit.tokenDoLink(recebido), NOVA_SENHA))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo("Senha alterada. Entre com a nova senha.");

        assertThat(tentarLogin(usuario.email(), usuario.senha())).hasStatus(HttpStatus.UNAUTHORIZED);
        entrar(usuario.email(), NOVA_SENHA);
        assertThat(mvc.post().uri("/api/auth/refresh").cookie(new Cookie("refresh_token", cookieAntigo)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("o link de recuperação vale uma vez só")
    void linkDeUsoUnico() {
        UsuarioTeste usuario = criarUsuario(Papel.ALUNO);
        solicitar(usuario.email());
        processarFila();
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(usuario.email()));

        assertThat(redefinir(token, NOVA_SENHA)).hasStatusOk();
        assertThat(redefinir(token, "outra-senha-nova"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Link inválido ou expirado. Peça um novo.");
        entrar(usuario.email(), NOVA_SENHA);
    }

    @Test
    @DisplayName("e-mail sem conta recebe a mesma resposta e nenhum e-mail é enviado")
    void emailSemConta() {
        String email = stamp() + "@teste.celke.uno";

        assertThat(solicitar(email))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.mensagem")
                .isEqualTo(MENSAGEM);
        processarFila();

        assertThat(mailpit.quantidadePara(email)).isZero();
    }

    @Test
    @DisplayName("redefinir a senha também confirma o e-mail de um cadastro pendente")
    void confirmaEmailPendente() {
        String email = stamp() + "@teste.celke.uno";
        post(
                "/api/auth/cadastro",
                "{\"nome\":\"Ana\",\"email\":\"%s\",\"senha\":\"senha-do-cadastro\"}".formatted(email));
        solicitar(email);
        processarFila();
        // O e-mail mais recente é o de recuperação
        String token = Mailpit.tokenDoLink(mailpit.aguardarEmailPara(email));

        assertThat(redefinir(token, NOVA_SENHA)).hasStatusOk();

        entrar(email, NOVA_SENHA);
    }

    @Test
    @DisplayName("nova senha curta é recusada com o campo indicado")
    void senhaCurta() {
        assertThat(redefinir("qualquer", "curta"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.erros[*].campo")
                .asArray()
                .containsExactly("senha");
    }

    private MvcTestResult solicitar(String email) {
        return post("/api/auth/recuperar-senha", "{\"email\":\"%s\"}".formatted(email));
    }

    private MvcTestResult redefinir(String token, String senha) {
        return post("/api/auth/redefinir-senha", "{\"token\":\"%s\",\"senha\":\"%s\"}".formatted(token, senha));
    }

    private MvcTestResult tentarLogin(String email, String senha) {
        return post("/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha));
    }

    private MvcTestResult post(String rota, String json) {
        return mvc.post()
                .uri(rota)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
`````

## Antes de executar

Etapas 1.1 a 1.5 concluídas.

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 192 testes unitários e 104 de integração, e a cobertura mínima atendida.

Na raiz do repositório:

```bash
docker compose -f infra/compose.yml up -d --build --wait
docker compose -f infra/compose.yml ps
```

- Os cinco serviços ficam `healthy`, inclusive o `worker`.
- `POST /api/auth/cadastro` (pelo Swagger ou `curl`) responde 202; em poucos segundos o e-mail
  "Confirme o seu e-mail" aparece no Mailpit (`http://localhost:8025`) com o link
  `http://localhost:5173/confirmar-email?token=...`.
- Login antes de confirmar responde 403; `POST /api/auth/confirmar-email` com o token do link responde
  200; depois o login responde 200.
- `POST /api/auth/recuperar-senha` envia "Crie uma nova senha"; `POST /api/auth/redefinir-senha` com o
  token e uma nova senha faz a senha antiga deixar de valer.

## Depois de executar

```bash
git add backend/ infra/
git commit -m "Cadastro, confirmação de e-mail e recuperação de senha"
```

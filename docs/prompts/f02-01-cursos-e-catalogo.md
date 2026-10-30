# Cursos, turmas, módulos, aulas e catálogo

**Fase:** 2
**Branch:** `feature/cursos`

> **Configuração do projeto:** troque os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`,
> `<sub-adm>`, `<sub-media>` e `<dono-github>` pelos valores de `docs/configuracao.md` (criado no
> primeiro prompt) em todo o conteúdo deste prompt, inclusive nos testes. Se o arquivo não existir,
> pergunte esses valores ao usuário antes de começar.

## Prompt

### Especificação e tasks (SDD)

Antes do código, registre a funcionalidade no SDD: crie a spec e as tasks abaixo com **exatamente** este conteúdo (troque os marcadores pelos valores de `docs/configuracao.md`). As tasks nascem com `Status: Pronta`; ao fim da **Verificação**, passam para `Concluída`.

`.sdd/specs/catalogo-de-cursos.spec.md`:

`````markdown
---
feature: catalogo-de-cursos
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Catálogo de cursos

## 1. Resumo Executivo

O administrador monta a estrutura de cada curso — curso, turmas, módulos e aulas — e decide o que é
publicado. O visitante vê no catálogo público apenas os cursos publicados, com o conteúdo da turma
ativa mais recente e a capa.

Entra: gestão de cursos, turmas, módulos e aulas, ordem do conteúdo, capa e catálogo público. Fica de
fora: matrícula (spec `matricula-e-acesso`) e vídeo (spec `envio-de-video`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Criar, alterar, reordenar, mudar o status e excluir itens vazios |
| Visitante | Ver o catálogo e a página dos cursos publicados |
| Aluno | O mesmo que o visitante; recebe 403 na gestão |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE manter cursos com título, endereço amigável único (gerado do título quando
  não informado) e status `RASCUNHO`, `PUBLICADO` ou `ARQUIVADO`.
- **RF-02** — O sistema DEVE manter turmas de um curso com status `PREPARACAO`, `ATIVA` ou `ENCERRADA`.
- **RF-03** — O sistema DEVE manter módulos de uma turma e aulas de um módulo, com status `ATIVO` ou
  `INATIVO` e a marcação de aula gratuita.
- **RF-04** — O sistema DEVE manter a ordem de módulos e aulas sem lacunas nem repetições: item novo
  entra no fim, exclusão refaz a sequência e a reordenação recebe a lista completa.
- **RF-05** — O sistema DEVE excluir apenas itens vazios (curso sem turmas, turma sem módulos, módulo sem
  aulas); os demais são arquivados, encerrados ou inativados.
- **RF-06** — O sistema DEVE receber a capa do curso direto no armazenamento (JPEG ou PNG até 5 MB) e
  guardá-la reduzida para até 1280 × 720, sem metadados, apagando o original e a capa anterior.
- **RF-07** — O sistema DEVE listar no catálogo público só os cursos publicados, em ordem alfabética,
  paginados.
- **RF-08** — O sistema DEVE mostrar na página do curso os módulos e as aulas ativos da turma ativa mais
  recente, sem dados internos; rascunho e arquivado respondem "não encontrado".
- **RF-09** — O sistema DEVE oferecer um endereço estável para a capa que leva a uma cópia de acesso
  temporário.

## 4. Modelo de Domínio

- **Curso:** título, endereço amigável, descrição, status, capa.
- **Turma:** curso, nome, status.
- **Módulo:** turma, título, posição, status.
- **Aula:** módulo, título, descrição, posição, status, gratuita.

## 5. Estados e Fluxos

- Curso: `RASCUNHO` ⇄ `PUBLICADO` → `ARQUIVADO`.
- Turma: `PREPARACAO` → `ATIVA` → `ENCERRADA`.
- **Montagem:** curso → turma → módulos → aulas → capa → publicar.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Criar curso | `titulo`, `slug` opcional, `descricao` | curso |
| Mudar status | `status` | item atualizado |
| Reordenar | lista completa de ids | ordem nova |
| Pedir envio da capa | `contentType`, `tamanho` | endereço temporário de envio e chave |
| Confirmar capa | `chave` | curso com capa |
| Catálogo | página | cursos publicados |
| Página do curso | endereço amigável | curso, módulos e aulas ativos |

A capa e os arquivos ficam em `<sub-media>.<dominio>`.

## 7. Casos de Borda

- Endereço amigável repetido: 409.
- Reordenação com id faltando, sobrando ou repetido: 400.
- Duas criações simultâneas no mesmo módulo: posições distintas, sem conflito.
- Capa com chave de outro curso, tamanho diferente do informado ou lado acima de 8000 px: recusada.
- Imagem com orientação nos metadados ou transparência: regravada corretamente.

## 8. Premissas Validadas

- Um curso pode ter várias turmas; o catálogo mostra a ativa mais recente.
- Nada é apagado se tiver histórico: arquivar e encerrar preservam matrículas.

## 9. Riscos

- **Imagem maliciosa ou gigante:** limites de tamanho e de lado antes de decodificar.
- **Aluno alcançar a gestão:** papel conferido na entrada e em cada operação.
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/README.md`:

`````markdown
# Tasks — Catálogo de cursos (v1.0)

Spec: [`.sdd/specs/catalogo-de-cursos.spec.md`](../../specs/catalogo-de-cursos.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f02-01-cursos-e-catalogo.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [CURSO-001](CURSO-001-dominio.md) | Domínio de cursos, turmas, módulos e aulas | Sênior | — |
| 2 | [CURSO-002](CURSO-002-gestao-adm.md) | Gestão pelo administrador | Pleno | CURSO-001 |
| 3 | [CURSO-003](CURSO-003-capa.md) | Capa do curso | Sênior | CURSO-001 |
| 4 | [CURSO-004](CURSO-004-catalogo-publico.md) | Catálogo público | Pleno | CURSO-001 |
| 5 | [CURSO-005](CURSO-005-fiacao.md) | Fiação do catálogo | Pleno | CURSO-002, CURSO-003, CURSO-004 |

## Grafo

```
CURSO-001 (início)
CURSO-001 → CURSO-002
CURSO-001 → CURSO-003
CURSO-001 → CURSO-004
CURSO-002 + CURSO-003 + CURSO-004 → CURSO-005
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | CURSO-001, CURSO-002 |
| RF-02 | CURSO-001, CURSO-002 |
| RF-03 | CURSO-001, CURSO-002 |
| RF-04 | CURSO-001, CURSO-002 |
| RF-05 | CURSO-002 |
| RF-06 | CURSO-003 |
| RF-07 | CURSO-004, CURSO-005 |
| RF-08 | CURSO-004 |
| RF-09 | CURSO-003, CURSO-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 2
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/CURSO-001-dominio.md`:

`````markdown
# CURSO-001 — Domínio de cursos, turmas, módulos e aulas

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/domain/Curso.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Turma.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Modulo.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Aula.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Slug.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/Ordenacao.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusCurso.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusTurma.java`
- `backend/src/main/java/br/com/celke/cursos/curso/domain/StatusConteudo.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/CursoRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/TurmaRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/ModuloRepository.java`
- `backend/src/main/java/br/com/celke/cursos/curso/infra/AulaRepository.java`

## Descrição técnica

Ordem única por pai com restrição adiável; item novo no fim; o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda.

## Restrições

`ON DELETE RESTRICT` em toda a hierarquia.

## Critérios de aceite

- [ ] `SlugTest`, `OrdenacaoTest` verdes
- [ ] `ConteudoOrdemIT`: criação simultânea sem conflito

## Armadilhas

Reordenar posição por posição viola a restrição única no meio da troca.
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/CURSO-002-gestao-adm.md`:

`````markdown
# CURSO-002 — Gestão pelo administrador

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-01, RF-02, RF-03, RF-04, RF-05
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/TurmaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/ModuloAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/AulaAdmController.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoRequests.java`
- `backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/CursoAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/TurmaAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/ModuloAdmService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/AulaAdmService.java`

## Descrição técnica

CRUD em `/api/adm/**`, `PATCH .../status` e `PUT .../ordem` com a lista completa de ids.

## Restrições

ADMIN conferido no `SecurityConfig` e com `@PreAuthorize`.

## Critérios de aceite

- [ ] serviços `*AdmServiceTest` verdes
- [ ] `CursoCrudIT` e `CursoAcessoIT` (matriz de papéis) verdes

## Armadilhas

Excluir item com filhos em vez de responder 409.
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/CURSO-003-capa.md`:

`````markdown
# CURSO-003 — Capa do curso

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-06, RF-09
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/application/CapaCursoService.java`
- `backend/src/main/java/br/com/celke/cursos/shared/imagem/ProcessadorImagem.java`

## Descrição técnica

URL pré-assinada em `uploads/capas/<curso>/<uuid>`; a confirmação confere chave, tamanho e lado e regrava em JPEG até 1280 × 720 em `capas/<curso>/<uuid>.jpg`.

## Restrições

Imagens servidas por `<sub-media>.<dominio>`.

## Critérios de aceite

- [ ] `CapaCursoServiceTest`, `ProcessadorImagemTest` verdes
- [ ] `CapaCursoIT` com o Garage real

## Armadilhas

Decodificar antes de conferir o lado deixa uma imagem gigante derrubar a API.
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/CURSO-004-catalogo-publico.md`:

`````markdown
# CURSO-004 — Catálogo público

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-07, RF-08, RF-09
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** CURSO-001

## Arquivos a criar/alterar

- `backend/src/main/java/br/com/celke/cursos/curso/api/CatalogoController.java` — `GET /api/cursos`, `/api/cursos/{slug}`, `/api/cursos/{slug}/capa`
- `backend/src/main/java/br/com/celke/cursos/curso/application/CatalogoService.java`
- `backend/src/main/java/br/com/celke/cursos/curso/application/ConteudoTurmaService.java`

## Descrição técnica

Só publicados; conteúdo ativo da turma ativa mais recente; capa por redirecionamento com cache de 5 minutos.

## Restrições

Rascunho e arquivado respondem 404.

## Critérios de aceite

- [ ] `CatalogoServiceTest`, `ConteudoTurmaServiceTest` verdes
- [ ] `CatalogoIT` verde
- [ ] `CatalogoE2E` verde

## Armadilhas

Expor ids internos e status no catálogo público.
`````

`.sdd/tasks/catalogo-de-cursos-v1.0/CURSO-005-fiacao.md`:

`````markdown
# CURSO-005 — Fiação do catálogo

- **Spec:** .sdd/specs/catalogo-de-cursos.spec.md (v1.0) · atende RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** CURSO-002, CURSO-003, CURSO-004

## Arquivos a criar/alterar

- `backend/src/main/resources/db/migration/V6__criar_cursos.sql`
- `backend/src/main/java/br/com/celke/cursos/shared/security/SecurityConfig.java`

## Descrição técnica

Migration V6 com os `CHECK` de status e do formato do slug; catálogo público só em GET.

## Critérios de aceite

- [ ] `./mvnw verify` verde
- [ ] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.

## Armadilhas

Restrição de ordem não adiável.
`````

Acrescente ao fim da tabela do índice `.sdd/README.md` a linha:

```text
| [catalogo-de-cursos](specs/catalogo-de-cursos.spec.md) | 1.0 | [v1.0](tasks/catalogo-de-cursos-v1.0/README.md) | `f02-01-cursos-e-catalogo.md` |
```

Vamos criar o módulo `curso` do backend: a gestão de cursos, turmas, módulos e aulas pelo ADM, a capa
do curso e o catálogo público que o site vai consumir. Siga o `backend/CLAUDE.md` e o padrão das
skills de teste. Não faça commit.

O que esta etapa entrega:

- **Modelo** (migration `V6`): curso → turmas → módulos → aulas, com `ON DELETE RESTRICT` em toda a
  hierarquia. Status como enum, gravados como texto com `CHECK` no banco:
  - curso: `RASCUNHO`, `PUBLICADO` ou `ARQUIVADO` (nasce em rascunho);
  - turma: `PREPARACAO`, `ATIVA` ou `ENCERRADA` (nasce em preparação);
  - módulo e aula: `ATIVO` ou `INATIVO`.
  A aula pode ser gratuita; sem o campo `gratuita` no corpo, ela é paga.
- **Slug** do curso para o site: sem acento, minúsculas, números e hífens, até 80 caracteres, gerado do
  título quando não informado e único no banco. Slug repetido responde 409.
- **Ordem** de módulos (na turma) e de aulas (no módulo), sempre 1, 2, 3...:
  - item novo entra no fim e a exclusão refaz a sequência;
  - a reordenação recebe a lista completa de ids (400 se faltar, sobrar ou repetir);
  - a posição é única por pai com restrição `DEFERRABLE INITIALLY DEFERRED`, para a troca de todas as
    posições acontecer numa transação;
  - o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda, para criações simultâneas não
    repetirem posição.
- **Exclusão só de item vazio**: curso sem turmas, turma sem módulos, módulo sem aulas (409 com a
  orientação). O que tem conteúdo é arquivado, encerrado ou inativado pelo status.
- **Rotas do ADM** em `/api/adm` (ADMIN exigido no `SecurityConfig`, antes de ler o corpo, e de novo com
  `@PreAuthorize` nos controllers):
  - cursos: `GET` paginado com filtro `status`, `POST` (201 com `Location`), `GET`, `PUT`,
    `PATCH /{id}/status` e `DELETE /{id}`;
  - turmas: `GET`/`POST /cursos/{id}/turmas`, `PUT`, `PATCH /status` e `DELETE /turmas/{id}`;
  - módulos: `GET`/`POST /turmas/{id}/modulos`, `PUT /turmas/{id}/modulos/ordem`, `PUT`,
    `PATCH /status` e `DELETE /modulos/{id}`;
  - aulas: `GET`/`POST /modulos/{id}/aulas`, `PUT /modulos/{id}/aulas/ordem`, `PUT`, `PATCH /status` e
    `DELETE /aulas/{id}`.
  As listagens ignoram a ordenação enviada pelo cliente.
- **Capa do curso**, em duas etapas:
  1. `POST /api/adm/cursos/{id}/capa/envio` com `contentType` (JPEG ou PNG) e `tamanho` (até 5 MB)
     devolve uma URL pré-assinada de PUT em `uploads/capas/<curso>/<uuid>`;
  2. `PUT /api/adm/cursos/{id}/capa` com a `chave` confirma: só aceita a chave gerada para o mesmo curso,
     confere o tamanho real, recusa lado acima de 8000 px lendo só o cabeçalho, reduz para até
     1280 × 720 e regrava em JPEG sem metadados em `capas/<curso>/<uuid>.jpg`. O arquivo enviado é
     apagado em qualquer resultado; a capa anterior, depois da troca.
  `DELETE /api/adm/cursos/{id}/capa` remove. O `ProcessadorImagem` fica em `shared/imagem`, porque a
  foto de perfil vai reutilizá-lo.
- **Catálogo público** (`GET`, declarado no `SecurityConfig`):
  - `/api/cursos`: publicados, paginado, em ordem alfabética;
  - `/api/cursos/{slug}`: módulos e aulas ativos da turma ativa mais recente, sem dados internos;
    rascunho e arquivado respondem 404;
  - `/api/cursos/{slug}/capa`: endereço estável que redireciona (302) para uma URL assinada nova, com
    cache de 5 minutos — a URL assinada vence em uma hora e não pode ir para o HTML pré-renderizado.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### 1. Arquivos alterados (substitua o conteúdo inteiro)

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

`backend/src/main/java/br/com/celke/cursos/shared/armazenamento/StorageService.java`:

`````java
package br.com.celke.cursos.shared.armazenamento;

import java.net.URL;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
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

### 2. Migration

`backend/src/main/resources/db/migration/V6__criar_cursos.sql`:

`````sql
-- Curso → turmas → módulos → aulas. Exclusão só de item vazio (ON DELETE RESTRICT): o que tem
-- conteúdo é arquivado ou encerrado pelo status.
CREATE TABLE cursos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulo        VARCHAR(120) NOT NULL,
    slug          VARCHAR(80)  NOT NULL,
    resumo        VARCHAR(300) NOT NULL,
    descricao     TEXT,
    status        VARCHAR(20)  NOT NULL,
    capa_chave    VARCHAR(200),
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_cursos_slug UNIQUE (slug),
    CONSTRAINT ck_cursos_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_cursos_status CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'ARQUIVADO'))
);

CREATE TABLE turmas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    curso_id      BIGINT       NOT NULL REFERENCES cursos (id) ON DELETE RESTRICT,
    nome          VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_turmas_status CHECK (status IN ('PREPARACAO', 'ATIVA', 'ENCERRADA'))
);
CREATE INDEX ix_turmas_curso ON turmas (curso_id);

-- A ordem é única por turma; a restrição é conferida só no commit, para a reordenação trocar todas
-- as posições na mesma transação.
CREATE TABLE modulos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id      BIGINT       NOT NULL REFERENCES turmas (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150) NOT NULL,
    ordem         INT          NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_modulos_turma_ordem UNIQUE (turma_id, ordem) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_modulos_ordem CHECK (ordem > 0),
    CONSTRAINT ck_modulos_status CHECK (status IN ('ATIVO', 'INATIVO'))
);

CREATE TABLE aulas (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modulo_id     BIGINT       NOT NULL REFERENCES modulos (id) ON DELETE RESTRICT,
    titulo        VARCHAR(150) NOT NULL,
    descricao     TEXT,
    ordem         INT          NOT NULL,
    gratuita      BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_aulas_modulo_ordem UNIQUE (modulo_id, ordem) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_aulas_ordem CHECK (ordem > 0),
    CONSTRAINT ck_aulas_status CHECK (status IN ('ATIVO', 'INATIVO'))
);
`````

### 3. Domínio

`backend/src/main/java/br/com/celke/cursos/curso/domain/StatusCurso.java`:

`````java
package br.com.celke.cursos.curso.domain;

/** Só cursos {@code PUBLICADO} aparecem no catálogo público. */
public enum StatusCurso {
    RASCUNHO,
    PUBLICADO,
    ARQUIVADO
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/StatusTurma.java`:

`````java
package br.com.celke.cursos.curso.domain;

/** O catálogo mostra o conteúdo da turma {@code ATIVA} mais recente do curso. */
public enum StatusTurma {
    PREPARACAO,
    ATIVA,
    ENCERRADA
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/StatusConteudo.java`:

`````java
package br.com.celke.cursos.curso.domain;

/** Status de módulos e aulas: só os {@code ATIVO} aparecem para o aluno e no catálogo. */
public enum StatusConteudo {
    ATIVO,
    INATIVO
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Slug.java`:

`````java
package br.com.celke.cursos.curso.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Slug do curso na URL do site: letras minúsculas sem acento, números e hífens entre palavras. */
public final class Slug {

    public static final int TAMANHO_MAXIMO = 80;
    public static final String FORMATO = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private static final Pattern VALIDO = Pattern.compile(FORMATO);
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    private Slug() {}

    /** Gera o slug a partir do título: "Java & Spring: do Zero!" → "java-spring-do-zero". */
    public static String gerar(String titulo) {
        String semAcento = ACENTOS.matcher(Normalizer.normalize(titulo, Normalizer.Form.NFD))
                .replaceAll("");
        String slug = SEPARADORES.matcher(semAcento.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        if (slug.length() > TAMANHO_MAXIMO) {
            slug = slug.substring(0, TAMANHO_MAXIMO).replaceAll("-+$", "");
        }
        return slug;
    }

    public static boolean valido(String slug) {
        return slug != null
                && slug.length() <= TAMANHO_MAXIMO
                && VALIDO.matcher(slug).matches();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Ordenavel.java`:

`````java
package br.com.celke.cursos.curso.domain;

/** Item com posição dentro do pai (módulo na turma, aula no módulo), começando em 1. */
public interface Ordenavel {

    Long getId();

    int getOrdem();

    void definirOrdem(int ordem);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Ordenacao.java`:

`````java
package br.com.celke.cursos.curso.domain;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Regras de posição dos itens dentro do pai: sempre 1, 2, 3... sem buracos nem repetição. */
public final class Ordenacao {

    private Ordenacao() {}

    /**
     * Reordena os itens conforme a lista de ids, que precisa ter exatamente os itens atuais, sem
     * faltar, sobrar ou repetir.
     */
    public static void reordenar(List<? extends Ordenavel> atuais, List<Long> idsNaNovaOrdem) {
        Map<Long, Ordenavel> porId = atuais.stream().collect(Collectors.toMap(Ordenavel::getId, Function.identity()));
        boolean mesmoConjunto = idsNaNovaOrdem.size() == atuais.size()
                && new HashSet<>(idsNaNovaOrdem).size() == idsNaNovaOrdem.size()
                && porId.keySet().containsAll(idsNaNovaOrdem);
        if (!mesmoConjunto) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "A nova ordem deve ter todos os itens atuais, sem repetir nenhum.");
        }
        for (int i = 0; i < idsNaNovaOrdem.size(); i++) {
            porId.get(idsNaNovaOrdem.get(i)).definirOrdem(i + 1);
        }
    }

    /** Refaz a sequência 1, 2, 3... mantendo a ordem atual (depois de uma exclusão). */
    public static void compactar(List<? extends Ordenavel> restantes) {
        List<? extends Ordenavel> ordenados = restantes.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
        for (int i = 0; i < ordenados.size(); i++) {
            ordenados.get(i).definirOrdem(i + 1);
        }
    }

    /** Próxima posição: depois da maior existente. */
    public static int proxima(List<? extends Ordenavel> atuais) {
        return atuais.stream().mapToInt(Ordenavel::getOrdem).max().orElse(0) + 1;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Curso.java`:

`````java
package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cursos")
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 300)
    private String resumo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCurso status;

    /** Chave da capa no bucket ({@code capas/<curso>/<uuid>.jpg}); nula sem capa. */
    @Column(name = "capa_chave", length = 200)
    private String capaChave;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Curso() {}

    /** Curso novo nasce em rascunho, fora do catálogo. */
    public Curso(String titulo, String slug, String resumo, String descricao, Instant agora) {
        atualizar(titulo, slug, resumo, descricao, agora);
        this.status = StatusCurso.RASCUNHO;
        this.criadoEm = agora;
    }

    public void atualizar(String titulo, String slug, String resumo, String descricao, Instant agora) {
        this.titulo = titulo.strip();
        this.slug = slug;
        this.resumo = resumo.strip();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusCurso novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    public void definirCapa(String chave, Instant agora) {
        this.capaChave = chave;
        this.atualizadoEm = agora;
    }

    public boolean isPublicado() {
        return status == StatusCurso.PUBLICADO;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getSlug() {
        return slug;
    }

    public String getResumo() {
        return resumo;
    }

    public String getDescricao() {
        return descricao;
    }

    public StatusCurso getStatus() {
        return status;
    }

    public String getCapaChave() {
        return capaChave;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Turma.java`:

`````java
package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Oferta de um curso, com os próprios módulos e aulas (e, adiante, as matrículas). */
@Entity
@Table(name = "turmas")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTurma status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Turma() {}

    /** Turma nova nasce em preparação. */
    public Turma(Curso curso, String nome, Instant agora) {
        this.curso = curso;
        this.nome = nome.strip();
        this.status = StatusTurma.PREPARACAO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void renomear(String nome, Instant agora) {
        this.nome = nome.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusTurma novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Curso getCurso() {
        return curso;
    }

    public String getNome() {
        return nome;
    }

    public StatusTurma getStatus() {
        return status;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Modulo.java`:

`````java
package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "modulos")
public class Modulo implements Ordenavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false)
    private int ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConteudo status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Modulo() {}

    public Modulo(Turma turma, String titulo, int ordem, Instant agora) {
        this.turma = turma;
        this.titulo = titulo.strip();
        this.ordem = ordem;
        this.status = StatusConteudo.ATIVO;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void renomear(String titulo, Instant agora) {
        this.titulo = titulo.strip();
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusConteudo novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    @Override
    public void definirOrdem(int ordem) {
        this.ordem = ordem;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Turma getTurma() {
        return turma;
    }

    public String getTitulo() {
        return titulo;
    }

    @Override
    public int getOrdem() {
        return ordem;
    }

    public StatusConteudo getStatus() {
        return status;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/domain/Aula.java`:

`````java
package br.com.celke.cursos.curso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "aulas")
public class Aula implements Ordenavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private int ordem;

    /** Aula aberta a quem não tem matrícula (degustação). */
    @Column(nullable = false)
    private boolean gratuita;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConteudo status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Aula() {}

    public Aula(Modulo modulo, String titulo, String descricao, boolean gratuita, int ordem, Instant agora) {
        this.modulo = modulo;
        this.ordem = ordem;
        this.status = StatusConteudo.ATIVO;
        this.criadoEm = agora;
        atualizar(titulo, descricao, gratuita, agora);
    }

    public void atualizar(String titulo, String descricao, boolean gratuita, Instant agora) {
        this.titulo = titulo.strip();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        this.gratuita = gratuita;
        this.atualizadoEm = agora;
    }

    public void alterarStatus(StatusConteudo novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    @Override
    public void definirOrdem(int ordem) {
        this.ordem = ordem;
    }

    @Override
    public Long getId() {
        return id;
    }

    public Modulo getModulo() {
        return modulo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public int getOrdem() {
        return ordem;
    }

    public boolean isGratuita() {
        return gratuita;
    }

    public StatusConteudo getStatus() {
        return status;
    }
}
`````

### 4. Repositórios

`backend/src/main/java/br/com/celke/cursos/curso/infra/CursoRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursoRepository extends JpaRepository<Curso, Long> {

    Page<Curso> findByStatus(StatusCurso status, Pageable pageable);

    Optional<Curso> findBySlugAndStatus(String slug, StatusCurso status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/infra/TurmaRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import jakarta.persistence.LockModeType;
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

    /** Trava a turma enquanto a ordem dos módulos muda (criar, reordenar, excluir). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turma t where t.id = :id")
    Optional<Turma> travar(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/infra/ModuloRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    List<Modulo> findByTurmaIdOrderByOrdem(Long turmaId);

    List<Modulo> findByTurmaIdAndStatusOrderByOrdem(Long turmaId, StatusConteudo status);

    boolean existsByTurmaId(Long turmaId);

    /** Trava o módulo enquanto a ordem das aulas muda (criar, reordenar, excluir). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Modulo m where m.id = :id")
    Optional<Modulo> travar(Long id);
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/infra/AulaRepository.java`:

`````java
package br.com.celke.cursos.curso.infra;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    List<Aula> findByModuloIdOrderByOrdem(Long moduloId);

    /** Aulas de vários módulos numa consulta só (catálogo). */
    List<Aula> findByModuloIdInAndStatusOrderByOrdem(Collection<Long> moduloIds, StatusConteudo status);

    boolean existsByModuloId(Long moduloId);
}
`````

### 5. Processamento de imagem

`backend/src/main/java/br/com/celke/cursos/shared/imagem/ImagemInvalidaException.java`:

`````java
package br.com.celke.cursos.shared.imagem;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import org.springframework.http.HttpStatus;

/** Arquivo que não é uma imagem aceita (formato, tamanho ou dimensões). */
public class ImagemInvalidaException extends RegraNegocioException {

    public ImagemInvalidaException(String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/imagem/ProcessadorImagem.java`:

`````java
package br.com.celke.cursos.shared.imagem;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Normaliza imagens enviadas pelos usuários (capa do curso, foto de perfil): aceita só JPEG e PNG,
 * reduz para caber no limite e regrava em JPEG. A re-codificação descarta todos os metadados (EXIF
 * com GPS e dados da câmera, comentários) e qualquer conteúdo escondido no arquivo original.
 */
@Component
public class ProcessadorImagem {

    /** Formatos aceitos, pelo conteúdo real do arquivo (não pela extensão ou Content-Type). */
    private static final Set<String> FORMATOS = Set.of("jpeg", "png");

    /** Lado máximo do original: protege contra "bombas de descompressão" (arquivo pequeno, imagem gigante). */
    static final int LADO_MAXIMO_ORIGINAL = 8000;

    private static final float QUALIDADE_JPEG = 0.85f;

    /**
     * Devolve a imagem em JPEG, reduzida para caber em {@code larguraMaxima} × {@code alturaMaxima}
     * sem distorcer (imagem menor que o limite não é ampliada).
     */
    public byte[] normalizar(byte[] original, int larguraMaxima, int alturaMaxima) {
        BufferedImage imagem = ler(original);
        double escala = Math.min(
                1.0, Math.min((double) larguraMaxima / imagem.getWidth(), (double) alturaMaxima / imagem.getHeight()));
        int largura = Math.max(1, (int) Math.round(imagem.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(imagem.getHeight() * escala));
        return gravarJpeg(redesenhar(imagem, largura, altura));
    }

    private static BufferedImage ler(byte[] original) {
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
            }
            ImageReader leitor = leitores.next();
            try {
                if (!FORMATOS.contains(leitor.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
                }
                leitor.setInput(entrada, true, true);
                // As dimensões vêm do cabeçalho: a imagem gigante é recusada antes de ocupar a memória
                if (leitor.getWidth(0) > LADO_MAXIMO_ORIGINAL || leitor.getHeight(0) > LADO_MAXIMO_ORIGINAL) {
                    throw new ImagemInvalidaException("A imagem pode ter no máximo %d × %d pixels."
                            .formatted(LADO_MAXIMO_ORIGINAL, LADO_MAXIMO_ORIGINAL));
                }
                return leitor.read(0);
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImagemInvalidaException invalida) {
                throw invalida;
            }
            throw new ImagemInvalidaException("Não foi possível ler a imagem. Envie um JPEG ou PNG válido.");
        }
    }

    /** Copia para RGB no tamanho final; a transparência do PNG vira fundo branco. */
    private static BufferedImage redesenhar(BufferedImage origem, int largura, int altura) {
        BufferedImage destino = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largura, altura);
            g.drawImage(origem, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** Grava só os pixels: sem metadados de entrada, o JPEG sai sem EXIF. */
    private static byte[] gravarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(QUALIDADE_JPEG);
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
`````

### 6. Casos de uso

`backend/src/main/java/br/com/celke/cursos/curso/application/DadosCurso.java`:

`````java
package br.com.celke.cursos.curso.application;

/** Dados editáveis do curso; sem slug, ele é gerado a partir do título. */
public record DadosCurso(String titulo, String slug, String resumo, String descricao) {}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/DadosAula.java`:

`````java
package br.com.celke.cursos.curso.application;

public record DadosAula(String titulo, String descricao, boolean gratuita) {}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/CursoAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Slug;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de cursos pelo ADM. */
@Service
public class CursoAdmService {

    private final CursoRepository cursos;
    private final TurmaRepository turmas;
    private final Clock clock;

    public CursoAdmService(CursoRepository cursos, TurmaRepository turmas, Clock clock) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.clock = clock;
    }

    /** Cursos em ordem alfabética, opcionalmente só os de um status (a ordenação do cliente é ignorada). */
    @Transactional(readOnly = true)
    public Page<Curso> listar(StatusCurso status, Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("titulo", "id"));
        return status == null ? cursos.findAll(ordenada) : cursos.findByStatus(status, ordenada);
    }

    @Transactional(readOnly = true)
    public Curso buscar(Long id) {
        return cursos.findById(id).orElseThrow(CursoAdmService::naoEncontrado);
    }

    @Transactional
    public Curso criar(DadosCurso dados) {
        String slug = slugDe(dados);
        if (cursos.existsBySlug(slug)) {
            throw slugEmUso(slug);
        }
        return cursos.save(new Curso(dados.titulo(), slug, dados.resumo(), dados.descricao(), clock.instant()));
    }

    @Transactional
    public Curso atualizar(Long id, DadosCurso dados) {
        Curso curso = buscar(id);
        String slug = slugDe(dados);
        if (cursos.existsBySlugAndIdNot(slug, id)) {
            throw slugEmUso(slug);
        }
        curso.atualizar(dados.titulo(), slug, dados.resumo(), dados.descricao(), clock.instant());
        return curso;
    }

    @Transactional
    public Curso alterarStatus(Long id, StatusCurso status) {
        Curso curso = buscar(id);
        curso.alterarStatus(status, clock.instant());
        return curso;
    }

    /**
     * Exclui um curso sem turmas e devolve a chave da capa, que o chamador apaga do bucket. Curso com
     * turmas é arquivado, para não perder matrículas e histórico.
     */
    @Transactional
    public Optional<String> excluir(Long id) {
        Curso curso = buscar(id);
        if (turmas.existsByCursoId(id)) {
            throw new ConflitoException("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
        }
        cursos.delete(curso);
        return Optional.ofNullable(curso.getCapaChave());
    }

    /** Troca a capa e devolve a chave da anterior, que o chamador apaga do bucket. */
    @Transactional
    public Optional<String> trocarCapa(Long id, String novaChave) {
        Curso curso = buscar(id);
        Optional<String> anterior = Optional.ofNullable(curso.getCapaChave());
        curso.definirCapa(novaChave, clock.instant());
        return anterior;
    }

    private static String slugDe(DadosCurso dados) {
        String slug = dados.slug() == null || dados.slug().isBlank() ? Slug.gerar(dados.titulo()) : dados.slug();
        if (!Slug.valido(slug)) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "Não foi possível gerar o slug a partir do título. Informe o slug.");
        }
        return slug;
    }

    private static ConflitoException slugEmUso(String slug) {
        return new ConflitoException("Já existe um curso com o slug \"%s\".".formatted(slug));
    }

    static NaoEncontradoException naoEncontrado() {
        return new NaoEncontradoException("Curso não encontrado.");
    }
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

    /** Exclui uma turma sem módulos; turma com conteúdo é encerrada. */
    @Transactional
    public void excluir(Long id) {
        Turma turma = buscar(id);
        if (modulos.existsByTurmaId(id)) {
            throw new ConflitoException("A turma tem módulos. Exclua os módulos ou encerre a turma.");
        }
        turmas.delete(turma);
    }

    private Turma buscar(Long id) {
        return turmas.findById(id).orElseThrow(TurmaAdmService::naoEncontrada);
    }

    static NaoEncontradoException naoEncontrada() {
        return new NaoEncontradoException("Turma não encontrada.");
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
        modulos.delete(modulo);
        modulos.flush();
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

`backend/src/main/java/br/com/celke/cursos/curso/application/AulaAdmService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.Ordenacao;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
import java.util.List;
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

    /** Exclui a aula e refaz a sequência das que ficam. */
    @Transactional
    public void excluir(Long id) {
        Aula aula = buscar(id);
        Long moduloId = aula.getModulo().getId();
        modulos.travar(moduloId);
        aulas.delete(aula);
        aulas.flush();
        Ordenacao.compactar(aulas.findByModuloIdOrderByOrdem(moduloId));
    }

    private Aula buscar(Long id) {
        return aulas.findById(id).orElseThrow(() -> new NaoEncontradoException("Aula não encontrada."));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/CapaCursoService.java`:

`````java
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
`````

`backend/src/main/java/br/com/celke/cursos/curso/application/CatalogoService.java`:

`````java
package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final ModuloRepository modulos;
    private final AulaRepository aulas;

    public CatalogoService(
            CursoRepository cursos, TurmaRepository turmas, ModuloRepository modulos, AulaRepository aulas) {
        this.cursos = cursos;
        this.turmas = turmas;
        this.modulos = modulos;
        this.aulas = aulas;
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
        List<ModuloDetalhe> conteudo = turmas.findFirstByCursoIdAndStatusOrderByIdDesc(curso.getId(), StatusTurma.ATIVA)
                .map(turma -> conteudo(modulos.findByTurmaIdAndStatusOrderByOrdem(turma.getId(), StatusConteudo.ATIVO)))
                .orElse(List.of());
        return new CursoDetalhe(curso, conteudo);
    }

    private List<ModuloDetalhe> conteudo(List<Modulo> ativos) {
        Map<Long, List<Aula>> aulasPorModulo = aulas
                .findByModuloIdInAndStatusOrderByOrdem(
                        ativos.stream().map(Modulo::getId).toList(), StatusConteudo.ATIVO)
                .stream()
                .collect(Collectors.groupingBy(aula -> aula.getModulo().getId()));
        return ativos.stream()
                .map(modulo -> new ModuloDetalhe(modulo, aulasPorModulo.getOrDefault(modulo.getId(), List.of())))
                .toList();
    }

    public record CursoDetalhe(Curso curso, List<ModuloDetalhe> modulos) {}

    public record ModuloDetalhe(Modulo modulo, List<Aula> aulas) {}
}
`````

### 7. API

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoRequests.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.DadosAula;
import br.com.celke.cursos.curso.application.DadosCurso;
import br.com.celke.cursos.curso.domain.Slug;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Corpos das rotas de gestão de cursos ({@code /api/adm}). */
public final class CursoRequests {

    private CursoRequests() {}

    /** Sem slug, ele é gerado a partir do título. */
    public record Curso(
            @NotBlank @Size(max = 120) String titulo,

            @Size(max = Slug.TAMANHO_MAXIMO)
            @Pattern(regexp = Slug.FORMATO, message = "deve ter só letras minúsculas, números e hífens")
            String slug,

            @NotBlank @Size(max = 300) String resumo,
            @Size(max = 20_000) String descricao) {

        public DadosCurso dados() {
            return new DadosCurso(titulo, slug, resumo, descricao);
        }
    }

    public record AlterarStatusCurso(@NotNull StatusCurso status) {}

    public record Turma(@NotBlank @Size(max = 120) String nome) {}

    public record AlterarStatusTurma(@NotNull StatusTurma status) {}

    public record Modulo(@NotBlank @Size(max = 150) String titulo) {}

    /** Sem {@code gratuita}, a aula é paga (só para matriculados). */
    public record Aula(
            @NotBlank @Size(max = 150) String titulo,
            @Size(max = 20_000) String descricao,
            Boolean gratuita) {

        public DadosAula dados() {
            return new DadosAula(titulo, descricao, Boolean.TRUE.equals(gratuita));
        }
    }

    public record AlterarStatusConteudo(@NotNull StatusConteudo status) {}

    /** Ids de todos os itens do pai, na nova ordem. */
    public record Ordem(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {}

    public record IniciarEnvioCapa(
            @NotBlank String contentType, @Positive long tamanho) {}

    public record ConfirmarCapa(@NotBlank @Size(max = 200) String chave) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoResponses.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.application.CapaCursoService.EnvioCapa;
import br.com.celke.cursos.curso.application.CatalogoService.CursoDetalhe;
import br.com.celke.cursos.curso.application.CatalogoService.ModuloDetalhe;
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

        static ModuloCatalogo de(ModuloDetalhe detalhe) {
            return new ModuloCatalogo(
                    detalhe.modulo().getTitulo(),
                    detalhe.aulas().stream()
                            .map(aula -> new AulaCatalogo(aula.getId(), aula.getTitulo(), aula.isGratuita()))
                            .toList());
        }
    }

    public record AulaCatalogo(Long id, String titulo, boolean gratuita) {}

    private static String enderecoCapa(br.com.celke.cursos.curso.domain.Curso curso) {
        return curso.getCapaChave() == null ? null : "/api/cursos/%s/capa".formatted(curso.getSlug());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CursoAdmController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.CursoAdm;
import br.com.celke.cursos.curso.api.CursoResponses.EnvioCapaResponse;
import br.com.celke.cursos.curso.application.CapaCursoService;
import br.com.celke.cursos.curso.application.CursoAdmService;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm/cursos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Cursos")
public class CursoAdmController {

    private final CursoAdmService cursoAdmService;
    private final CapaCursoService capaCursoService;

    public CursoAdmController(CursoAdmService cursoAdmService, CapaCursoService capaCursoService) {
        this.cursoAdmService = cursoAdmService;
        this.capaCursoService = capaCursoService;
    }

    @GetMapping
    @Operation(summary = "Listar cursos", description = "Em ordem alfabética; filtro opcional por status.")
    public Pagina<CursoAdm> listar(
            @RequestParam(required = false) StatusCurso status, @ParameterObject Pageable pagina) {
        return Pagina.de(cursoAdmService.listar(status, pagina).map(this::resposta));
    }

    @PostMapping
    @Operation(summary = "Criar curso", description = "Nasce em rascunho; sem slug, ele é gerado a partir do título.")
    public ResponseEntity<CursoAdm> criar(@Valid @RequestBody CursoRequests.Curso request) {
        Curso curso = cursoAdmService.criar(request.dados());
        return ResponseEntity.created(URI.create("/api/adm/cursos/" + curso.getId()))
                .body(resposta(curso));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar curso")
    public CursoAdm buscar(@PathVariable Long id) {
        return resposta(cursoAdmService.buscar(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar curso")
    public CursoAdm atualizar(@PathVariable Long id, @Valid @RequestBody CursoRequests.Curso request) {
        return resposta(cursoAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publicar, arquivar ou voltar para rascunho")
    public CursoAdm alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusCurso request) {
        return resposta(cursoAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir curso", description = "Só sem turmas; curso com turmas é arquivado.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        capaCursoService.excluirCurso(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/capa/envio")
    @Operation(
            summary = "Iniciar o envio da capa",
            description = "Devolve a URL para enviar a imagem (JPEG ou PNG, até 5 MB) direto ao armazenamento.")
    public EnvioCapaResponse iniciarEnvioCapa(
            @PathVariable Long id, @Valid @RequestBody CursoRequests.IniciarEnvioCapa request) {
        return EnvioCapaResponse.de(capaCursoService.iniciarEnvio(id, request.contentType(), request.tamanho()));
    }

    @PutMapping("/{id}/capa")
    @Operation(
            summary = "Confirmar a capa enviada",
            description = "Confere a imagem, reduz para até 1280 × 720 e regrava em JPEG sem metadados.")
    public CursoAdm confirmarCapa(@PathVariable Long id, @Valid @RequestBody CursoRequests.ConfirmarCapa request) {
        return resposta(capaCursoService.confirmar(id, request.chave()));
    }

    @DeleteMapping("/{id}/capa")
    @Operation(summary = "Remover a capa")
    public ResponseEntity<Void> removerCapa(@PathVariable Long id) {
        capaCursoService.remover(id);
        return ResponseEntity.noContent().build();
    }

    private CursoAdm resposta(Curso curso) {
        return CursoAdm.de(curso, capaCursoService.url(curso));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/TurmaAdmController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Turma;
import br.com.celke.cursos.curso.application.TurmaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Turmas")
public class TurmaAdmController {

    private final TurmaAdmService turmaAdmService;

    public TurmaAdmController(TurmaAdmService turmaAdmService) {
        this.turmaAdmService = turmaAdmService;
    }

    @GetMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Listar as turmas do curso")
    public List<Turma> listar(@PathVariable Long cursoId) {
        return turmaAdmService.listar(cursoId).stream().map(Turma::de).toList();
    }

    @PostMapping("/cursos/{cursoId}/turmas")
    @Operation(summary = "Criar turma", description = "Nasce em preparação.")
    public ResponseEntity<Turma> criar(@PathVariable Long cursoId, @Valid @RequestBody CursoRequests.Turma request) {
        Turma turma = Turma.de(turmaAdmService.criar(cursoId, request.nome()));
        return ResponseEntity.created(URI.create("/api/adm/turmas/" + turma.id()))
                .body(turma);
    }

    @PutMapping("/turmas/{id}")
    @Operation(summary = "Renomear turma")
    public Turma renomear(@PathVariable Long id, @Valid @RequestBody CursoRequests.Turma request) {
        return Turma.de(turmaAdmService.renomear(id, request.nome()));
    }

    @PatchMapping("/turmas/{id}/status")
    @Operation(summary = "Ativar ou encerrar turma", description = "O catálogo mostra a turma ativa mais recente.")
    public Turma alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusTurma request) {
        return Turma.de(turmaAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/turmas/{id}")
    @Operation(summary = "Excluir turma", description = "Só sem módulos; turma com conteúdo é encerrada.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        turmaAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/ModuloAdmController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Modulo;
import br.com.celke.cursos.curso.application.ModuloAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Módulos")
public class ModuloAdmController {

    private final ModuloAdmService moduloAdmService;

    public ModuloAdmController(ModuloAdmService moduloAdmService) {
        this.moduloAdmService = moduloAdmService;
    }

    @GetMapping("/turmas/{turmaId}/modulos")
    @Operation(summary = "Listar os módulos da turma", description = "Na ordem do curso.")
    public List<Modulo> listar(@PathVariable Long turmaId) {
        return moduloAdmService.listar(turmaId).stream().map(Modulo::de).toList();
    }

    @PostMapping("/turmas/{turmaId}/modulos")
    @Operation(summary = "Criar módulo", description = "Entra no fim da turma.")
    public ResponseEntity<Modulo> criar(@PathVariable Long turmaId, @Valid @RequestBody CursoRequests.Modulo request) {
        Modulo modulo = Modulo.de(moduloAdmService.criar(turmaId, request.titulo()));
        return ResponseEntity.created(URI.create("/api/adm/modulos/" + modulo.id()))
                .body(modulo);
    }

    @PutMapping("/turmas/{turmaId}/modulos/ordem")
    @Operation(summary = "Reordenar os módulos", description = "Recebe os ids de todos os módulos da turma.")
    public List<Modulo> reordenar(@PathVariable Long turmaId, @Valid @RequestBody CursoRequests.Ordem request) {
        return moduloAdmService.reordenar(turmaId, request.ids()).stream()
                .map(Modulo::de)
                .toList();
    }

    @PutMapping("/modulos/{id}")
    @Operation(summary = "Renomear módulo")
    public Modulo renomear(@PathVariable Long id, @Valid @RequestBody CursoRequests.Modulo request) {
        return Modulo.de(moduloAdmService.renomear(id, request.titulo()));
    }

    @PatchMapping("/modulos/{id}/status")
    @Operation(summary = "Ativar ou inativar módulo")
    public Modulo alterarStatus(
            @PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusConteudo request) {
        return Modulo.de(moduloAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/modulos/{id}")
    @Operation(summary = "Excluir módulo", description = "Só sem aulas.")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        moduloAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/AulaAdmController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.Aula;
import br.com.celke.cursos.curso.application.AulaAdmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adm")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ADM — Aulas")
public class AulaAdmController {

    private final AulaAdmService aulaAdmService;

    public AulaAdmController(AulaAdmService aulaAdmService) {
        this.aulaAdmService = aulaAdmService;
    }

    @GetMapping("/modulos/{moduloId}/aulas")
    @Operation(summary = "Listar as aulas do módulo", description = "Na ordem do curso.")
    public List<Aula> listar(@PathVariable Long moduloId) {
        return aulaAdmService.listar(moduloId).stream().map(Aula::de).toList();
    }

    @PostMapping("/modulos/{moduloId}/aulas")
    @Operation(summary = "Criar aula", description = "Entra no fim do módulo.")
    public ResponseEntity<Aula> criar(@PathVariable Long moduloId, @Valid @RequestBody CursoRequests.Aula request) {
        Aula aula = Aula.de(aulaAdmService.criar(moduloId, request.dados()));
        return ResponseEntity.created(URI.create("/api/adm/aulas/" + aula.id())).body(aula);
    }

    @PutMapping("/modulos/{moduloId}/aulas/ordem")
    @Operation(summary = "Reordenar as aulas", description = "Recebe os ids de todas as aulas do módulo.")
    public List<Aula> reordenar(@PathVariable Long moduloId, @Valid @RequestBody CursoRequests.Ordem request) {
        return aulaAdmService.reordenar(moduloId, request.ids()).stream()
                .map(Aula::de)
                .toList();
    }

    @PutMapping("/aulas/{id}")
    @Operation(summary = "Atualizar aula")
    public Aula atualizar(@PathVariable Long id, @Valid @RequestBody CursoRequests.Aula request) {
        return Aula.de(aulaAdmService.atualizar(id, request.dados()));
    }

    @PatchMapping("/aulas/{id}/status")
    @Operation(summary = "Ativar ou inativar aula")
    public Aula alterarStatus(@PathVariable Long id, @Valid @RequestBody CursoRequests.AlterarStatusConteudo request) {
        return Aula.de(aulaAdmService.alterarStatus(id, request.status()));
    }

    @DeleteMapping("/aulas/{id}")
    @Operation(summary = "Excluir aula")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        aulaAdmService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/curso/api/CatalogoController.java`:

`````java
package br.com.celke.cursos.curso.api;

import br.com.celke.cursos.curso.api.CursoResponses.CursoCatalogo;
import br.com.celke.cursos.curso.api.CursoResponses.CursoCatalogoDetalhe;
import br.com.celke.cursos.curso.application.CapaCursoService;
import br.com.celke.cursos.curso.application.CatalogoService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Duration;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público: rotas sem login, declaradas no {@code SecurityConfig}. */
@RestController
@RequestMapping("/api/cursos")
@Tag(name = "Catálogo")
public class CatalogoController {

    private final CatalogoService catalogoService;
    private final CapaCursoService capaCursoService;

    public CatalogoController(CatalogoService catalogoService, CapaCursoService capaCursoService) {
        this.catalogoService = catalogoService;
        this.capaCursoService = capaCursoService;
    }

    @GetMapping
    @Operation(summary = "Cursos publicados", description = "Em ordem alfabética.")
    public Pagina<CursoCatalogo> listar(@ParameterObject Pageable pagina) {
        return Pagina.de(catalogoService.listar(pagina).map(CursoCatalogo::de));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Curso publicado", description = "Com os módulos e as aulas ativos da turma atual.")
    public CursoCatalogoDetalhe detalhar(@PathVariable String slug) {
        return CursoCatalogoDetalhe.de(catalogoService.detalhar(slug));
    }

    @GetMapping("/{slug}/capa")
    @Operation(summary = "Capa do curso", description = "Redireciona para a imagem (endereço temporário).")
    public ResponseEntity<Void> capa(@PathVariable String slug) {
        URI imagem = capaCursoService
                .url(catalogoService.buscar(slug))
                .map(url -> URI.create(url.toString()))
                .orElseThrow(() -> new NaoEncontradoException("Curso sem capa."));
        // O redirecionamento pode ficar em cache por bem menos tempo que a validade da URL (1 hora)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(imagem)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .build();
    }
}
`````

### 8. Fábricas de teste

`backend/src/test/java/br/com/celke/cursos/support/fixtures/CursoFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.Turma;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Fábrica de cursos, turmas, módulos e aulas para testes unitários (sem banco: o id é preenchido aqui). */
public final class CursoFixture {

    public static final Instant CRIADO_EM = Instant.parse("2026-01-01T00:00:00Z");

    private CursoFixture() {}

    public static Curso curso(long id, String slug) {
        Curso curso = new Curso("Curso " + slug, slug, "Resumo de " + slug, null, CRIADO_EM);
        ReflectionTestUtils.setField(curso, "id", id);
        return curso;
    }

    public static Curso publicado(Curso curso) {
        curso.alterarStatus(StatusCurso.PUBLICADO, CRIADO_EM);
        return curso;
    }

    public static Curso comCapa(Curso curso, String chave) {
        curso.definirCapa(chave, CRIADO_EM);
        return curso;
    }

    public static Turma turma(long id, Curso curso) {
        Turma turma = new Turma(curso, "Turma " + id, CRIADO_EM);
        ReflectionTestUtils.setField(turma, "id", id);
        return turma;
    }

    public static Modulo modulo(long id, Turma turma, int ordem) {
        Modulo modulo = new Modulo(turma, "Módulo " + id, ordem, CRIADO_EM);
        ReflectionTestUtils.setField(modulo, "id", id);
        return modulo;
    }

    public static Aula aula(long id, Modulo modulo, int ordem) {
        Aula aula = new Aula(modulo, "Aula " + id, null, false, ordem, CRIADO_EM);
        ReflectionTestUtils.setField(aula, "id", id);
        return aula;
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/support/fixtures/ImagemFixture.java`:

`````java
package br.com.celke.cursos.support.fixtures;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Imagens geradas em memória para os testes (metade azul, para conferir o conteúdo depois). */
public final class ImagemFixture {

    private ImagemFixture() {}

    public static byte[] png(int largura, int altura) {
        return imagem("png", largura, altura, BufferedImage.TYPE_INT_RGB);
    }

    /** PNG com canal alfa (fundo transparente). */
    public static byte[] pngTransparente(int largura, int altura) {
        return imagem("png", largura, altura, BufferedImage.TYPE_INT_ARGB);
    }

    public static byte[] jpeg(int largura, int altura) {
        return imagem("jpeg", largura, altura, BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] imagem(String formato, int largura, int altura, int tipo) {
        BufferedImage imagem = new BufferedImage(largura, altura, tipo);
        var g = imagem.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, Math.max(1, largura / 2), altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagem, formato, saida);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }

    /** JPEG com um segmento APP1 (Exif) contendo {@code conteudo}, logo depois do marcador SOI. */
    public static byte[] jpegComExif(int largura, int altura, String conteudo) {
        byte[] jpeg = jpeg(largura, altura);
        byte[] dados = ("Exif\0\0" + conteudo).getBytes(StandardCharsets.ISO_8859_1);
        int tamanho = dados.length + 2;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpeg, 0, 2);
        saida.write(0xFF);
        saida.write(0xE1);
        saida.write(tamanho >> 8);
        saida.write(tamanho & 0xFF);
        saida.write(dados, 0, dados.length);
        saida.write(jpeg, 2, jpeg.length - 2);
        return saida.toByteArray();
    }
}
`````

### 9. Testes unitários

`backend/src/test/java/br/com/celke/cursos/curso/domain/SlugTest.java`:

`````java
package br.com.celke.cursos.curso.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "acentos e cedilha viram letras simples | Programação Ágil       | programacao-agil",
                "símbolos viram um único hífen          | Java & Spring: do Zero! | java-spring-do-zero",
                "hífens das pontas são removidos        | --Docker--             | docker",
                "números são mantidos                   | React 19 em 2026       | react-19-em-2026",
                "só símbolos gera slug vazio            | !!!                    | ''",
            })
    void geraAPartirDoTitulo(String caso, String titulo, String esperado) {
        assertThat(Slug.gerar(titulo)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("título longo é cortado em 80 caracteres sem terminar em hífen")
    void cortaNoTamanhoMaximo() {
        String titulo = "a".repeat(79) + " bcd";
        String slug = Slug.gerar(titulo);
        assertThat(slug).isEqualTo("a".repeat(79));
        assertThat(Slug.valido(slug)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
        "java-spring, true",
        "curso2026,   true",
        "Java,        false",
        "java--spring, false",
        "-java,       false",
        "java-,       false",
        "java_spring, false",
        "'',          false",
    })
    void validaOFormato(String slug, boolean esperado) {
        assertThat(Slug.valido(slug)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nulo e acima de 80 caracteres são inválidos; 80 é o limite")
    void validaTamanho() {
        assertThat(Slug.valido(null)).isFalse();
        assertThat(Slug.valido("a".repeat(80))).isTrue();
        assertThat(Slug.valido("a".repeat(81))).isFalse();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/domain/OrdenacaoTest.java`:

`````java
package br.com.celke.cursos.curso.domain;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class OrdenacaoTest {

    private final Turma turma = turma(1, curso(1, "curso"));

    /** Módulos com ids 10, 20, 30 nas posições 1, 2, 3. */
    private List<Modulo> tresModulos() {
        return List.of(modulo(10, turma, 1), modulo(20, turma, 2), modulo(30, turma, 3));
    }

    private static List<Long> idsPelaOrdem(List<Modulo> modulos) {
        return modulos.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .map(Modulo::getId)
                .toList();
    }

    @Nested
    class Reordenar {

        @Test
        @DisplayName("aplica a nova ordem a partir da posição 1")
        void aplicaNovaOrdem() {
            List<Modulo> modulos = tresModulos();
            Ordenacao.reordenar(modulos, List.of(30L, 10L, 20L));
            assertThat(idsPelaOrdem(modulos)).containsExactly(30L, 10L, 20L);
            assertThat(modulos).extracting(Modulo::getOrdem).containsExactly(2, 3, 1);
        }

        static Stream<Arguments> listasInvalidas() {
            return Stream.of(
                    Arguments.of("faltando um item", List.of(10L, 20L)),
                    Arguments.of("com item a mais", List.of(10L, 20L, 30L, 40L)),
                    Arguments.of("com item repetido", List.of(10L, 10L, 20L)),
                    Arguments.of("com item de outro pai", List.of(10L, 20L, 99L)),
                    Arguments.of("vazia", List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("listasInvalidas")
        void recusaListaQueNaoTemExatamenteOsItens(String caso, List<Long> ids) {
            List<Modulo> modulos = tresModulos();
            assertThatThrownBy(() -> Ordenacao.reordenar(modulos, ids))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A nova ordem deve ter todos os itens atuais, sem repetir nenhum.")
                    .extracting("status")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(modulos).extracting(Modulo::getOrdem).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("pai sem itens aceita a lista vazia")
        void paiVazio() {
            Ordenacao.reordenar(List.of(), List.of());
        }
    }

    @Test
    @DisplayName("compactar refaz 1, 2, 3 mantendo a ordem relativa")
    void compactar() {
        List<Modulo> restantes = List.of(modulo(30, turma, 5), modulo(10, turma, 1), modulo(20, turma, 3));
        Ordenacao.compactar(restantes);
        assertThat(idsPelaOrdem(restantes)).containsExactly(10L, 20L, 30L);
        assertThat(restantes).extracting(Modulo::getOrdem).containsExactly(3, 1, 2);
    }

    @Test
    @DisplayName("próxima posição vem depois da maior; lista vazia começa em 1")
    void proxima() {
        assertThat(Ordenacao.proxima(List.of())).isEqualTo(1);
        assertThat(Ordenacao.proxima(List.of(modulo(10, turma, 1), modulo(20, turma, 4))))
                .isEqualTo(5);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/api/CursoRequestsTest.java`:

`````java
package br.com.celke.cursos.curso.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CursoRequestsTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> camposInvalidos(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static CursoRequests.Curso curso(String titulo, String slug, String resumo, String descricao) {
        return new CursoRequests.Curso(titulo, slug, resumo, descricao);
    }

    @Test
    @DisplayName("curso válido, com ou sem slug, não tem erros")
    void cursoValido() {
        assertThat(camposInvalidos(curso("Java", "java", "Resumo", "Descrição")))
                .isEmpty();
        assertThat(camposInvalidos(curso("Java", null, "Resumo", null))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "título em branco,             ' ',   java, Resumo, titulo",
        "resumo em branco,             Java,  java, ' ',    resumo",
        "slug com maiúscula,           Java,  Java, Resumo, slug",
        "slug com espaço,              Java,  'a b', Resumo, slug",
        "slug com hífen duplo,         Java,  a--b, Resumo, slug",
    })
    void camposDoCurso(String caso, String titulo, String slug, String resumo, String campoInvalido) {
        assertThat(camposInvalidos(curso(titulo, slug, resumo, null))).containsExactly(campoInvalido);
    }

    @Test
    @DisplayName("limites de tamanho do curso: título 120, slug 80, resumo 300, descrição 20 mil")
    void tamanhosDoCurso() {
        assertThat(camposInvalidos(curso("a".repeat(120), "a".repeat(80), "a".repeat(300), "a".repeat(20_000))))
                .isEmpty();
        assertThat(camposInvalidos(curso("a".repeat(121), "a".repeat(81), "a".repeat(301), "a".repeat(20_001))))
                .containsExactlyInAnyOrder("titulo", "slug", "resumo", "descricao");
    }

    @Test
    @DisplayName("status, turma, módulo e aula validam os campos obrigatórios")
    void outrosCorpos() {
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusCurso(null))).containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusTurma(null))).containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.AlterarStatusConteudo(null)))
                .containsExactly("status");
        assertThat(camposInvalidos(new CursoRequests.Turma(" "))).containsExactly("nome");
        assertThat(camposInvalidos(new CursoRequests.Turma("a".repeat(121)))).containsExactly("nome");
        assertThat(camposInvalidos(new CursoRequests.Modulo("a".repeat(151)))).containsExactly("titulo");
        assertThat(camposInvalidos(new CursoRequests.Aula("", "a".repeat(20_001), true)))
                .containsExactlyInAnyOrder("titulo", "descricao");
        assertThat(camposInvalidos(new CursoRequests.Aula("Aula", null, false))).isEmpty();
    }

    @Test
    @DisplayName("aula sem o campo gratuita é paga")
    void aulaSemGratuitaEPaga() {
        assertThat(new CursoRequests.Aula("Aula", null, null).dados().gratuita())
                .isFalse();
        assertThat(new CursoRequests.Aula("Aula", null, true).dados().gratuita())
                .isTrue();
    }

    @Test
    @DisplayName("ordem exige de 1 a 500 ids, sem nulos")
    void ordem() {
        assertThat(camposInvalidos(new CursoRequests.Ordem(List.of()))).containsExactly("ids");
        assertThat(camposInvalidos(new CursoRequests.Ordem(null))).containsExactly("ids");
        assertThat(camposInvalidos(new CursoRequests.Ordem(Arrays.asList(1L, null))))
                .containsExactly("ids[1].<list element>");
        List<Long> quinhentos = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            quinhentos.add(i);
        }
        assertThat(camposInvalidos(new CursoRequests.Ordem(quinhentos))).isEmpty();
        quinhentos.add(501L);
        assertThat(camposInvalidos(new CursoRequests.Ordem(quinhentos))).containsExactly("ids");
    }

    @ParameterizedTest(name = "tamanho {0}")
    @CsvSource({"0, false", "-1, false", "1, true"})
    void envioDaCapaExigeTamanhoPositivo(long tamanho, boolean valido) {
        Set<String> invalidos = camposInvalidos(new CursoRequests.IniciarEnvioCapa("image/png", tamanho));
        if (valido) {
            assertThat(invalidos).isEmpty();
        } else {
            assertThat(invalidos).containsExactly("tamanho");
        }
    }

    @Test
    @DisplayName("envio e confirmação da capa exigem tipo e chave")
    void capa() {
        assertThat(camposInvalidos(new CursoRequests.IniciarEnvioCapa(" ", 10))).containsExactly("contentType");
        assertThat(camposInvalidos(new CursoRequests.ConfirmarCapa(""))).containsExactly("chave");
        assertThat(camposInvalidos(new CursoRequests.ConfirmarCapa("a".repeat(201))))
                .containsExactly("chave");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/CursoAdmServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CursoAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private CursoRepository cursos;

    @Mock
    private TurmaRepository turmas;

    private CursoAdmService service;

    @BeforeEach
    void setUp() {
        service = new CursoAdmService(cursos, turmas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Nested
    class Criar {

        @Test
        @DisplayName("sem slug, gera a partir do título; nasce em rascunho com os textos aparados")
        void geraSlug() {
            when(cursos.existsBySlug("programacao-java")).thenReturn(false);
            when(cursos.save(any(Curso.class))).thenAnswer(i -> i.getArgument(0));

            Curso curso = service.criar(new DadosCurso("  Programação Java ", null, " Resumo ", "  "));

            assertThat(curso.getSlug()).isEqualTo("programacao-java");
            assertThat(curso.getTitulo()).isEqualTo("Programação Java");
            assertThat(curso.getResumo()).isEqualTo("Resumo");
            assertThat(curso.getDescricao()).isNull();
            assertThat(curso.getStatus()).isEqualTo(StatusCurso.RASCUNHO);
            assertThat(curso.getCriadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("usa o slug informado")
        void usaSlugInformado() {
            when(cursos.existsBySlug("java")).thenReturn(false);
            when(cursos.save(any(Curso.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.criar(new DadosCurso("Programação Java", "java", "Resumo", null))
                            .getSlug())
                    .isEqualTo("java");
        }

        @Test
        @DisplayName("rejeita slug já cadastrado")
        void rejeitaSlugJaCadastrado() {
            when(cursos.existsBySlug("java")).thenReturn(true);

            assertThatThrownBy(() -> service.criar(new DadosCurso("Java", null, "Resumo", null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Já existe um curso com o slug \"java\".");
            verify(cursos, never()).save(any());
        }

        @Test
        @DisplayName("título só com símbolos e sem slug pede o slug")
        void tituloSemLetras() {
            assertThatThrownBy(() -> service.criar(new DadosCurso("!!!", " ", "Resumo", null)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Não foi possível gerar o slug a partir do título. Informe o slug.");
            verify(cursos, never()).save(any());
        }
    }

    @Nested
    class Atualizar {

        @Test
        @DisplayName("altera os dados e mantém o mesmo slug do próprio curso")
        void atualiza() {
            Curso curso = curso(1, "java");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(cursos.existsBySlugAndIdNot("java", 1L)).thenReturn(false);

            service.atualizar(1L, new DadosCurso("Java Moderno", "java", "Novo resumo", "Descrição"));

            assertThat(curso.getTitulo()).isEqualTo("Java Moderno");
            assertThat(curso.getDescricao()).isEqualTo("Descrição");
            assertThat(curso.getAtualizadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("rejeita slug de outro curso")
        void rejeitaSlugDeOutroCurso() {
            Curso curso = curso(1, "java");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(cursos.existsBySlugAndIdNot("react", 1L)).thenReturn(true);

            assertThatThrownBy(() -> service.atualizar(1L, new DadosCurso("Java", "react", "Resumo", null)))
                    .isInstanceOf(ConflitoException.class);
            assertThat(curso.getSlug()).isEqualTo("java");
        }

        @Test
        @DisplayName("curso inexistente responde não encontrado")
        void inexistente() {
            when(cursos.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizar(9L, new DadosCurso("Java", null, "Resumo", null)))
                    .isInstanceOf(NaoEncontradoException.class)
                    .hasMessage("Curso não encontrado.");
        }
    }

    @Test
    @DisplayName("listar ignora a ordenação do cliente e filtra pelo status quando informado")
    void listar() {
        Pageable pedido = PageRequest.of(2, 10, Sort.by("senha"));
        Pageable esperado = PageRequest.of(2, 10, Sort.by("titulo", "id"));
        when(cursos.findAll(esperado)).thenReturn(Page.empty());
        when(cursos.findByStatus(StatusCurso.PUBLICADO, esperado)).thenReturn(Page.empty());

        service.listar(null, pedido);
        service.listar(StatusCurso.PUBLICADO, pedido);

        verify(cursos).findAll(esperado);
        verify(cursos).findByStatus(StatusCurso.PUBLICADO, esperado);
    }

    @Test
    @DisplayName("alterar status muda o status e a data de atualização")
    void alterarStatus() {
        Curso curso = curso(1, "java");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));

        service.alterarStatus(1L, StatusCurso.PUBLICADO);

        assertThat(curso.getStatus()).isEqualTo(StatusCurso.PUBLICADO);
        assertThat(curso.getAtualizadoEm()).isEqualTo(AGORA);
    }

    @Nested
    class Excluir {

        @Test
        @DisplayName("curso sem turmas é excluído e devolve a capa para apagar")
        void semTurmas() {
            Curso curso = comCapa(curso(1, "java"), "capas/1/a.jpg");
            when(cursos.findById(1L)).thenReturn(Optional.of(curso));
            when(turmas.existsByCursoId(1L)).thenReturn(false);

            assertThat(service.excluir(1L)).contains("capas/1/a.jpg");
            verify(cursos).delete(curso);
        }

        @Test
        @DisplayName("curso com turmas não é excluído")
        void comTurmas() {
            when(cursos.findById(1L)).thenReturn(Optional.of(curso(1, "java")));
            when(turmas.existsByCursoId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.excluir(1L))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("O curso tem turmas. Exclua as turmas vazias ou arquive o curso.");
            verify(cursos, never()).delete(any());
        }
    }

    @Test
    @DisplayName("trocar capa grava a nova chave e devolve a anterior")
    void trocarCapa() {
        Curso curso = comCapa(curso(1, "java"), "capas/1/antiga.jpg");
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));

        assertThat(service.trocarCapa(1L, "capas/1/nova.jpg")).contains("capas/1/antiga.jpg");
        assertThat(curso.getCapaChave()).isEqualTo("capas/1/nova.jpg");
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
    @DisplayName("turma inexistente responde não encontrada")
    void inexistente() {
        when(turmas.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(9L, "Nova"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Turma não encontrada.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/ModuloAdmServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.curso.infra.TurmaRepository;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.time.Clock;
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
class ModuloAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private TurmaRepository turmas;

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private ModuloAdmService service;
    private final Turma turma = turma(5, curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new ModuloAdmService(turmas, modulos, aulas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("módulo novo entra depois do último, com a turma travada")
    void criarNoFim() {
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(modulo(10, turma, 1), modulo(20, turma, 2)));
        when(modulos.save(any(Modulo.class))).thenAnswer(i -> i.getArgument(0));

        Modulo modulo = service.criar(5L, " Introdução ");

        assertThat(modulo.getOrdem()).isEqualTo(3);
        assertThat(modulo.getTitulo()).isEqualTo("Introdução");
        assertThat(modulo.getStatus()).isEqualTo(StatusConteudo.ATIVO);
    }

    @Test
    @DisplayName("turma inexistente: listar e criar respondem não encontrada")
    void turmaInexistente() {
        when(turmas.existsById(9L)).thenReturn(false);
        when(turmas.travar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, "Módulo")).isInstanceOf(NaoEncontradoException.class);
        verify(modulos, never()).save(any());
    }

    @Test
    @DisplayName("reordenar devolve os módulos na nova ordem")
    void reordenar() {
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(modulo(10, turma, 1), modulo(20, turma, 2)));

        assertThat(service.reordenar(5L, List.of(20L, 10L)))
                .extracting(Modulo::getId, Modulo::getOrdem)
                .containsExactly(tuple(20L, 1), tuple(10L, 2));
    }

    @Test
    @DisplayName("reordenar com lista incompleta não muda nada")
    void reordenarIncompleto() {
        List<Modulo> atuais = List.of(modulo(10, turma, 1), modulo(20, turma, 2));
        when(turmas.travar(5L)).thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(atuais);

        assertThatThrownBy(() -> service.reordenar(5L, List.of(20L))).isInstanceOf(RegraNegocioException.class);
        assertThat(atuais).extracting(Modulo::getOrdem).containsExactly(1, 2);
    }

    @Test
    @DisplayName("excluir módulo sem aulas refaz a sequência dos que ficam")
    void excluirCompacta() {
        Modulo excluido = modulo(10, turma, 1);
        Modulo restante = modulo(20, turma, 2);
        when(modulos.findById(10L)).thenReturn(Optional.of(excluido));
        when(aulas.existsByModuloId(10L)).thenReturn(false);
        when(modulos.findByTurmaIdOrderByOrdem(5L)).thenReturn(List.of(restante));

        service.excluir(10L);

        verify(turmas).travar(5L);
        verify(modulos).delete(excluido);
        assertThat(restante.getOrdem()).isEqualTo(1);
    }

    @Test
    @DisplayName("módulo com aulas não é excluído")
    void naoExcluiComAulas() {
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo(10, turma, 1)));
        when(aulas.existsByModuloId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(10L))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("O módulo tem aulas. Exclua as aulas ou inative o módulo.");
        verify(modulos, never()).delete(any());
    }

    @Test
    @DisplayName("renomeia e inativa")
    void renomearEInativar() {
        Modulo modulo = modulo(10, turma, 1);
        when(modulos.findById(10L)).thenReturn(Optional.of(modulo));

        service.renomear(10L, "Novo");
        service.alterarStatus(10L, StatusConteudo.INATIVO);

        assertThat(modulo.getTitulo()).isEqualTo("Novo");
        assertThat(modulo.getStatus()).isEqualTo(StatusConteudo.INATIVO);
    }

    @Test
    @DisplayName("módulo inexistente responde não encontrado")
    void inexistente() {
        when(modulos.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renomear(9L, "Novo"))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Módulo não encontrado.");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/AulaAdmServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Aula;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import java.time.Clock;
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
class AulaAdmServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private AulaAdmService service;
    private final Modulo modulo = modulo(10, turma(5, curso(1, "java")), 1);

    @BeforeEach
    void setUp() {
        service = new AulaAdmService(modulos, aulas, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("aula nova entra no fim do módulo, com os textos aparados")
    void criarNoFim() {
        when(modulos.travar(10L)).thenReturn(Optional.of(modulo));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(aula(100, modulo, 1)));
        when(aulas.save(any(Aula.class))).thenAnswer(i -> i.getArgument(0));

        Aula aula = service.criar(10L, new DadosAula(" Boas-vindas ", "  ", true));

        assertThat(aula.getOrdem()).isEqualTo(2);
        assertThat(aula.getTitulo()).isEqualTo("Boas-vindas");
        assertThat(aula.getDescricao()).isNull();
        assertThat(aula.isGratuita()).isTrue();
        assertThat(aula.getStatus()).isEqualTo(StatusConteudo.ATIVO);
    }

    @Test
    @DisplayName("módulo inexistente: listar, criar e reordenar respondem não encontrado")
    void moduloInexistente() {
        when(modulos.existsById(9L)).thenReturn(false);
        when(modulos.travar(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.criar(9L, new DadosAula("Aula", null, false)))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> service.reordenar(9L, List.of(1L))).isInstanceOf(NaoEncontradoException.class);
        verify(aulas, never()).save(any());
    }

    @Test
    @DisplayName("reordenar devolve as aulas na nova ordem")
    void reordenar() {
        when(modulos.travar(10L)).thenReturn(Optional.of(modulo));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(aula(100, modulo, 1), aula(200, modulo, 2)));

        assertThat(service.reordenar(10L, List.of(200L, 100L)))
                .extracting(Aula::getId)
                .containsExactly(200L, 100L);
    }

    @Test
    @DisplayName("excluir refaz a sequência das que ficam")
    void excluirCompacta() {
        Aula excluida = aula(100, modulo, 1);
        Aula restante = aula(200, modulo, 2);
        when(aulas.findById(100L)).thenReturn(Optional.of(excluida));
        when(aulas.findByModuloIdOrderByOrdem(10L)).thenReturn(List.of(restante));

        service.excluir(100L);

        verify(modulos).travar(10L);
        verify(aulas).delete(excluida);
        assertThat(restante.getOrdem()).isEqualTo(1);
    }

    @Test
    @DisplayName("atualiza os dados e o status")
    void atualizar() {
        Aula aula = aula(100, modulo, 1);
        when(aulas.findById(100L)).thenReturn(Optional.of(aula));

        service.atualizar(100L, new DadosAula("Nova", " Texto ", true));
        service.alterarStatus(100L, StatusConteudo.INATIVO);

        assertThat(aula.getTitulo()).isEqualTo("Nova");
        assertThat(aula.getDescricao()).isEqualTo("Texto");
        assertThat(aula.isGratuita()).isTrue();
        assertThat(aula.getStatus()).isEqualTo(StatusConteudo.INATIVO);
    }

    @Test
    @DisplayName("aula inexistente responde não encontrada")
    void inexistente() {
        when(aulas.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(9L))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Aula não encontrada.");
        verify(aulas, never()).delete(any());
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/CapaCursoServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.comCapa;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.armazenamento.StorageService.InfoArquivo;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.imagem.ImagemInvalidaException;
import br.com.celke.cursos.shared.imagem.ProcessadorImagem;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapaCursoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-03-10T12:00:00Z");
    private static final String CHAVE = "uploads/capas/1/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b";
    private static final byte[] ORIGINAL = {1, 2, 3};
    private static final byte[] PROCESSADA = {4, 5};

    @Mock
    private CursoAdmService cursoAdmService;

    @Mock
    private StorageService storage;

    @Mock
    private ProcessadorImagem processador;

    private CapaCursoService service;

    @BeforeEach
    void setUp() {
        service = new CapaCursoService(cursoAdmService, storage, processador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static URL url(String endereco) throws Exception {
        return URI.create(endereco).toURL();
    }

    @Nested
    class IniciarEnvio {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"image/jpeg, 1", "image/png, 5242880"})
        void devolveUrlComTipoEValidade(String tipo, long tamanho) throws Exception {
            when(storage.urlUpload(startsWith("uploads/capas/1/"), eq(tipo), eq(CapaCursoService.VALIDADE_UPLOAD)))
                    .thenReturn(url("http://s3/assinada"));

            var envio = service.iniciarEnvio(1L, tipo, tamanho);

            assertThat(envio.chave()).matches("uploads/capas/1/[0-9a-f-]{36}");
            assertThat(envio.contentType()).isEqualTo(tipo);
            assertThat(envio.expiraEm()).isEqualTo(AGORA.plus(CapaCursoService.VALIDADE_UPLOAD));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"image/gif", "image/svg+xml", "text/html", "image/webp"})
        void recusaTipoNaoAceito(String tipo) {
            assertThatThrownBy(() -> service.iniciarEnvio(1L, tipo, 10))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envie uma imagem JPEG ou PNG.");
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("recusa um byte acima de 5 MB")
        void recusaTamanhoAcimaDoLimite() {
            assertThatThrownBy(() -> service.iniciarEnvio(1L, "image/png", 5L * 1024 * 1024 + 1))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem pode ter no máximo 5 MB.");
        }

        @Test
        @DisplayName("curso inexistente responde não encontrado")
        void cursoInexistente() {
            when(cursoAdmService.buscar(9L)).thenThrow(CursoAdmService.naoEncontrado());

            assertThatThrownBy(() -> service.iniciarEnvio(9L, "image/png", 10))
                    .isInstanceOf(NaoEncontradoException.class);
            verify(storage, never()).urlUpload(anyString(), anyString(), any());
        }
    }

    @Nested
    class Confirmar {

        @Test
        @DisplayName("grava a imagem processada em capas/, apaga o original e a capa anterior")
        void confirma() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720)).thenReturn(PROCESSADA);
            when(cursoAdmService.trocarCapa(eq(1L), startsWith("capas/1/")))
                    .thenReturn(Optional.of("capas/1/antiga.jpg"));

            service.confirmar(1L, CHAVE);

            ArgumentCaptor<String> capa = ArgumentCaptor.forClass(String.class);
            verify(storage).gravar(capa.capture(), eq(PROCESSADA), eq("image/jpeg"));
            assertThat(capa.getValue()).matches("capas/1/[0-9a-f-]{36}\\.jpg");
            verify(storage).excluir(CHAVE);
            verify(storage).excluir("capas/1/antiga.jpg");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "chave de outro curso,          uploads/capas/2/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b",
            "fora da pasta de uploads,      capas/1/0b1e8f6a-4c2d-4e8b-9a51-7f3c2d1e0a9b",
            "subindo de pasta,              uploads/capas/1/../../videos/x",
            "nome que não é um uuid,        uploads/capas/1/imagem.png",
        })
        void recusaChaveQueNaoFoiGeradaParaOCurso(String caso, String chave) {
            assertThatThrownBy(() -> service.confirmar(1L, chave))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("Envio de capa inválido. Comece um novo envio.");
            verify(storage, never()).info(anyString());
            verify(storage, never()).excluir(anyString());
        }

        @Test
        @DisplayName("arquivo não enviado pede um novo envio")
        void naoEnviado() {
            when(storage.info(CHAVE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem não foi enviada. Comece um novo envio.");
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("arquivo acima de 5 MB é apagado sem ser baixado")
        void acimaDoLimite() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(5L * 1024 * 1024 + 1, "image/png")));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE))
                    .isInstanceOf(ImagemInvalidaException.class)
                    .hasMessage("A imagem pode ter no máximo 5 MB.");
            verify(storage, never()).baixar(anyString());
            verify(storage).excluir(CHAVE);
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("imagem inválida apaga o original e não troca a capa")
        void imagemInvalida() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720))
                    .thenThrow(new ImagemInvalidaException("Envie uma imagem JPEG ou PNG."));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE)).isInstanceOf(ImagemInvalidaException.class);
            verify(storage).excluir(CHAVE);
            verify(storage, never()).gravar(anyString(), any(), anyString());
            verify(cursoAdmService, never()).trocarCapa(any(), any());
        }

        @Test
        @DisplayName("falha ao gravar no banco apaga a imagem processada")
        void falhaNoBanco() {
            when(storage.info(CHAVE)).thenReturn(Optional.of(new InfoArquivo(3, "image/png")));
            when(storage.baixar(CHAVE)).thenReturn(ORIGINAL);
            when(processador.normalizar(ORIGINAL, 1280, 720)).thenReturn(PROCESSADA);
            when(cursoAdmService.trocarCapa(eq(1L), anyString())).thenThrow(new IllegalStateException("banco fora"));

            assertThatThrownBy(() -> service.confirmar(1L, CHAVE)).isInstanceOf(IllegalStateException.class);
            ArgumentCaptor<String> capa = ArgumentCaptor.forClass(String.class);
            verify(storage).gravar(capa.capture(), eq(PROCESSADA), eq("image/jpeg"));
            verify(storage).excluir(capa.getValue());
        }
    }

    @Test
    @DisplayName("remover limpa a capa e apaga o arquivo")
    void remover() {
        when(cursoAdmService.trocarCapa(1L, null)).thenReturn(Optional.of("capas/1/a.jpg"));

        service.remover(1L);

        verify(storage).excluir("capas/1/a.jpg");
    }

    @Test
    @DisplayName("excluir curso sem capa não mexe no bucket")
    void excluirCursoSemCapa() {
        when(cursoAdmService.excluir(1L)).thenReturn(Optional.empty());

        service.excluirCurso(1L);

        verify(storage, never()).excluir(anyString());
    }

    @Test
    @DisplayName("url só existe para curso com capa")
    void url() throws Exception {
        when(storage.urlDownload("capas/1/a.jpg", CapaCursoService.VALIDADE_DOWNLOAD))
                .thenReturn(url("http://s3/capa"));

        assertThat(service.url(curso(1, "java"))).isEmpty();
        assertThat(service.url(comCapa(curso(1, "java"), "capas/1/a.jpg"))).contains(url("http://s3/capa"));
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/application/CatalogoServiceTest.java`:

`````java
package br.com.celke.cursos.curso.application;

import static br.com.celke.cursos.support.fixtures.CursoFixture.aula;
import static br.com.celke.cursos.support.fixtures.CursoFixture.curso;
import static br.com.celke.cursos.support.fixtures.CursoFixture.modulo;
import static br.com.celke.cursos.support.fixtures.CursoFixture.publicado;
import static br.com.celke.cursos.support.fixtures.CursoFixture.turma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.StatusConteudo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.AulaRepository;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.curso.infra.ModuloRepository;
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
    private ModuloRepository modulos;

    @Mock
    private AulaRepository aulas;

    private CatalogoService service;
    private final Curso curso = publicado(curso(1, "java"));

    @BeforeEach
    void setUp() {
        service = new CatalogoService(cursos, turmas, modulos, aulas);
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
    @DisplayName("agrupa as aulas ativas por módulo, na ordem; módulo sem aulas vem com lista vazia")
    void agrupaAulas() {
        Turma turma = turma(5, curso);
        var primeiro = modulo(10, turma, 1);
        var segundo = modulo(20, turma, 2);
        when(cursos.findBySlugAndStatus("java", StatusCurso.PUBLICADO)).thenReturn(Optional.of(curso));
        when(turmas.findFirstByCursoIdAndStatusOrderByIdDesc(1L, StatusTurma.ATIVA))
                .thenReturn(Optional.of(turma));
        when(modulos.findByTurmaIdAndStatusOrderByOrdem(5L, StatusConteudo.ATIVO))
                .thenReturn(List.of(primeiro, segundo));
        when(aulas.findByModuloIdInAndStatusOrderByOrdem(List.of(10L, 20L), StatusConteudo.ATIVO))
                .thenReturn(List.of(aula(100, primeiro, 1), aula(101, primeiro, 2)));

        var detalhe = service.detalhar("java");

        assertThat(detalhe.curso()).isSameAs(curso);
        assertThat(detalhe.modulos()).hasSize(2);
        assertThat(detalhe.modulos().get(0).aulas()).extracting(a -> a.getId()).containsExactly(100L, 101L);
        assertThat(detalhe.modulos().get(1).aulas()).isEmpty();
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/shared/imagem/ProcessadorImagemTest.java`:

`````java
package br.com.celke.cursos.shared.imagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.ImagemFixture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem();

    private static BufferedImage ler(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static boolean ehJpeg(byte[] conteudo) {
        return (conteudo[0] & 0xFF) == 0xFF && (conteudo[1] & 0xFF) == 0xD8;
    }

    @ParameterizedTest(name = "{0}×{1} → {2}×{3}")
    @CsvSource({
        "4000, 1000, 1280, 320", // limitada pela largura
        "1000, 2000, 360,  720", // limitada pela altura
        "1280, 720,  1280, 720", // exatamente no limite
        "300,  200,  300,  200", // menor que o limite: não amplia
    })
    void reduzSemDistorcer(int largura, int altura, int larguraFinal, int alturaFinal) throws IOException {
        byte[] resultado = processador.normalizar(ImagemFixture.png(largura, altura), 1280, 720);

        BufferedImage final_ = ler(resultado);
        assertThat(ehJpeg(resultado)).isTrue();
        assertThat(final_.getWidth()).isEqualTo(larguraFinal);
        assertThat(final_.getHeight()).isEqualTo(alturaFinal);
    }

    @Test
    @DisplayName("PNG transparente vira JPEG com fundo branco")
    void transparenciaViraBranco() throws IOException {
        byte[] resultado = processador.normalizar(ImagemFixture.pngTransparente(100, 100), 1280, 720);

        Color canto = new Color(ler(resultado).getRGB(99, 50));
        assertThat(canto.getRed()).isGreaterThan(240);
        assertThat(canto.getGreen()).isGreaterThan(240);
        assertThat(canto.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("descarta os metadados EXIF do JPEG original")
    void descartaExif() throws IOException {
        byte[] original = ImagemFixture.jpegComExif(200, 100, "GPS -23.5505 -46.6333");
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("Exif", "GPS -23.5505");

        byte[] resultado = processador.normalizar(original, 1280, 720);

        String bytes = new String(resultado, StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain("Exif").doesNotContain("GPS -23.5505");
        assertThat(ler(resultado).getWidth()).isEqualTo(200);
    }

    @Test
    @DisplayName("lado de 8000 pixels é aceito; 8001 é recusado antes de decodificar")
    void limiteDeDimensoes() throws IOException {
        assertThat(processador.normalizar(
                        ImagemFixture.imagem("png", 8000, 1, BufferedImage.TYPE_BYTE_GRAY), 1280, 720))
                .isNotEmpty();
        byte[] grande = ImagemFixture.imagem("png", 1, 8001, BufferedImage.TYPE_BYTE_GRAY);

        assertThatThrownBy(() -> processador.normalizar(grande, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem pode ter no máximo 8000 × 8000 pixels.");
    }

    @Test
    @DisplayName("recusa formato que não é JPEG nem PNG, mesmo sendo imagem")
    void recusaGif() throws IOException {
        byte[] gif = ImagemFixture.imagem("gif", 10, 10, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> processador.normalizar(gif, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Envie uma imagem JPEG ou PNG.");
    }

    @Test
    @DisplayName("recusa conteúdo que não é imagem (ex.: HTML com extensão de imagem)")
    void recusaNaoImagem() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processador.normalizar(html, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Envie uma imagem JPEG ou PNG.");
    }

    @Test
    @DisplayName("recusa PNG truncado")
    void recusaTruncado() throws IOException {
        byte[] png = ImagemFixture.png(200, 200);
        byte[] truncado = Arrays.copyOf(png, 40);

        assertThatThrownBy(() -> processador.normalizar(truncado, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Não foi possível ler a imagem. Envie um JPEG ou PNG válido.");
    }
}
`````

### 10. Testes de integração

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

/** Monta cursos, turmas, módulos e aulas pela API do ADM, para os testes do módulo curso. */
final class CursoITHelper {

    private final MockMvcTester mvc;
    private final String bearer;

    CursoITHelper(MockMvcTester mvc, String bearer) {
        this.mvc = mvc;
        this.bearer = bearer;
    }

    MvcTestResult enviar(String metodo, String uri, String corpo) {
        var requisicao = mvc.method(org.springframework.http.HttpMethod.valueOf(metodo))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer);
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return requisicao.exchange();
    }

    long criarCurso(String titulo, String slug) {
        String corpo = slug == null
                ? "{\"titulo\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, titulo)
                : "{\"titulo\":\"%s\",\"slug\":\"%s\",\"resumo\":\"Resumo de %s\"}".formatted(titulo, slug, titulo);
        return idCriado(enviar("POST", "/api/adm/cursos", corpo));
    }

    void publicar(long cursoId) {
        status("/api/adm/cursos/" + cursoId + "/status", "PUBLICADO");
    }

    long criarTurma(long cursoId, String nome) {
        return idCriado(enviar("POST", "/api/adm/cursos/" + cursoId + "/turmas", "{\"nome\":\"%s\"}".formatted(nome)));
    }

    void ativarTurma(long turmaId) {
        status("/api/adm/turmas/" + turmaId + "/status", "ATIVA");
    }

    long criarModulo(long turmaId, String titulo) {
        return idCriado(
                enviar("POST", "/api/adm/turmas/" + turmaId + "/modulos", "{\"titulo\":\"%s\"}".formatted(titulo)));
    }

    long criarAula(long moduloId, String titulo, boolean gratuita) {
        return idCriado(enviar(
                "POST",
                "/api/adm/modulos/" + moduloId + "/aulas",
                "{\"titulo\":\"%s\",\"gratuita\":%s}".formatted(titulo, gratuita)));
    }

    /** {@code PATCH <rota>} com o novo status; confere o 200. */
    void status(String rota, String status) {
        assertThat(enviar("PATCH", rota, "{\"status\":\"%s\"}".formatted(status)))
                .hasStatusOk();
    }

    static long idCriado(MvcTestResult resposta) {
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(corpo(resposta), "$.id")).longValue();
    }

    static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CursoAcessoIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Cobre: matriz de papéis de todas as rotas de cursos — ADM ({@code /api/adm}: 401 anônimo, 403
 * ALUNO, ADMIN passa pela segurança) e catálogo público (mesma resposta para os três).
 * Não cobre: o comportamento de cada rota (CursoCrudIT, ConteudoOrdemIT, CapaCursoIT, CatalogoIT).
 * Dado criado: a cada caso, um curso it-<stamp> com turma, módulo e aula (ids reais nas rotas).
 */
class CursoAcessoIT extends IntegrationTest {

    private String tokenAdmin;
    private String tokenAluno;
    private long curso;
    private long turma;
    private long modulo;
    private long aula;
    private String slug;

    @BeforeEach
    void criarEstrutura() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
        tokenAluno = login(UsuariosSeed.ALUNO_EMAIL, UsuariosSeed.SENHA);
        CursoITHelper adm = new CursoITHelper(mvc, bearer(tokenAdmin));
        slug = stamp();
        curso = adm.criarCurso("IT Curso " + slug, slug);
        turma = adm.criarTurma(curso, "Turma");
        modulo = adm.criarModulo(turma, "Módulo");
        aula = adm.criarAula(modulo, "Aula", false);
    }

    /**
     * Rotas do ADM com corpo válido e o status esperado para o ADMIN. As exclusões de curso, turma e
     * módulo dão 409 porque cada um tem filhos: passam pela segurança sem apagar a estrutura.
     */
    static Stream<Arguments> rotasAdm() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/adm/cursos", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/cursos",
                        "{\"titulo\":\"IT {stamp}\",\"resumo\":\"R\"}",
                        HttpStatus.CREATED),
                Arguments.of(HttpMethod.GET, "/api/adm/cursos/{c}", null, HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PUT,
                        "/api/adm/cursos/{c}",
                        "{\"titulo\":\"IT\",\"slug\":\"{slug}\",\"resumo\":\"R\"}",
                        HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PATCH, "/api/adm/cursos/{c}/status", "{\"status\":\"RASCUNHO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/cursos/{c}", null, HttpStatus.CONFLICT),
                Arguments.of(
                        HttpMethod.POST,
                        "/api/adm/cursos/{c}/capa/envio",
                        "{\"contentType\":\"image/png\",\"tamanho\":10}",
                        HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/cursos/{c}/capa", "{\"chave\":\"x\"}", HttpStatus.BAD_REQUEST),
                Arguments.of(HttpMethod.DELETE, "/api/adm/cursos/{c}/capa", null, HttpStatus.NO_CONTENT),
                Arguments.of(HttpMethod.GET, "/api/adm/cursos/{c}/turmas", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/cursos/{c}/turmas", "{\"nome\":\"T\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/turmas/{t}", "{\"nome\":\"T\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/turmas/{t}/status", "{\"status\":\"ATIVA\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/turmas/{t}", null, HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.GET, "/api/adm/turmas/{t}/modulos", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/turmas/{t}/modulos", "{\"titulo\":\"M\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/turmas/{t}/modulos/ordem", "{\"ids\":[{m}]}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/modulos/{m}", "{\"titulo\":\"M\"}", HttpStatus.OK),
                Arguments.of(
                        HttpMethod.PATCH, "/api/adm/modulos/{m}/status", "{\"status\":\"INATIVO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/modulos/{m}", null, HttpStatus.CONFLICT),
                Arguments.of(HttpMethod.GET, "/api/adm/modulos/{m}/aulas", null, HttpStatus.OK),
                Arguments.of(HttpMethod.POST, "/api/adm/modulos/{m}/aulas", "{\"titulo\":\"A\"}", HttpStatus.CREATED),
                Arguments.of(HttpMethod.PUT, "/api/adm/modulos/{m}/aulas/ordem", "{\"ids\":[{a}]}", HttpStatus.OK),
                Arguments.of(HttpMethod.PUT, "/api/adm/aulas/{a}", "{\"titulo\":\"A\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.PATCH, "/api/adm/aulas/{a}/status", "{\"status\":\"INATIVO\"}", HttpStatus.OK),
                Arguments.of(HttpMethod.DELETE, "/api/adm/aulas/{a}", null, HttpStatus.NO_CONTENT));
    }

    private String preencher(String modelo) {
        return modelo == null
                ? null
                : modelo.replace("{c}", String.valueOf(curso))
                        .replace("{t}", String.valueOf(turma))
                        .replace("{m}", String.valueOf(modulo))
                        .replace("{a}", String.valueOf(aula))
                        .replace("{slug}", slug)
                        .replace("{stamp}", stamp());
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult chamar(
            HttpMethod metodo, String rota, String corpo, String token) {
        var requisicao = mvc.method(metodo).uri(preencher(rota));
        if (corpo != null) {
            requisicao = requisicao.contentType(MediaType.APPLICATION_JSON).content(preencher(corpo));
        }
        if (token != null) {
            requisicao = requisicao.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return requisicao.exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: anônimo recebe 401")
    void admAnonimo(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, null))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Autenticação necessária.");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ALUNO recebe 403")
    void admAluno(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAluno))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Acesso negado.");
    }

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("rotasAdm")
    @DisplayName("ADM: ADMIN passa pela segurança")
    void admAdmin(HttpMethod metodo, String rota, String corpo, HttpStatus admin) {
        assertThat(chamar(metodo, rota, corpo, tokenAdmin)).hasStatus(admin);
    }

    @Test
    @DisplayName("ALUNO recebe 403 mesmo com corpo inválido: o papel é conferido antes do corpo")
    void papelAntesDoCorpo() {
        assertThat(chamar(HttpMethod.POST, "/api/adm/cursos", "{}", tokenAluno)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("papeis")
    @DisplayName("catálogo: mesma resposta para anônimo, ALUNO e ADMIN")
    void catalogoPublico(String papel) {
        new CursoITHelper(mvc, bearer(tokenAdmin)).publicar(curso);
        String token = switch (papel) {
            case "ALUNO" -> tokenAluno;
            case "ADMIN" -> tokenAdmin;
            default -> null;
        };

        assertThat(chamar(HttpMethod.GET, "/api/cursos", null, token)).hasStatusOk();
        assertThat(chamar(HttpMethod.GET, "/api/cursos/{slug}", null, token))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.slug")
                .isEqualTo(slug);
        assertThat(chamar(HttpMethod.GET, "/api/cursos/{slug}/capa", null, token))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Curso sem capa.");
    }

    static Stream<String> papeis() {
        return Stream.of("anônimo", "ALUNO", "ADMIN");
    }

    @Test
    @DisplayName("catálogo: métodos não declarados exigem token")
    void metodoNaoDeclarado() {
        assertThat(chamar(HttpMethod.POST, "/api/cursos", "{}", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(chamar(HttpMethod.DELETE, "/api/cursos/{slug}", null, null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CursoCrudIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
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
        assertThat(adm.enviar("GET", "/api/adm/cursos?status=PUBLICADO&size=100", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.itens[*].slug")
                .asArray()
                .contains(stamp);
        String rascunho = stamp();
        adm.criarCurso("IT Rascunho " + rascunho, rascunho);
        var rascunhos = adm.enviar("GET", "/api/adm/cursos?status=RASCUNHO&size=100", null);
        assertThat(rascunhos)
                .bodyJson()
                .extractingPath("$.itens[*].slug")
                .asArray()
                .contains(rascunho)
                .doesNotContain(stamp);
        assertThat(rascunhos)
                .bodyJson()
                .extractingPath("$.itens[*].status")
                .asArray()
                .containsOnly("RASCUNHO");

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + id, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", "/api/adm/cursos/" + id, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"status\":404,\"detail\":\"Curso não encontrado.\",\"instance\":\"/api/adm/cursos/%d\"}"
                                .formatted(id));
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

`backend/src/test/java/br/com/celke/cursos/curso/ConteudoOrdemIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Cobre: posição de módulos e aulas — criação no fim, reordenação (restrição única adiável no
 * PostgreSQL), lista inválida, exclusão que refaz a sequência e criação simultânea sem posição
 * repetida (trava do pai).
 * Não cobre: regras puras de ordenação (OrdenacaoTest).
 * Dado criado: curso it-<stamp> com uma turma.
 */
class ConteudoOrdemIT extends IntegrationTest {

    private CursoITHelper adm;
    private long turma;

    @BeforeEach
    void criarTurma() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        String slug = stamp();
        turma = adm.criarTurma(adm.criarCurso("IT Curso " + slug, slug), "Turma");
    }

    private String modulos() {
        return "/api/adm/turmas/" + turma + "/modulos";
    }

    @Test
    @DisplayName("módulos: entram no fim, são reordenados e a exclusão refaz a sequência")
    void ordemDosModulos() {
        long a = adm.criarModulo(turma, "A");
        long b = adm.criarModulo(turma, "B");
        long c = adm.criarModulo(turma, "C");
        assertThat(adm.enviar("GET", modulos(), null)).bodyJson().isStrictlyEqualTo("""
                        [{"id":%d,"turmaId":%d,"titulo":"A","ordem":1,"status":"ATIVO"},
                         {"id":%d,"turmaId":%d,"titulo":"B","ordem":2,"status":"ATIVO"},
                         {"id":%d,"turmaId":%d,"titulo":"C","ordem":3,"status":"ATIVO"}]
                        """.formatted(
                        a, turma, b, turma, c, turma));

        // Troca todas as posições numa transação: a restrição única só é conferida no commit
        assertThat(adm.enviar("PUT", modulos() + "/ordem", "{\"ids\":[%d,%d,%d]}".formatted(c, a, b)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].titulo")
                .asArray()
                .containsExactly("C", "A", "B");
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3);

        assertThat(adm.enviar("DELETE", "/api/adm/modulos/" + a, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .isLenientlyEqualTo("[{\"titulo\":\"C\",\"ordem\":1},{\"titulo\":\"B\",\"ordem\":2}]");
    }

    @Test
    @DisplayName("reordenar com lista incompleta, repetida ou com item de outra turma responde 400 sem mudar nada")
    void reordenacaoInvalida() {
        long a = adm.criarModulo(turma, "A");
        long b = adm.criarModulo(turma, "B");
        String slug = stamp();
        long deOutraTurma = adm.criarModulo(adm.criarTurma(adm.criarCurso("IT " + slug, slug), "T"), "X");

        for (String ids :
                List.of("[%d]".formatted(a), "[%d,%d]".formatted(a, a), "[%d,%d]".formatted(a, deOutraTurma))) {
            assertThat(adm.enviar("PUT", modulos() + "/ordem", "{\"ids\":" + ids + "}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("A nova ordem deve ter todos os itens atuais, sem repetir nenhum.");
        }
        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly((int) a, (int) b);
    }

    @Test
    @DisplayName("aulas: entram no fim com os dados normalizados, são reordenadas e a exclusão refaz a sequência")
    void ordemDasAulas() {
        long modulo = adm.criarModulo(turma, "Módulo");
        String aulas = "/api/adm/modulos/" + modulo + "/aulas";
        var criacao =
                adm.enviar("POST", aulas, "{\"titulo\":\" Boas-vindas \",\"descricao\":\"  \",\"gratuita\":true}");
        long a = CursoITHelper.idCriado(criacao);
        assertThat(criacao).bodyJson().isStrictlyEqualTo("""
                        {"id":%d,"moduloId":%d,"titulo":"Boas-vindas","descricao":null,"ordem":1,
                         "gratuita":true,"status":"ATIVO"}
                        """.formatted(a, modulo));
        long b = adm.criarAula(modulo, "B", false);
        long c = adm.criarAula(modulo, "C", false);

        assertThat(adm.enviar("PUT", aulas + "/ordem", "{\"ids\":[%d,%d,%d]}".formatted(b, c, a)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3);
        assertThat(adm.enviar("DELETE", "/api/adm/aulas/" + c, null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(adm.enviar("GET", aulas, null))
                .bodyJson()
                .isLenientlyEqualTo("[{\"id\":%d,\"ordem\":1},{\"id\":%d,\"ordem\":2}]".formatted(b, a));
    }

    @Test
    @DisplayName("módulos criados ao mesmo tempo recebem posições distintas")
    void criacaoSimultanea() throws Exception {
        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String titulo = "Simultâneo " + i;
            tarefas.add(() -> adm.criarModulo(turma, titulo));
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var futuro : executor.invokeAll(tarefas)) {
                futuro.get();
            }
        }

        assertThat(adm.enviar("GET", modulos(), null))
                .bodyJson()
                .extractingPath("$[*].ordem")
                .asArray()
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CatalogoIT.java`:

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
import org.springframework.http.HttpStatus;

/**
 * Cobre: catálogo público — só cursos publicados na lista e no detalhe; o detalhe mostra só os
 * módulos e as aulas ativos da turma ativa mais recente, na ordem, sem dados internos.
 * Não cobre: capa (CapaCursoIT), matriz de papéis (CursoAcessoIT).
 * Dado criado: cursos it-<stamp>.
 */
class CatalogoIT extends IntegrationTest {

    private CursoITHelper adm;

    @BeforeEach
    void entrarComoAdmin() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
    }

    /** Slugs de todas as páginas do catálogo, sem login. */
    private List<String> slugsNoCatalogo() {
        List<String> slugs = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            var resposta = mvc.get().uri("/api/cursos?size=100&page=" + pagina).exchange();
            assertThat(resposta).hasStatusOk();
            String corpo = CursoITHelper.corpo(resposta);
            slugs.addAll(JsonPath.read(corpo, "$.itens[*].slug"));
            if (pagina + 1 >= (int) JsonPath.read(corpo, "$.totalPaginas")) {
                return slugs;
            }
        }
    }

    @Test
    @DisplayName("só cursos publicados aparecem: rascunho e arquivado ficam fora e respondem 404")
    void soPublicados() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Curso não encontrado.");

        adm.publicar(curso);
        assertThat(slugsNoCatalogo()).contains(slug);
        assertThat(mvc.get().uri("/api/cursos?size=100"))
                .bodyJson()
                .isLenientlyEqualTo("{\"pagina\":0,\"tamanho\":100}");

        adm.status("/api/adm/cursos/" + curso + "/status", "ARQUIVADO");
        assertThat(slugsNoCatalogo()).doesNotContain(slug);
        assertThat(mvc.get().uri("/api/cursos/" + slug)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("detalhe mostra o conteúdo ativo da turma ativa mais recente, na ordem")
    void conteudoDaTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.publicar(curso);

        long antiga = adm.criarTurma(curso, "Antiga");
        adm.ativarTurma(antiga);
        adm.criarModulo(antiga, "Da turma antiga");

        long atual = adm.criarTurma(curso, "Atual");
        adm.ativarTurma(atual);
        long introducao = adm.criarModulo(atual, "Introdução");
        long inativo = adm.criarModulo(atual, "Inativo");
        long semAulas = adm.criarModulo(atual, "Sem aulas");
        long gratuita = adm.criarAula(introducao, "Gratuita", true);
        long escondida = adm.criarAula(introducao, "Escondida", false);
        long paga = adm.criarAula(introducao, "Paga", false);
        adm.criarAula(inativo, "Aula de módulo inativo", false);
        adm.status("/api/adm/modulos/" + inativo + "/status", "INATIVO");
        adm.status("/api/adm/aulas/" + escondida + "/status", "INATIVO");
        assertThat(adm.enviar(
                        "PUT",
                        "/api/adm/turmas/" + atual + "/modulos/ordem",
                        "{\"ids\":[%d,%d,%d]}".formatted(semAulas, introducao, inativo)))
                .hasStatusOk();

        long preparacao = adm.criarTurma(curso, "Próxima");
        adm.criarModulo(preparacao, "Da turma em preparação");

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"slug":"%s","titulo":"IT Curso %s","resumo":"Resumo de IT Curso %s","descricao":null,
                         "capaUrl":null,
                         "modulos":[
                           {"titulo":"Sem aulas","aulas":[]},
                           {"titulo":"Introdução","aulas":[
                             {"id":%d,"titulo":"Gratuita","gratuita":true},
                             {"id":%d,"titulo":"Paga","gratuita":false}]}]}
                        """.formatted(slug, slug, slug, gratuita, paga));
    }

    @Test
    @DisplayName("curso publicado sem turma ativa aparece sem conteúdo")
    void semTurmaAtiva() {
        String slug = stamp();
        long curso = adm.criarCurso("IT Curso " + slug, slug);
        adm.criarModulo(adm.criarTurma(curso, "Em preparação"), "Módulo");
        adm.publicar(curso);

        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.modulos")
                .asArray()
                .isEmpty();
    }

    @Test
    @DisplayName("a listagem não expõe dados internos do curso")
    void semDadosInternos() {
        String slug = stamp();
        adm.publicar(adm.criarCurso("IT Curso " + slug, slug));

        var resposta = mvc.get().uri("/api/cursos/" + slug).exchange();
        assertThat(resposta).hasStatusOk();
        assertThat(CursoITHelper.corpo(resposta))
                .doesNotContain("\"id\"", "status", "capaChave", "criadoEm", "atualizadoEm");
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/curso/CapaCursoIT.java`:

`````java
package br.com.celke.cursos.curso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.shared.armazenamento.ArmazenamentoProperties;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.UsuariosSeed;
import br.com.celke.cursos.support.fixtures.ImagemFixture;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * Cobre: capa do curso com o Garage real — envio por URL pré-assinada, confirmação que reduz e
 * regrava em JPEG sem EXIF, troca (apaga a anterior), remoção, redirecionamento do catálogo e os
 * envios recusados (tipo, tamanho, conteúdo, chave de outro curso), sempre apagando o original.
 * Não cobre: casos do processamento de imagem (ProcessadorImagemTest).
 * Dado criado: cursos it-<stamp> e arquivos em uploads/capas/ e capas/.
 */
class CapaCursoIT extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private StorageService storage;

    @Autowired
    private CursoRepository cursos;

    @Autowired
    private ArmazenamentoProperties armazenamento;

    private CursoITHelper adm;
    private long curso;
    private String slug;

    @BeforeEach
    void criarCurso() {
        adm = new CursoITHelper(mvc, bearer(login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA)));
        slug = stamp();
        curso = adm.criarCurso("IT Curso " + slug, slug);
    }

    /** Inicia o envio, faz o PUT da imagem na URL pré-assinada e devolve a chave. */
    private String enviar(byte[] conteudo, String contentType) throws Exception {
        var inicio = adm.enviar(
                "POST",
                "/api/adm/cursos/" + curso + "/capa/envio",
                "{\"contentType\":\"%s\",\"tamanho\":%d}".formatted(contentType, conteudo.length));
        assertThat(inicio).hasStatusOk();
        String corpo = CursoITHelper.corpo(inicio);
        String url = JsonPath.read(corpo, "$.url");
        assertThat(url).startsWith(armazenamento.endpointPublico());
        var put = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", contentType)
                        .PUT(BodyPublishers.ofByteArray(conteudo))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        return JsonPath.read(corpo, "$.chave");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult confirmar(String chave) {
        return adm.enviar("PUT", "/api/adm/cursos/" + curso + "/capa", "{\"chave\":\"%s\"}".formatted(chave));
    }

    private String chaveDaCapa() {
        return cursos.findById(curso).orElseThrow().getCapaChave();
    }

    @Test
    @DisplayName("envia, confirma (reduzida, JPEG sem EXIF), troca, aparece no catálogo e é removida")
    void cicloDaCapa() throws Exception {
        String original = enviar(ImagemFixture.pngTransparente(2000, 1500), "image/png");
        assertThat(confirmar(original))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.capaUrl")
                .asString()
                .startsWith(armazenamento.endpointPublico())
                .contains("/capas/" + curso + "/");
        String primeira = chaveDaCapa();
        assertThat(primeira).matches("capas/" + curso + "/[0-9a-f-]{36}\\.jpg");
        assertThat(storage.info(original)).as("original apagado").isEmpty();
        assertThat(storage.info(primeira))
                .get()
                .extracting(StorageService.InfoArquivo::contentType)
                .isEqualTo("image/jpeg");
        BufferedImage gravada = ImageIO.read(new ByteArrayInputStream(storage.baixar(primeira)));
        assertThat(gravada.getWidth()).isEqualTo(960);
        assertThat(gravada.getHeight()).isEqualTo(720);

        // Troca por um JPEG com EXIF: a capa anterior é apagada e o EXIF não chega ao bucket
        assertThat(confirmar(enviar(ImagemFixture.jpegComExif(400, 300, "GPS -23.5505"), "image/jpeg")))
                .hasStatusOk();
        String segunda = chaveDaCapa();
        assertThat(segunda).isNotEqualTo(primeira);
        assertThat(storage.info(primeira)).as("capa anterior apagada").isEmpty();
        assertThat(new String(storage.baixar(segunda), StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif")
                .doesNotContain("GPS -23.5505");

        adm.publicar(curso);
        assertThat(mvc.get().uri("/api/cursos/" + slug))
                .bodyJson()
                .extractingPath("$.capaUrl")
                .isEqualTo("/api/cursos/" + slug + "/capa");
        var redirecionamento = mvc.get().uri("/api/cursos/" + slug + "/capa").exchange();
        assertThat(redirecionamento).hasStatus(HttpStatus.FOUND);
        assertThat(redirecionamento.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("max-age=300, public");
        String destino = redirecionamento.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(destino).startsWith(armazenamento.endpointPublico());
        var imagem = http.send(HttpRequest.newBuilder(URI.create(destino)).build(), BodyHandlers.ofByteArray());
        assertThat(imagem.statusCode()).isEqualTo(200);
        assertThat(imagem.body()).isEqualTo(storage.baixar(segunda));

        assertThat(adm.enviar("DELETE", "/api/adm/cursos/" + curso + "/capa", null))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(storage.info(segunda)).isEmpty();
        assertThat(mvc.get().uri("/api/cursos/" + slug + "/capa")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("conteúdo que não é imagem é recusado e apagado, mesmo enviado como image/png")
    void conteudoQueNaoEImagem() throws Exception {
        String chave = enviar("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(confirmar(chave))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie uma imagem JPEG ou PNG.");
        assertThat(storage.info(chave)).isEmpty();
        assertThat(chaveDaCapa()).isNull();
    }

    @Test
    @DisplayName("arquivo acima de 5 MB enviado mesmo assim é recusado na confirmação e apagado")
    void arquivoGrandeDemais() throws Exception {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        var inicio = adm.enviar(
                "POST", "/api/adm/cursos/" + curso + "/capa/envio", "{\"contentType\":\"image/png\",\"tamanho\":10}");
        String url = JsonPath.read(CursoITHelper.corpo(inicio), "$.url");
        String chave = JsonPath.read(CursoITHelper.corpo(inicio), "$.chave");
        http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "image/png")
                        .PUT(BodyPublishers.ofByteArray(grande))
                        .build(),
                BodyHandlers.discarding());

        assertThat(confirmar(chave))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem pode ter no máximo 5 MB.");
        assertThat(storage.info(chave)).isEmpty();
    }

    @Test
    @DisplayName("envio recusado: tipo não aceito, tamanho acima do limite, sem upload e chave de outro curso")
    void enviosRecusados() throws Exception {
        String rota = "/api/adm/cursos/" + curso + "/capa/envio";
        assertThat(adm.enviar("POST", rota, "{\"contentType\":\"image/svg+xml\",\"tamanho\":10}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envie uma imagem JPEG ou PNG.");
        assertThat(adm.enviar("POST", rota, "{\"contentType\":\"image/png\",\"tamanho\":5242881}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem pode ter no máximo 5 MB.");

        String naoEnviada = JsonPath.read(
                CursoITHelper.corpo(adm.enviar("POST", rota, "{\"contentType\":\"image/png\",\"tamanho\":10}")),
                "$.chave");
        assertThat(confirmar(naoEnviada))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A imagem não foi enviada. Comece um novo envio.");

        // Imagem válida enviada para outro curso não pode virar capa deste
        String outroSlug = stamp();
        long outro = adm.criarCurso("IT Outro " + outroSlug, outroSlug);
        long atual = curso;
        curso = outro;
        String chaveDoOutro = enviar(ImagemFixture.png(10, 10), "image/png");
        curso = atual;
        assertThat(confirmar(chaveDoOutro))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Envio de capa inválido. Comece um novo envio.");
        assertThat(storage.info(chaveDoOutro))
                .as("o envio do outro curso continua lá")
                .isPresent();
    }
}
`````

### 11. Jornada e2e

`backend/src/e2e/java/br/com/celke/cursos/e2e/curso/CatalogoE2E.java`:

`````java
package br.com.celke.cursos.e2e.curso;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

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
        String token =
                Api.login(E2eConfig.ADMIN_SEED, E2eConfig.SENHA_SEED).jsonPath().getString("accessToken");
        String slug = Dados.stamp();

        // 1. Curso, turma ativa, módulo e duas aulas (uma gratuita)
        long curso = criar(
                token,
                "/api/adm/cursos",
                "{\"titulo\":\"Curso %s\",\"slug\":\"%s\",\"resumo\":\"Jornada e2e\"}".formatted(slug, slug));
        long turma = criar(token, "/api/adm/cursos/" + curso + "/turmas", "{\"nome\":\"Turma e2e\"}");
        alterarStatus(token, "/api/adm/turmas/" + turma + "/status", "ATIVA");
        long modulo = criar(token, "/api/adm/turmas/" + turma + "/modulos", "{\"titulo\":\"Primeiros passos\"}");
        criar(token, "/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Boas-vindas\",\"gratuita\":true}");
        criar(token, "/api/adm/modulos/" + modulo + "/aulas", "{\"titulo\":\"Instalação\"}");

        // 2. Capa: URL pré-assinada → PUT no Garage pelo Caddy (media) → confirmação na API
        byte[] png = ImagemFixture.png(1600, 900);
        Response envio = adm(token)
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
        adm(token)
                .body("{\"chave\":\"%s\"}".formatted(envio.jsonPath().getString("chave")))
                .put("/api/adm/cursos/" + curso + "/capa")
                .then()
                .statusCode(200)
                .body("capaUrl", startsWith(E2eConfig.origem(HOST_MIDIA)));

        // 3. Antes de publicar, o catálogo não conhece o curso
        Api.aluno().get("/api/cursos/" + slug).then().statusCode(404);
        alterarStatus(token, "/api/adm/cursos/" + curso + "/status", "PUBLICADO");

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

        adm(token).get("/api/adm/cursos").then().statusCode(403).body("detail", equalTo("Acesso negado."));
    }

    private static RequestSpecification adm(String token) {
        return Api.adm().header("Authorization", Api.bearer(token));
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

    private static long criar(String token, String rota, String corpo) {
        return adm(token)
                .body(corpo)
                .post(rota)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    private static void alterarStatus(String token, String rota, String status) {
        adm(token)
                .body("{\"status\":\"%s\"}".formatted(status))
                .patch(rota)
                .then()
                .statusCode(200)
                .body("status", equalTo(status));
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

## Antes de executar

Fase 1 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/cursos
```

## Verificação

Dentro de `backend/`:

```bash
./mvnw spotless:apply
./mvnw verify
```

- `BUILD SUCCESS` com 255 testes unitários e 213 de integração, e a cobertura mínima atendida.

Na raiz do repositório, a jornada e2e com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- 11 jornadas passam, entre elas as duas de `CatalogoE2E`.

No ambiente local (`docker compose -f infra/compose.yml up -d --build --wait`), pelo Swagger
(`http://localhost:8080/swagger-ui.html`) com o token de um admin:

- Crie um curso sem slug e confira o slug gerado; crie uma turma, ative, crie dois módulos e inverta a
  ordem.
- Peça a URL de envio da capa, envie uma imagem com `curl -X PUT -H "Content-Type: image/png"
  --data-binary @capa.png "<url>"` e confirme; publique o curso.
- Sem token, `GET /api/cursos/<slug>` mostra o conteúdo e `GET /api/cursos/<slug>/capa` redireciona
  para a imagem.
- Com tudo verde, as tasks de `.sdd/tasks/catalogo-de-cursos-v1.0/` passam para `Status: Concluída` e os critérios de aceite
  são marcados (`- [x]`).

## Depois de executar

```bash
git add backend/ .sdd/
git commit -m "feat(curso): adicionar cursos, turmas, módulos, aulas e catálogo"
git push -u origin feature/cursos
```

Abra o Pull Request de `feature/cursos` para `homolog`.

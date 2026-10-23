# LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 19
**Branch:** `feature/lgpd`

## Prompt

Vamos atender os direitos do titular (LGPD, art. 18) pelo próprio perfil do aluno e deixar a política
de privacidade dizendo exatamente o que o sistema faz. Siga o `backend/CLAUDE.md`, as rules de
`.claude/rules/` e os padrões do `web/`. Não faça commit.

Entregas:

1. **Baixar meus dados**: um JSON com tudo o que a plataforma guarda sobre a conta.
2. **Excluir minha conta**: apaga ou anonimiza o que não precisa ficar e mantém, sem nome nem e-mail,
   o que tem prazo legal de guarda.
3. **Retenção da auditoria de login** (180 dias), como o log de acesso aos vídeos.
4. **Modelo da política de privacidade** com os dados, finalidades, bases legais e os prazos aplicados.

### 1. Porta por módulo (`shared/lgpd`)

- `DadosPessoais`: `secao()`, `exportar(usuarioId)` (`List<Map<String, Object>>` em ordem cronológica)
  e `default void excluir(usuarioId) {}` (o padrão é manter). Cada módulo implementa, num `@Component`
  em `infra/`, só para as próprias tabelas, com `JdbcClient`.
- `Linhas.de(...)`: converte `Timestamp`/`OffsetDateTime` em instantes ISO-8601.

| Módulo (classe) | Seção | Exporta | Na exclusão |
|---|---|---|---|
| `auth` (`AuthDadosPessoais`) | `entradas` | auditoria de login: quando, resultado, IP, navegador | apaga refresh tokens, tokens de e-mail, códigos de recuperação e segundo fator |
| `matricula` | `matriculas` | curso, turma, início, fim, status, forma de pagamento, datas | mantém (registro do serviço e da venda) |
| `progresso` | `progresso` | aula, posição, vista, revisar | apaga |
| `forum` | `forum` | perguntas e respostas (tipo, título, texto, data) | mantém sem autor (aparece "Conta excluída") |
| `certificado` | `certificados` | código, nome, curso, turma, carga, percentual, emissão | apaga as linhas e os PDFs do bucket |
| `termo` | `aceitesTermos` | tipo, versão, aceite, IP, navegador | mantém (prova do consentimento) |
| `video` | `acessosVideo` | aula, acesso, IP, navegador | mantém até a retenção (180 dias) |
| `pagamento` | `pedidos` | código, e-mail, nome, status, valor, moeda, forma, datas | troca o e-mail por `excluida-<id>@conta-excluida.invalid` e apaga o nome |

### 2. Conta (`usuario`)

- `Usuario.NOME_EXCLUIDA = "Conta excluída"`; `anonimizar(agora)`: nome `Conta excluída`, e-mail
  `excluida-<id>@conta-excluida.invalid`, sem senha, sem foto (devolve a chave da foto), bloqueada e com
  as sessões encerradas; `isExcluida()`. O id fica: matrículas, pedidos, aceites e auditoria apontam para
  ele.
- `UsuarioService.anonimizar(id)` → `Optional<String>` com a foto a apagar.

### 3. Serviço e rotas (`perfil`)

`PrivacidadeService` recebe a `List<DadosPessoais>`:

- `exportar(usuarioId)`: `geradoEm`, `conta` (id, nome, e-mail, papéis, criação, confirmação do
  e-mail, tem foto) e uma chave por seção, em ordem alfabética.
- `excluirConta(usuarioId, senha)` (transação): administrador recebe 409 ("Contas de administrador não
  são excluídas por aqui. Fale com outro administrador."); senha errada, 400 ("A senha não confere.");
  envia o e-mail `conta-excluida` ("Sua conta foi excluída") para o endereço atual, chama `excluir` de
  cada módulo, revoga os refresh tokens, anonimiza a conta, apaga a foto do bucket **depois do commit** e
  esquece a conta no `SessaoValidaValidator` (o access token cai na hora; o validador esquece de novo
  depois do commit).

`PrivacidadeController` em `/api/perfil`:

- `GET /dados` → o JSON com `Content-Disposition: attachment; filename="meus-dados.json"`.
- `POST /exclusao { senha, confirmacao }` (`confirmacao` exatamente `EXCLUIR`, "Digite EXCLUIR para
  confirmar.") → 204 limpando o cookie de refresh.

Template `templates/email/conta-excluida.html`: o que foi apagado, o que fica pelo prazo legal (sem nome
nem e-mail) e "Se você não pediu a exclusão, responda este e-mail.".

### 4. Retenção da auditoria de login

`AuditoriaLoginProperties` (`app.auth.auditoria-login.retencao: P180D`, com comentário do Marco Civil),
`AuditoriaLoginService.apagarVencidas()` (`AuditoriaLoginRepository.apagarAnteriores(limite)`) e o
agendamento `LimpezaAuditoriaLogin` no worker (`@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")`,
todo dia às 4h40 de Brasília).

### 5. Front (área do aluno)

- `perfil/privacidade.ts` (com teste): `PALAVRA_EXCLUSAO`, `erroConfirmacaoExclusao`,
  `nomeArquivoDados(data)` (`meus-dados-AAAA-MM-DD.json`) e `urlPoliticaPrivacidade(location)`
  (`aluno.<domínio>` → `cursos.<domínio>/privacidade/`).
- `perfil/Privacidade.tsx`, um card no fim do perfil: "Baixar meus dados" (baixa o JSON pela API e salva
  como arquivo), o resumo do que a exclusão apaga e mantém com o link da política, e "Excluir minha
  conta", que abre um diálogo com "Sua senha" e "Digite EXCLUIR para confirmar"; depois da exclusão,
  `window.location.replace('/entrar?conta=excluida')`.
- `EntrarPagina` do aluno: com `?conta=excluida`, "Sua conta foi excluída. Enviamos a confirmação para o
  seu e-mail.".

### 6. Testes

- `PrivacidadeIT`: exportação em anexo com a conta e todas as seções (401 sem login); exclusão com senha
  errada e sem a palavra recusadas; exclusão certa limpa o cookie, derruba o token, impede o login,
  anonimiza a conta, apaga os refresh tokens, mantém a auditoria de login e envia o e-mail; o
  administrador recebe 409.
- `AuditoriaLoginRetencaoIT`: apaga o que passou de 180 dias e mantém o resto.
- Vitest de `privacidade.ts`.
- Playwright `e2e/aluno/privacidade.spec.ts`: baixa o JSON (nome do arquivo e e-mail da conta) e exclui
  a conta (validação, e-mail de confirmação, login recusado). `support/mailpit.ts` ganha
  `aguardarEmail(destinatario, assunto)`, usado também pelo `linkDoEmail`.

### 7. Documentação

- `docs/politica-privacidade-modelo.md`: modelo para publicar em ADM → Termos, com controlador e
  encarregado entre colchetes, a tabela de dados, finalidades e bases legais, compartilhamento (meio de
  pagamento, e-mail, hospedagem), a tabela de prazos (conta, fórum, matrículas e pedidos 5 anos, corpo
  das notificações 180 dias, entradas e acessos 6 meses, auditoria 5 anos, aceites, logs 180 dias,
  backups até 6 meses), direitos (os dois botões do perfil e o e-mail do encarregado), segurança, cookie
  de sessão e mudanças na política.
- `docs/operacao.md` (seção 4.2 com a tabela de retenção e a auditoria de login na limpeza), plano
  (Fase 19), capítulos (Parte 13), `README.md` (funcionalidade e link do modelo) e `web/e2e/README.md`.

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

`README.md`:

`````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo (ADM) e site público, desenvolvida
passo a passo como tutorial.

## Índice

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Requisitos](#requisitos)
- [Executar com Docker (recomendado)](#executar-com-docker-recomendado)
- [Executar para desenvolvimento](#executar-para-desenvolvimento)
- [Testes](#testes)
- [Comandos úteis](#comandos-úteis)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Branches](#branches)
- [Documentação](#documentação)

## Funcionalidades

- **Área do aluno:** meus cursos (ativos, arquivados e expirados), turma com módulos e progresso, aula
  com vídeo HLS (retoma de onde parou), arquivos de apoio, fórum por aula e por turma, certificado em PDF
  com código de validação, perfil (nome, foto e senha), compra de cursos.
- **ADM:** cursos, turmas, módulos e aulas; envio de vídeo em partes com conversão para HLS; capa,
  arquivos de apoio, matrículas, moderação do fórum, regras do certificado e termos; meios de pagamento,
  ofertas, pedidos e notificações recebidas; usuários (bloquear e encerrar sessões) e trilha de
  auditoria.
- **Site público:** home, catálogo, página do curso com preço e botão de compra, validação de
  certificado, termos e privacidade, com SEO (Open Graph, dados estruturados, sitemap).
- **Conta e segurança:** cadastro com confirmação de e-mail, login com JWT e refresh em cookie
  `HttpOnly`, verificação em duas etapas (aplicativo autenticador) obrigatória para administradores,
  senhas comuns e vazadas recusadas, recuperação de senha, bloqueio por tentativas, limites por IP e por
  usuário, termos versionados com aceite, usuário do banco da aplicação sem permissão de mudar tabelas.
- **Privacidade (LGPD):** o aluno baixa uma cópia dos dados e exclui a conta pelo perfil; retenção dos
  registros com prazo legal e um modelo de política de privacidade com os prazos que o sistema aplica.
- **Pagamentos:** Hotmart (webhook 2.0.0) numa estrutura que aceita outros meios de pagamento.
- **Operação:** fila de jobs no PostgreSQL (worker), alertas por e-mail, backup e restauração, logs em
  JSON no horário de Brasília guardados em arquivo, auditoria de todas as alterações nos dados.

## Tecnologias

| Parte | Tecnologias |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 18 |
| Vídeos e arquivos | Garage (S3) com URLs pré-assinadas, ffmpeg (HLS) |
| Frontend | React 19, TypeScript, Vite, `@celke/kole-react`, hls.js |
| Site | Páginas HTML geradas a partir da API (Node) |
| Testes | JUnit 5, Mockito, Testcontainers, REST Assured, Vitest, Testing Library, Playwright |
| Infraestrutura | Docker Compose, Caddy, GitHub Actions, deploy numa VPS |

## Requisitos

**Para executar com Docker:** Docker com o Docker Compose e Git.

**Para desenvolver:** além do Docker, JDK 25 e ffmpeg (para os testes do backend) e Node.js 24 (para os
fronts).

Portas usadas:

| Porta | Serviço |
|---|---|
| 8088 | Stack completa (Caddy): aluno, ADM, site e mídia |
| 8026 | Mailpit da stack completa (e-mails enviados) |
| 8080 | API (desenvolvimento) |
| 5173 e 5174 | Área do aluno e ADM (desenvolvimento, Vite) |
| 5432, 3900 e 8025 | PostgreSQL, Garage e Mailpit (desenvolvimento) |

## Executar com Docker (recomendado)

Sobe tudo como em produção — Caddy, API, worker, fronts, site, PostgreSQL, Garage e Mailpit — com contas
de exemplo já criadas. Os valores de `infra/e2e.env` são só para uso local.

**1. Clonar o repositório**

```bash
git clone https://github.com/celkecursos/plataforma-cursos.git
cd plataforma-cursos
```

**2. Subir os serviços** (a primeira vez leva alguns minutos para construir as imagens)

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

Este comando:

- cria o banco e aplica as migrations;
- sobe a API, o worker (fila, e-mails e conversão de vídeo), os fronts e o gerador do site;
- cria o bucket no Garage e as contas de exemplo;
- deixa tudo atrás do Caddy na porta 8088.

**3. Acessar**

| Endereço | O quê |
|---|---|
| http://aluno.localhost:8088 | Área do aluno |
| http://adm.localhost:8088 | ADM |
| http://cursos.localhost:8088 | Site público (atualizado a cada 3 segundos a partir da API) |
| http://localhost:8026 | Mailpit: e-mails de confirmação, recuperação de senha e matrícula |

Os navegadores atuais resolvem `*.localhost` sozinhos; não é preciso mexer no `hosts`.

**4. Contas de exemplo**

| Conta | E-mail | Senha |
|---|---|---|
| Administrador | `admin@celke.com.br` | `123456A#b123` |
| Aluno | `aluno@celke.com.br` | `123456A#b123` |

O administrador entra em duas etapas: depois da senha, informe o código de 6 dígitos de um aplicativo
autenticador (Google Authenticator, Microsoft Authenticator, 1Password...). No aplicativo, adicione uma
conta pela chave `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` (tipo "baseado em tempo"): é o segredo do admin de
exemplo, definido em `SEED_E2E_TOTP` no `infra/e2e.env`.

Para ver um curso no site e na área do aluno: entre no ADM, crie o curso, uma turma (ative-a), um módulo
e uma aula, publique o curso e matricule o aluno na aba **Matrículas** da turma.

**5. Parar**

```bash
# Para os serviços e mantém os dados
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env stop
# Para e apaga tudo (banco, vídeos, logs)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

## Executar para desenvolvimento

API e worker no Docker (com Swagger e Mailpit) e os fronts no Vite, com recarga ao salvar.

**1. Gerar o `infra/.env`** com segredos aleatórios (uma vez)

```bash
./infra/scripts/gerar-env.sh
```

Para usar o ADM no Vite, acrescente no `infra/.env` a porta dele nas origens:

```bash
AUTH_ORIGENS=http://localhost:8080,http://localhost:5173,http://localhost:5174
S3_CORS_ORIGENS=http://localhost:5173,http://localhost:5174
```

**2. Subir o backend**

```bash
docker compose -f infra/compose.yml up -d --build --wait
```

| Endereço | O quê |
|---|---|
| http://localhost:8080/swagger-ui.html | Documentação da API (Swagger) |
| http://localhost:8025 | Mailpit |
| http://localhost:8080/actuator/health | Saúde da API |

**3. Criar o administrador** (pede a senha no terminal; mínimo de 12 caracteres, sem o nome ou o
e-mail e fora das senhas mais usadas)

```bash
docker compose -f infra/compose.yml run --rm api criar-admin admin@exemplo.com "Administrador"
```

No primeiro login do ADM, leia o QR code com um aplicativo autenticador e guarde os códigos de
recuperação que aparecem em seguida.

Alunos criam a conta pela tela de cadastro; o link de confirmação chega no Mailpit.

**4. Subir os fronts** (cada um num terminal)

```bash
cd web
npm ci
npm run dev:aluno   # http://localhost:5173
npm run dev:adm     # http://localhost:5174
```

O Vite manda `/api` para a API em `http://localhost:8080` (outra API: `VITE_API_PROXY`).

## Testes

**Backend** (dentro de `backend/`, com o Docker rodando e o ffmpeg no PATH):

```bash
./mvnw spotless:apply        # formata
./mvnw verify                # unitários, integração (Testcontainers), cobertura mínima e formatação
```

**Jornadas e2e** (com a stack completa no ar, como em "Executar com Docker"):

```bash
(cd backend && ./mvnw verify -Pe2e)    # API pelo Caddy, worker, Garage e e-mail
(cd web && npm run e2e)                # navegador real (Playwright): aluno, ADM e site
```

Como escolher o que rodar, ver os testes na tela (janela aberta, câmera lenta, modo UI), contas, dados de
teste e o que cada spec cobre: [`web/e2e/README.md`](web/e2e/README.md).

**Fronts** (dentro de `web/`):

```bash
npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```

## Comandos úteis

```bash
# Situação dos containers (desenvolvimento)
docker compose -f infra/compose.yml ps

# Logs da API e do worker
docker compose -f infra/compose.yml logs -f api worker

# Reconstruir depois de mudar o backend
docker compose -f infra/compose.yml up -d --build --wait

# Parar o ambiente de desenvolvimento (com -v, apaga os dados)
docker compose -f infra/compose.yml down

# Abrir o banco de desenvolvimento
docker compose -f infra/compose.yml exec postgres psql -U plataforma_cursos plataforma_cursos

# Administrador perdeu o celular e os códigos de recuperação: o próximo login pede o QR code de novo
docker compose -f infra/compose.yml run --rm api redefinir-2fa admin@exemplo.com
```

## Estrutura do projeto

```
backend/   API e worker (Java 25, Spring Boot 4.1): módulos por funcionalidade, testes e e2e
web/       Área do aluno e ADM (React + Vite), site público gerado a partir da API, testes Playwright
infra/     Docker Compose (local, servidores e e2e), Caddy, scripts de VPS, deploy e backup
docs/      Plano, roteiro dos capítulos, VPS, operação, pagamentos e prompts
```

## Branches

| Branch | Uso |
|---|---|
| `main` | Produção |
| `homolog` | Homologação |
| `feature/nome` | Nova funcionalidade (PR para `homolog`) |
| `fix/nome` | Correção (PR para `homolog`) |
| `hotfix/nome` | Correção urgente a partir da `main` |

## Documentação

- [Plano de implementação](docs/plano-implementacao.md)
- [Roteiro da VPS](docs/vps.md)
- [Operação: backup, alertas, logs, auditoria e segurança](docs/operacao.md)
- [Pagamentos (Hotmart)](docs/pagamentos.md)
- [Modelo da política de privacidade](docs/politica-privacidade-modelo.md)
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto do zero](docs/prompts-iniciais/README.md)
- [Prompts por fase](docs/prompts/README.md)
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/AuditoriaLoginService.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import br.com.celke.cursos.auth.domain.ResultadoLogin;
import br.com.celke.cursos.auth.infra.AuditoriaLoginRepository;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada tentativa de login numa transação própria: o registro fica gravado mesmo quando o
 * login falha e a transação dele é desfeita. Os registros são apagados depois da retenção (6 meses,
 * prazo do Marco Civil para os registros de acesso a aplicações).
 */
@Service
@EnableConfigurationProperties(AuditoriaLoginProperties.class)
public class AuditoriaLoginService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaLoginService.class);

    private final AuditoriaLoginRepository repository;
    private final AuditoriaLoginProperties props;
    private final Clock clock;

    public AuditoriaLoginService(AuditoriaLoginRepository repository, AuditoriaLoginProperties props, Clock clock) {
        this.repository = repository;
        this.props = props;
        this.clock = clock;
    }

    /** Apaga as tentativas mais antigas que a retenção; devolve quantas saíram. */
    @Transactional
    public int apagarVencidas() {
        Instant limite = clock.instant().minus(props.retencao());
        int apagadas = repository.apagarAnteriores(limite);
        LOG.info("Auditoria de login: {} registro(s) anteriores a {} apagados", apagadas, limite);
        return apagadas;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String email, Usuario usuario, ResultadoLogin resultado, OrigemRequisicao origem) {
        repository.save(new AuditoriaLogin(
                Usuario.normalizarEmail(email),
                usuario == null ? null : usuario.getId(),
                resultado,
                origem.ip(),
                origem.userAgent(),
                clock.instant()));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/SessaoValidaValidator.java`:

`````java
package br.com.celke.cursos.auth.application;

import br.com.celke.cursos.usuario.application.UsuarioService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Recusa o access token de conta bloqueada ou emitido antes do "encerrar sessões" do ADM, sem esperar
 * os 10 minutos de validade dele. A situação de cada conta fica 30 segundos em memória (o bloqueio
 * feito nesta instância vale na hora: ela esquece a conta).
 */
@Component
public class SessaoValidaValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSAO_ENCERRADA = new OAuth2Error("invalid_token", "Sessão encerrada.", null);

    private final UsuarioService usuarioService;
    private final Cache<Long, Optional<Situacao>> situacoes = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public SessaoValidaValidator(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
        }
        Optional<Situacao> situacao = situacoes.get(id, this::carregar);
        boolean valida = situacao.filter(Situacao::ativo)
                .filter(s -> s.encerradasEm() == null
                        || jwt.getIssuedAt() == null
                        || !jwt.getIssuedAt().isBefore(s.encerradasEm().truncatedTo(ChronoUnit.SECONDS)))
                .isPresent();
        return valida ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(SESSAO_ENCERRADA);
    }

    /**
     * Esquece a situação guardada (depois de bloquear, desbloquear, encerrar as sessões ou excluir a
     * conta). Dentro de uma transação, esquece de novo depois do commit: uma requisição no meio do caminho
     * não deixa a situação antiga guardada.
     */
    public void esquecer(Long usuarioId) {
        situacoes.invalidate(usuarioId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    situacoes.invalidate(usuarioId);
                }
            });
        }
    }

    private Optional<Situacao> carregar(Long id) {
        return usuarioService.buscarPorId(id).map(u -> new Situacao(u.isAtivo(), u.getSessoesEncerradasEm()));
    }

    private record Situacao(boolean ativo, Instant encerradasEm) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/AuditoriaLoginRepository.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.auth.domain.AuditoriaLogin;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditoriaLoginRepository extends JpaRepository<AuditoriaLogin, Long> {

    @Modifying
    @Query("DELETE FROM AuditoriaLogin a WHERE a.criadoEm < :limite")
    int apagarAnteriores(Instant limite);
}
`````

`backend/src/main/java/br/com/celke/cursos/usuario/application/UsuarioService.java`:

`````java
package br.com.celke.cursos.usuario.application;

import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import br.com.celke.cursos.usuario.infra.UsuarioRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * Conta de aluno criada por uma compra: sem senha e com o e-mail confirmado (o pagamento chegou a
     * ele). O dono cria a senha pelo link enviado no e-mail da matrícula.
     */
    @Transactional
    public Usuario criarAlunoSemSenha(String nome, String email) {
        return criar(nome, email, null, Set.of(Papel.ALUNO), true);
    }

    private Usuario criar(String nome, String email, String senha, Set<Papel> papeis, boolean confirmado) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (repository.existsByEmail(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        String hash = senha == null ? null : passwordEncoder.encode(senha);
        return repository.save(new Usuario(nome, emailNormalizado, hash, papeis, confirmado, clock.instant()));
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
        return senha != null && usuario.possuiSenha() && passwordEncoder.matches(senha, usuario.getSenhaHash());
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

    /** Lista do ADM, ordenada por nome. */
    @Transactional(readOnly = true)
    public Page<Usuario> listar(String busca, Papel papel, Boolean ativo, Pageable pagina) {
        String termo = busca == null || busca.isBlank()
                ? null
                : "%" + busca.strip().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "") + "%";
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), Sort.by("nome", "id"));
        return repository.buscar(termo, papel, ativo, ordenada);
    }

    @Transactional
    public Usuario bloquear(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.bloquear(clock.instant());
        return usuario;
    }

    @Transactional
    public Usuario desbloquear(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.desbloquear(clock.instant());
        return usuario;
    }

    /** Anonimiza a conta (exclusão a pedido do titular); devolve a chave da foto, a apagar do bucket. */
    @Transactional
    public Optional<String> anonimizar(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        return Optional.ofNullable(usuario.anonimizar(clock.instant()));
    }

    @Transactional
    public Usuario encerrarSessoes(Long id) {
        Usuario usuario =
                repository.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        usuario.encerrarSessoes(clock.instant());
        return usuario;
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

`backend/src/main/java/br/com/celke/cursos/usuario/domain/Usuario.java`:

`````java
package br.com.celke.cursos.usuario.domain;

import br.com.celke.cursos.shared.auditoria.DadoSensivel;
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

    /** Nome que aparece no lugar do autor depois que a conta é excluída. */
    public static final String NOME_EXCLUIDA = "Conta excluída";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254)
    private String email;

    /** Nulo na conta criada por uma compra, até o dono criar a senha pelo link do e-mail. */
    @DadoSensivel
    @Column(name = "senha_hash")
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

    /** Encerramento das sessões pelo ADM: access tokens emitidos antes disso deixam de valer. */
    @Column(name = "sessoes_encerradas_em")
    private Instant sessoesEncerradasEm;

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

    public boolean possuiSenha() {
        return senhaHash != null;
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

    /** Bloqueia a conta (não entra mais) e encerra as sessões abertas. */
    public void bloquear(Instant agora) {
        this.ativo = false;
        encerrarSessoes(agora);
    }

    public void desbloquear(Instant agora) {
        this.ativo = true;
        this.atualizadoEm = agora;
    }

    public void encerrarSessoes(Instant agora) {
        this.sessoesEncerradasEm = agora;
        this.atualizadoEm = agora;
    }

    /**
     * Exclusão da conta a pedido do titular: tira nome, e-mail, senha e foto, inativa e encerra as
     * sessões. O id fica (matrículas, pedidos, aceites e auditoria apontam para ele). Devolve a chave da
     * foto, a apagar do bucket.
     */
    public String anonimizar(Instant agora) {
        String foto = this.fotoChave;
        this.nome = NOME_EXCLUIDA;
        this.email = "excluida-" + id + "@conta-excluida.invalid";
        this.senhaHash = null;
        this.fotoChave = null;
        bloquear(agora);
        return foto;
    }

    public boolean isExcluida() {
        return email.endsWith("@conta-excluida.invalid");
    }

    public Instant getSessoesEncerradasEm() {
        return sessoesEncerradasEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEmailConfirmadoEm() {
        return emailConfirmadoEm;
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
    # Usuário da API e do worker (só dados): criado e liberado pelo afterMigrate.sql quando informado.
    # Em branco (desenvolvimento), a aplicação usa o próprio dono do schema
    placeholders:
      usuario_app: ${DB_APP_USERNAME:}
      senha_app: ${DB_APP_PASSWORD:}
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
  # Arquivo de log (ligado nos servidores por LOGGING_FILE_NAME, num volume): um arquivo por dia de
  # Brasília, comprimido, guardado por 180 dias ou até 2 GB por serviço, o que vier primeiro
  logback:
    rollingpolicy:
      file-name-pattern: "${LOG_FILE}.%d{yyyy-MM-dd,America/Sao_Paulo}.%i.gz"
      max-file-size: 50MB
      max-history: 180
      total-size-cap: 2GB

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
    segundo-fator:
      # Chave AES-256 dos segredos do aplicativo autenticador dos administradores: 32 bytes em Base64
      # (openssl rand -base64 32). Trocar a chave exige cadastrar o aplicativo de novo (redefinir-2fa)
      chave: ${TOTP_CHAVE:}
      emissor: Plataforma de Cursos
      rejeitar-reuso: true
    auditoria-login:
      # Tentativas de login (e-mail, IP e navegador): 6 meses, prazo do Marco Civil para os registros de
      # acesso a aplicações; a limpeza é diária, no worker
      retencao: P180D
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
  limites-usuario:
    # Gravações (POST, PUT, PATCH, DELETE) de um usuário logado a cada minuto, em todas as rotas
    escritas-por-minuto: 120
    # Pedidos abertos no meio de pagamento por um usuário a cada hora
    checkouts-por-hora: 10
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
    # Validade das URLs dos segmentos = duração do vídeo + margem (pausas e idas e voltas no player)
    margem-playlist: PT2H
    # Log de acesso aos vídeos: 6 meses (prazo do Marco Civil para registros de acesso a aplicações)
    retencao-log-acesso: P180D
  forum:
    # Perguntas e respostas por aluno dentro da janela (o instrutor não tem limite)
    mensagens-por-janela: 20
    janela: PT1H
    anexos-por-mensagem: 3
  pagamentos:
    # Chave AES-256 dos segredos dos meios de pagamento: 32 bytes em Base64 (openssl rand -base64 32).
    # Em branco, a API sobe, mas o painel não grava credenciais
    chave: ${PAGAMENTOS_CHAVE:}
    webhook-tamanho-maximo: 256KB
    webhook-requisicoes-por-ip-por-minuto: 600
    # Corpo dos eventos processados ou ignorados é apagado depois disso (o resto fica para auditoria)
    retencao-payload: P180D
    # Disputa aberta mantém o acesso; true tira o acesso até a disputa terminar
    disputa-suspende: false
    # Cobrança atrasada mantém o acesso; com app.pagamentos.atraso-suspende-apos (ex.: P7D), o acesso
    # é tirado depois desse prazo
    reconciliacao:
      # Consulta diária na API do meio de pagamento (precisa das credenciais da API)
      ativa: false
      pendente-apos: PT24H
      sem-evento-apos: P3D
      janela: P60D
    hotmart:
      url-token: https://api-sec-vlc.hotmart.com/security/oauth/token
      url-api: https://developers.hotmart.com/payments/api/v1
      url-api-sandbox: https://sandbox.hotmart.com/payments/api/v1
      timeout: PT10S
  auditoria:
    # Trilha de auditoria guardada por 5 anos (prazo de prescrição das ações do consumidor); a limpeza é
    # semanal, no worker
    retencao: P1826D
  alertas:
    # Destinatário dos alertas do worker (jobs com erro, disco); em branco, desligados
    email: ${ALERTA_EMAIL:}
    disco-minimo-percentual: 15
    diretorio-disco: /
    repeticao-disco: PT6H
`````

`docs/operacao.md`:

`````markdown
# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

O dump traz as tabelas, não os papéis do PostgreSQL: a subida depois da restauração passa pelo serviço
`migracao`, que recria o usuário da aplicação e as permissões dele.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Pagamento contestado ou notificação com erro | Chargeback, disputa aberta e notificação de pagamento que foi para `ERRO` geram um e-mail para `ALERTA_EMAIL` na hora (veja `docs/pagamentos.md`) |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente, poucos dias de histórico. Os logs da API e do worker são JSON (ECS) com o id de correlação e o `@timestamp` no horário de Brasília (`-03:00`); o gerador do site e o `deploy.log` usam o mesmo fuso.
- **Logs persistentes da API e do worker**: além do console, cada um grava o mesmo JSON em
  `/var/log/plataforma-cursos/api.log` e `worker.log`, no volume `logs` do ambiente (sobrevive a deploy e
  recriação do container). Um arquivo por dia de Brasília, comprimido (`api.log.2026-10-22.0.gz`), novo
  arquivo a cada 50 MB, guardado por **180 dias** ou até **2 GB por serviço**, o que vier primeiro. Para
  ler, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api ls -lh /var/log/plataforma-cursos
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api tail -n 100 /var/log/plataforma-cursos/api.log
  # Um dia antigo, procurando um id de requisição (funciona com o container parado)
  docker run --rm -v plataforma-cursos-producao_logs:/logs:ro alpine \
    sh -c 'zcat /logs/api.log.2026-10-22.*.gz | grep 7f1c2a90'
  ```

  O volume não entra no backup diário (o que importa guardar por anos está no banco: a auditoria).
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Auditoria de login** (e-mail, IP, navegador de cada tentativa): o worker apaga todo dia o que passou
  de 180 dias (`app.auth.auditoria-login.retencao`).
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 4.1 Auditoria

A tabela `auditoria` guarda cada inclusão, alteração e exclusão feita pela aplicação nos dados (ADM, área
do aluno, cadastro e worker): **quem** (usuário do token; vazio para visitante e sistema), **o quê**
(entidade e id), a **operação**, o **antes e o depois** dos campos (na alteração, só os que mudaram),
**de onde** (rota da requisição, como `PUT /api/adm/cursos/{id}`, ou o job da fila, como
`job pagamento.processar-evento`), **IP**, navegador, id da requisição e a data.

- Consulta no ADM: **Segurança → Auditoria**, com filtro por entidade, id, operação e e-mail do usuário.
- Dados sensíveis (hash de senha, segredos do meio de pagamento, token do webhook) aparecem como `***`:
  a trilha mostra que mudaram, nunca o valor.
- Ficam fora: progresso das aulas (muda a cada poucos segundos), log de acesso a vídeo, auditoria de
  login, notificações de pagamento (já são registros próprios) e tokens de sessão e de e-mail. Mudanças
  feitas direto em SQL (fila, atualizações em lote) também não passam pela trilha.
- O registro entra na mesma transação da mudança: o que foi desfeito não aparece.
- **Só inclusão**: o banco recusa `UPDATE` e `DELETE` na tabela (gatilho). A única exclusão é a retenção,
  feita pelo worker aos domingos, que apaga o que passou de **5 anos** (`app.auditoria.retencao`).
- Login e tentativas de login continuam na `auditoria_login`; acesso a vídeo na `acessos_video`.

## 4.2 LGPD: direitos do titular e retenção

- **Baixar meus dados** (`GET /api/perfil/dados`, perfil do aluno → Privacidade): JSON com a conta e uma
  seção por módulo (`matriculas`, `progresso`, `forum`, `certificados`, `pedidos`, `aceitesTermos`,
  `entradas`, `acessosVideo`). Cada módulo responde pelas próprias tabelas (`shared/lgpd/DadosPessoais`):
  módulo novo com dado pessoal implementa a interface.
- **Excluir minha conta** (`POST /api/perfil/exclusao`, senha + "EXCLUIR"; administradores não se
  excluem por aqui): aviso por e-mail para o endereço da conta; apagados progresso, certificados (com o
  PDF), foto, sessões, links de e-mail e segundo fator; conta anonimizada (`Conta excluída`,
  `excluida-<id>@conta-excluida.invalid`, sem senha, inativa); pedidos sem e-mail e nome. Ficam, pelo
  prazo legal: matrículas e pedidos (5 anos), aceites dos termos, entradas e acessos aos vídeos (180
  dias), a trilha de auditoria (5 anos, com o antes da anonimização) e os backups (até 6 meses).
- **Política de privacidade**: o modelo em [`politica-privacidade-modelo.md`](politica-privacidade-modelo.md)
  traz os dados, as finalidades, as bases legais e os prazos acima; publique pelo ADM → Termos depois da
  revisão jurídica. Mudou um prazo na configuração? Mude o texto publicado.

| Dado | Retenção | Onde |
|---|---|---|
| Trilha de auditoria | 5 anos | `app.auditoria.retencao` (worker, semanal) |
| Auditoria de login | 180 dias | `app.auth.auditoria-login.retencao` (worker, diária) |
| Acessos aos vídeos | 180 dias | `app.video.retencao-log-acesso` (worker, diária) |
| Corpo das notificações de pagamento | 180 dias | `app.pagamentos.retencao-payload` (worker) |
| Logs da API e do worker | 180 dias ou 2 GB | volume `logs` |
| Backups | 7 diários, 4 semanais, 6 mensais | `backup.sh` (restic) |

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; recusa as senhas mais comuns e vazadas (lista de ~117 mil), as que contêm o nome ou o e-mail e as sequências e repetições; medidor de força nas telas; **segundo fator (TOTP) obrigatório para administradores**, com códigos de recuperação de uso único; bloqueio após falhas por e-mail e por e-mail + IP (o código errado do segundo fator conta como falha); limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões; o ADM bloqueia a conta ou encerra as sessões e o access token já emitido deixa de valer na hora (conferido a cada requisição, 30 s em memória); token de link de e-mail sai da barra de endereço depois de lido |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método e conferidas por um teste que varre todas as rotas; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR); limite por usuário nas gravações (120 por minuto) e no checkout (10 por hora); a compra só abre o pedido com a confirmação do aluno; checkout só em HTTPS nos domínios da Hotmart |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); `npm audit` das dependências do navegador (alta ou crítica quebra o build); Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker; versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção; a API e o worker entram no banco com um usuário que só lê e grava dados (o dono do schema fica para as migrations) |

### Segundo fator dos administradores

- Todo usuário com o papel `ADMIN` entra em duas etapas: a senha certa devolve um desafio (válido por
  5 minutos, sem sessão nem cookie) e a sessão só abre com o código de 6 dígitos do aplicativo
  autenticador (Google Authenticator, Microsoft Authenticator, 1Password...).
- No primeiro acesso, a tela mostra o QR code; o primeiro código certo ativa o segundo fator e mostra
  **uma vez** os 10 códigos de recuperação (cada um vale uma entrada sem o celular).
- O segredo fica cifrado no banco (AES-256-GCM, chave `TOTP_CHAVE` do `.env`); o mesmo código não vale
  duas vezes; cada etapa fica na auditoria de login (`SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`,
  `SEGUNDO_FATOR_CADASTRADO`, `CODIGO_RECUPERACAO`).
- Perdeu o celular e os códigos? Na VPS, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml run --rm api redefinir-2fa admin@celke.com.br
  ```

  O comando apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
- Trocar a `TOTP_CHAVE` invalida todos os segredos: rode o `redefinir-2fa` para cada administrador.

### Usuário do banco da aplicação

- O serviço `migracao` (mesma imagem da API, comando `migrar`) roda a cada deploy com o dono do schema
  (`POSTGRES_USER`), aplica as migrations e, no `afterMigrate.sql`, cria (na primeira vez) e libera o
  usuário da aplicação (`POSTGRES_APP_USER`): `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas, uso
  das sequências, sem criar, alterar ou apagar tabelas e sem acesso ao histórico do Flyway.
- A API e o worker sobem só depois dele, com `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`. Uma injeção
  de SQL ou uma conta roubada da aplicação não derruba tabelas nem apaga a trilha de auditoria (o
  gatilho continua barrando `UPDATE` e `DELETE` nela).
- O `deploy.sh` acrescenta ao `.env` as variáveis novas (`POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD`,
  `TOTP_CHAVE`) que ainda não existirem, com valores aleatórios.

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
`````

`web/aluno/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { DesafioSegundoFator } from '@compartilhado/api/tipos.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { SegundoFator } from '@compartilhado/sessao/SegundoFator.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));
  // Quem veio de "Comprar" cria a conta e volta para a compra depois de entrar
  const comVoltar = parametros.get('voltar')
    ? `?voltar=${encodeURIComponent(parametros.get('voltar')!)}`
    : '';

  // Administrador que entra pela área do aluno também passa pelo segundo fator
  const [desafio, setDesafio] = useState<DesafioSegundoFator>();

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  if (desafio) {
    return (
      <SegundoFator
        marca="Plataforma de Cursos"
        desafio={desafio}
        aoConcluir={() => navigate(destino, { replace: true })}
        aoVoltar={() => setDesafio(undefined)}
      />
    );
  }

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
          : parametros.get('conta') === 'excluida'
            ? 'Sua conta foi excluída. Enviamos a confirmação para o seu e-mail.'
            : 'Acesse seus cursos, aulas e certificados.'
      }
      apresentacao={{
        chamada: 'Plataforma de Cursos',
        titulo: 'Aprenda no seu ritmo',
        descricao:
          'Aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado ao concluir.',
        destaques: [
          { icone: 'circle-play', texto: 'Aulas em vídeo de qualquer lugar' },
          { icone: 'message-square', texto: 'Dúvidas respondidas no fórum' },
          { icone: 'award', texto: 'Certificado com código de validação' },
        ],
      }}
      linkSenha={<Link to={CAMINHOS.recuperarSenha}>Esqueci minha senha</Link>}
      acoes={
        <p>
          Ainda não tem conta? <Link to={CAMINHOS.cadastro + comVoltar}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          const resultado = await entrar(usuario.trim(), senha);
          if (resultado.tipo === 'desafio') {
            setDesafio(resultado.desafio);
            return;
          }
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/aluno/src/paginas/perfil/PerfilPagina.tsx`:

`````tsx
import {
  Alerta,
  Avatar,
  Botao,
  CabecalhoPagina,
  Card,
  CampoEmail,
  CampoSenha,
  CampoTexto,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  erroConfirmacao,
  erroObrigatorio,
  erroSenhaNova,
  SENHA_MINIMA_ALUNO,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { EnvioSimples, Perfil } from '../../tipos.ts';
import { Privacidade } from './Privacidade.tsx';

const FOTO_MAXIMA = 5 * 1024 * 1024;

/** Dados do aluno: nome, foto, troca de senha e privacidade (cópia dos dados e exclusão da conta). */
export function PerfilPagina() {
  const perfil = useCarregar(() => api.get<Perfil>('/api/perfil'), []);
  if (perfil.erro && !perfil.dados) return <Alerta severidade="err" mensagem={perfil.erro} />;
  if (!perfil.dados) return <Carregando mensagem="Carregando o perfil..." />;
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meu perfil" icone="user" />
      <Dados perfil={perfil.dados} aoMudar={perfil.definir} />
      <Foto perfil={perfil.dados} aoMudar={perfil.definir} />
      <TrocarSenha />
      <Privacidade />
    </div>
  );
}

function Dados({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const [nome, setNome] = useState(perfil.nome);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(nome, 'Informe o seu nome.', 120);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(await api.put<Perfil>('/api/perfil', { nome: nome.trim() }));
      toast.ok('Nome atualizado');
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('nome')) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Dados">
      <form
        noValidate
        className="pc-grade-detalhes"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={120}
          autoComplete="name"
        />
        <CampoEmail
          rotulo="E-mail"
          valor={perfil.email}
          disabled
          dica="O e-mail da conta não muda."
        />
        <div>
          <Botao type="submit" rotulo="Salvar nome" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}

function Foto({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();

  const enviar = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma foto JPG ou PNG.');
      return;
    }
    if (arquivo.size > FOTO_MAXIMA) {
      toast.err('Foto grande demais', 'A foto pode ter até 5 MB.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>('/api/perfil/foto/envio', {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Perfil>('/api/perfil/foto', { chave: envio.chave });
    }, 'Foto atualizada');
    if (novo) aoMudar(novo);
  };

  return (
    <Card titulo="Foto">
      <div className="pc-linha">
        <Avatar nome={perfil.nome} foto={perfil.fotoUrl ?? undefined} tamanho="4rem" />
        <EscolherArquivo
          rotulo={perfil.fotoUrl ? 'Trocar foto' : 'Enviar foto'}
          icone="image"
          aparencia="contorno"
          aceitar="image/jpeg,image/png"
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {perfil.fotoUrl && (
          <Botao
            rotulo="Remover foto"
            aparencia="texto"
            severidade="err"
            onClick={async () => {
              if (!(await confirmar.remover({ mensagem: 'A sua foto será removida.' }))) return;
              const ok = await executar(
                () => api.delete('/api/perfil/foto').then(() => true),
                'Foto removida',
              );
              if (ok) aoMudar({ ...perfil, fotoUrl: null });
            }}
          />
        )}
      </div>
      <p className="pc-dica">JPG ou PNG até 5 MB. A foto é reduzida para 400 × 400.</p>
    </Card>
  );
}

type ErrosSenha = Partial<Record<'atual' | 'nova' | 'confirmacao', string>>;

function TrocarSenha() {
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<ErrosSenha>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: ErrosSenha = {
      atual: atual ? undefined : 'Informe a senha atual.',
      nova: erroSenhaNova(nova),
      confirmacao: erroConfirmacao(nova, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await api.put('/api/perfil/senha', { senhaAtual: atual, novaSenha: nova });
      // A API encerrou todas as sessões (o cookie de refresh já foi limpo): recarrega na entrada,
      // o que também descarta o token desta aba
      window.location.replace(`${CAMINHOS.entrar}?senha=alterada`);
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ atual: erro.erroDoCampo('senhaAtual'), nova: erro.erroDoCampo('novaSenha') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Trocar senha">
      <form
        noValidate
        className="pc-pilha pc-formulario-estreito"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Senha atual"
          valor={atual}
          aoMudar={setAtual}
          erro={erros.atual}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoSenha
          rotulo="Nova senha"
          valor={nova}
          aoMudar={setNova}
          erro={erros.nova}
          obrigatorio
          dica="Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas mais usadas."
          forca
          politica={{ minimo: SENHA_MINIMA_ALUNO }}
          autoComplete="new-password"
        />
        <CampoSenha
          rotulo="Repita a nova senha"
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="new-password"
        />
        <Alerta
          severidade="info"
          mensagem="Ao trocar a senha, você sai de todos os dispositivos e entra de novo."
        />
        <div>
          <Botao type="submit" rotulo="Trocar senha" icone="key" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/e2e/README.md`:

`````markdown
# Testes E2E dos fronts (Playwright)

Testes ponta a ponta: dirigem um navegador real contra a **stack de e2e rodando de verdade** — Caddy,
API, worker (fila, e-mail e conversão de vídeo), fronts, gerador do site, PostgreSQL, Garage e Mailpit,
com as mesmas imagens que vão para produção. Não há mock de API: dependem dos containers no ar.

**Nunca contra um servidor.** A stack de e2e é descartável (`down -v` apaga tudo) e as contas do seed só
existem nela; `support/config.ts` recusa qualquer endereço que não seja `*.localhost`.

**Neste documento:** [Começo rápido](#começo-rápido) · [O que é testado aqui](#o-que-é-e-o-que-não-é-testado-aqui) ·
[Pré-requisitos](#pré-requisitos-uma-vez-por-máquina) · [Como rodar](#como-rodar) ·
[Contas e sessão](#contas-e-sessão) · [Dado de teste e limpeza](#dado-de-teste-e-limpeza) ·
[Os specs](#os-specs) · [Lighthouse do site](#lighthouse-do-site) · [Manutenção](#manutenção)

## Começo rápido

Depois dos pré-requisitos (uma vez por máquina, abaixo), na **raiz do repositório**:

**1. Subir a stack de e2e** (a primeira vez constrói as imagens e leva alguns minutos):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
```

**2. Rodar os testes**, dentro de `web/`:

```bash
cd web
npm run e2e                                  # todos (aluno, ADM e site)
E2E_SLOWMO=500 npm run e2e:headed -- e2e/adm/ # só o ADM, com a janela aberta e em câmera lenta
```

**3. Derrubar a stack** quando terminar (apaga banco, vídeos e e-mails):

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- Troque `e2e/adm/` por `e2e/aluno/`, `e2e/site/` ou um arquivo (`e2e/aluno/compra.spec.ts`): apontar
  para uma pasta roda todos os specs dela.
- `:headed` e `E2E_SLOWMO` servem só para **acompanhar na tela**; para rodar rápido, sem janela,
  use `npm run e2e`.
- Mudou o backend ou os fronts? Suba a stack de novo com o `--build` para os testes verem o código novo.

O resto deste documento é referência: não é preciso ler tudo para rodar o primeiro teste.

## O que é e o que não é testado aqui

| Camada             | Cobre                                                                                                  | Onde                                                                            |
| :----------------- | :----------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| Unidade (front)    | funções puras, componentes, cliente HTTP                                                               | `{aluno,adm,compartilhado}/src/**/*.test.ts(x)` e `site/*.test.ts` — `npm test` |
| Backend            | regra, rotas, segurança, banco, fila                                                                   | `backend/src/test` — `./mvnw verify`                                            |
| Jornadas da API    | API pelo Caddy, worker, Garage e e-mail, sem navegador                                                 | `backend/src/e2e` — `./mvnw verify -Pe2e`                                       |
| **E2E dos fronts** | **telas de verdade**: navegação, formulários, mensagens, sessão por cookie, vídeo tocando, site gerado | **aqui**                                                                        |

**Regra de negócio não sobe para cá.** Se o caso é de cálculo, validação ou permissão, ele tem teste no
backend ou de unidade; aqui fica o que só o navegador prova (a tela mostra o erro certo, o vídeo toca, o
cookie mantém a sessão, o link do e-mail funciona).

## Pré-requisitos (uma vez por máquina)

1. Docker com o Docker Compose (a stack sobe com ele).
2. Node.js 24 e as dependências: `cd web && npm ci`
3. O navegador do Playwright: `npx playwright install chromium`
4. As portas **8088** (Caddy) e **8026** (Mailpit) livres.

Os navegadores atuais resolvem `*.localhost` sozinhos: não é preciso mexer no `hosts`.

## Como rodar

Todos os comandos dentro de `web/`, com a stack de e2e no ar.

**Escolher o que rodar** — uma pasta, um arquivo, um teste pelo nome ou um projeto:

```bash
npm run e2e                                    # tudo
npm run e2e -- e2e/aluno/                      # só a área do aluno
npm run e2e -- e2e/aluno/compra.spec.ts        # só um spec
npm run e2e -- -g "reembolso"                  # só os testes cujo nome tem o texto
npm run e2e -- --project=site                  # projeto: aluno, adm ou site
```

**Ver o teste rodando** — combine com qualquer seleção acima:

```bash
npm run e2e:ui                                 # melhor opção: linha do tempo, volta no tempo, rede
npm run e2e:headed                             # janela maximizada
E2E_SLOWMO=500 npm run e2e:headed              # câmera lenta (ms entre ações): dá para ler a tela
npm run e2e:debug -- e2e/adm/turma.spec.ts     # passo a passo, com o Inspector
```

**Depois de rodar:**

```bash
npm run e2e:report                             # abre o último relatório HTML
npm run e2e:trace test-results/<pasta>/trace.zip   # revê uma falha gravada (o relatório mostra o caminho)
```

Quando um teste falha, ficam em `test-results/` o trace, o vídeo e a captura da tela. A janela só
maximiza com `--headed`; na execução normal o viewport é fixo (Desktop Chrome), igual em qualquer
máquina.

Os testes rodam **um de cada vez** (`workers: 1`): escrevem no mesmo banco e gastam o mesmo limite de
requisições por IP.

**Variáveis** (padrões de `infra/e2e.env`; só mude se subir a stack com outras portas):

| Variável          | Padrão                             | Uso                                                 |
| :---------------- | :--------------------------------- | :-------------------------------------------------- |
| `E2E_ALUNO_URL`   | `http://aluno.localhost:8088`      | área do aluno                                       |
| `E2E_ADM_URL`     | `http://adm.localhost:8088`        | ADM                                                 |
| `E2E_SITE_URL`    | `http://cursos.localhost:8088`     | site público                                        |
| `E2E_MAILPIT_URL` | `http://localhost:8026`            | e-mails (links de confirmação e de senha)           |
| `E2E_SENHA_SEED`  | `123456A#b123`                     | senha das contas do seed                            |
| `E2E_TOTP_SEED`   | `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP` | segredo do aplicativo autenticador do admin do seed |
| `E2E_SLOWMO`      | `0`                                | câmera lenta, em ms entre ações                     |

## Contas e sessão

- **Seed da stack** (criado pela API no perfil `e2e`): `admin@celke.com.br` e
  `aluno@celke.com.br`, senha `123456A#b123`. São **só leitura**: nenhum teste troca a senha,
  sai ou mexe no perfil delas.
- **Segundo fator do admin**: o admin do seed já tem o aplicativo autenticador cadastrado com o segredo
  de `SEED_E2E_TOTP`; `support/totp.ts` calcula o código como o celular, e as fixtures e o
  `confirmarSegundoFator` (`support/telas.ts`) passam pela segunda etapa. Na stack de e2e o mesmo código
  vale mais de uma vez (os testes entram várias vezes no mesmo intervalo de 30 s).
- **Sessão nova por teste**: as fixtures `comoAdmin` e `comoAluno` (`support/sessoes.ts`) entram pela API
  no contexto do navegador; ao abrir a página, o app renova a sessão pelo cookie `HttpOnly`. Não há
  sessão gravada: o refresh token é rotativo e um token reutilizado derruba a sessão inteira.
- **Conta própria**: teste que troca senha, sai, compra ou mexe no perfil cria um aluno novo
  (`support/contas.ts`), confirmado pelo link que chega no Mailpit.
- **Termos**: se outra jornada publicou termos, `aceitarTermosSePedir` (`support/telas.ts`) aceita antes
  de seguir.

## Dado de teste e limpeza

- Cursos, contas e ofertas levam um carimbo único (`pw<tempo><sequência>`, e-mails
  `pw...@teste.celke.uno`): execuções seguidas não colidem, mesmo sem limpar a stack.
- Os testes **não apagam** o que criam; o lixo some com o `down -v` da stack. Para começar do zero:
  `down -v` e `up` de novo.
- O pagamento não sai para a internet: a ida ao `pay.hotmart.com` é interceptada (`page.route`) e a
  notificação da Hotmart é enviada pelo próprio teste (`support/pagamentos.ts`, a partir das fixtures do
  backend).

## Os specs

**ADM** (`e2e/adm/`)

- **`acesso.spec.ts`** — entrada do admin com o código do aplicativo, volta para onde ia, código errado,
  senha errada; aluno barrado na tela e na API do ADM.
- **`usuarios.spec.ts`** — bloqueio de um aluno (não entra mais) e desbloqueio; o admin sem as ações na
  própria linha.
- **`conteudo.spec.ts`** — cursos (validação, slug repetido, capa em formato errado, exclusão com
  turmas, filtro), conteúdo da turma (reordenar, inativar, exclusão com aulas) e aula (vídeo recusado na
  tela e pela API, edição, arquivo de apoio recusado).
- **`curso-completo.spec.ts`** — cria um curso completo e envia um vídeo curto até ficar "pronto"
  (o worker converte de verdade).
- **`turma.spec.ts`** — matrículas (matricular, período, cancelar, e-mail sem conta, turma encerrada),
  moderação do fórum e configurações da turma (certificado, exclusão).
- **`termos.spec.ts`** — texto vazio barrado; publicação de uma versão nova.
- **`pagamentos.spec.ts`** — cadastra a Hotmart (validação, credencial errada recusada), ativa e vende
  uma turma; evento de oferta não cadastrada é ignorado e reprocessado depois; o pedido aparece no
  painel.
- **`auditoria.spec.ts`** — a trilha mostra quem criou o curso, de onde e os campos gravados.

**Aluno** (`e2e/aluno/`)

- **`entrar.spec.ts`** — login, senha errada, e-mail inexistente com a mesma resposta, volta para a
  página privada, redirecionamento externo ignorado, 404.
- **`cadastro.spec.ts`** — cadastro confirmado pelo link do e-mail; validações; e-mail já cadastrado não
  é revelado; link de confirmação inválido ou sem token.
- **`senha.spec.ts`** — recuperação de senha pelo e-mail e os casos de link inválido.
- **`sessao.spec.ts`** — a sessão volta pelo cookie ao abrir e recarregar; sair encerra; cookie de
  refresh reutilizado derruba a família.
- **`estudo.spec.ts`** — do curso à aula: o vídeo toca até o fim, o progresso sobe, retomada, vista e
  revisar, anterior e próxima, arquivo de apoio, fórum da aula, certificado (bloqueado → emitido →
  validado); sem matrícula, matrícula futura, arquivar.
- **`perfil.spec.ts`** — troca de nome, foto (formato errado recusado) e senha.
- **`privacidade.spec.ts`** — baixar a cópia dos dados (JSON com a conta) e excluir a conta (validação,
  e-mail de confirmação, a conta não entra mais).
- **`compra.spec.ts`** — compra pelo botão do site com a Hotmart interceptada: a página mostra curso e
  valor e só abre o pedido no "Ir para o pagamento"; aprovada (o curso aparece), reembolso (o curso
  some), boleto aguardando, e o "Comprar" sem login que volta para a compra depois de entrar.

**Site** (`e2e/site/`)

- **`site.spec.ts`** — home e catálogo com o curso publicado (rascunho não aparece), página do curso com
  meta tags, capa, 404, rotas da API fora das públicas barradas, sitemap e robots, validação de
  certificado e termos publicados. O site é regenerado a cada 3 s na stack de e2e; os testes recarregam
  até a mudança aparecer.

**Apoio** (`e2e/support/`): `config.ts` (endereços e trava), `api.ts` (montar dados pela API),
`sessoes.ts` (fixtures de sessão), `contas.ts` e `mailpit.ts` (contas confirmadas pelo e-mail),
`telas.ts` (login pela tela, segundo fator, termos, escolher opção do `CampoSelect`), `totp.ts` (código
do aplicativo autenticador), `amostras.ts` (arquivos de amostra), `pagamentos.ts` (Hotmart e
notificações).

## Lighthouse do site

Com a stack no ar, mede desempenho, acessibilidade, boas práticas e SEO das páginas do site (desktop) e
falha abaixo de 90, 95, 95 e 90:

```bash
npm run lighthouse
```

Precisa de pelo menos um curso publicado (rode antes o `npm run e2e -- e2e/site/`).

## Manutenção

- Seletores pelo que o usuário vê: campo pelo rótulo (`getByLabel`, `getByRole('textbox', { name })`),
  botão e link pelo nome acessível (`getByRole`), linha da tabela pelo dado que o próprio teste criou.
  **Nunca** por posição nem por classe interna da `@celke/kole-react` (`.lp-*`): ela muda numa
  atualização da biblioteca.
- O `CampoSelect` da biblioteca não dá nome acessível ao combobox: use `escolher(page, rótulo, opção)`.
- Se a tela mudar um rótulo por decisão de UX, **conserte o teste** — não volte o rótulo para acomodar a
  automação.
- Espera sempre por condição (`expect(...).toBeVisible()`, `expect.poll`, `toPass`), nunca por tempo
  fixo.
`````

`web/e2e/support/mailpit.ts`:

`````ts
import { expect } from '@playwright/test';
import { MAILPIT_URL } from './config.ts';

interface Busca {
  messages: { ID: string }[] | null;
}

/** Espera o worker entregar o e-mail e devolve o id dele no Mailpit. */
export async function aguardarEmail(destinatario: string, assunto: string): Promise<string> {
  let id: string | undefined;
  await expect
    .poll(
      async () => {
        const consulta = encodeURIComponent(`to:"${destinatario}" subject:"${assunto}"`);
        const resposta = await fetch(`${MAILPIT_URL}/api/v1/search?query=${consulta}`);
        id = ((await resposta.json()) as Busca).messages?.[0]?.ID;
        return id;
      },
      { message: `e-mail "${assunto}" para ${destinatario}`, timeout: 30_000 },
    )
    .toBeTruthy();
  return id!;
}

/** Espera o worker entregar o e-mail e devolve o link dele (o primeiro com ?token=). */
export async function linkDoEmail(destinatario: string, assunto: string): Promise<string> {
  const id = await aguardarEmail(destinatario, assunto);
  const resposta = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
  const { HTML } = (await resposta.json()) as { HTML: string };
  const link = /href="([^"]*[?&]token=[A-Za-z0-9_-]+)"/.exec(HTML)?.[1];
  if (!link) throw new Error(`O e-mail "${assunto}" não tem link com token.`);
  return link.replaceAll('&amp;', '&');
}
`````

### Arquivos novos

`backend/src/main/java/br/com/celke/cursos/auth/application/AuditoriaLoginProperties.java`:

`````java
package br.com.celke.cursos.auth.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param retencao tempo de guarda das tentativas de login (e-mail, IP e navegador) */
@Validated
@ConfigurationProperties("app.auth.auditoria-login")
public record AuditoriaLoginProperties(@NotNull Duration retencao) {}
`````

`backend/src/main/java/br/com/celke/cursos/auth/application/LimpezaAuditoriaLogin.java`:

`````java
package br.com.celke.cursos.auth.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Aplica a retenção da auditoria de login uma vez por dia. Só no worker, que tem o agendamento ligado. */
@Component
@ConditionalOnBooleanProperty("app.fila.consumidor-ativo")
public class LimpezaAuditoriaLogin {

    private final AuditoriaLoginService auditoriaLoginService;

    public LimpezaAuditoriaLogin(AuditoriaLoginService auditoriaLoginService) {
        this.auditoriaLoginService = auditoriaLoginService;
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        auditoriaLoginService.apagarVencidas();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/auth/infra/AuthDadosPessoais.java`:

`````java
package br.com.celke.cursos.auth.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Entradas na conta (auditoria de login: guardada pelo prazo do Marco Civil e apagada pela retenção).
 * Na exclusão, saem as sessões, os links de e-mail e o segundo fator.
 */
@Component
public class AuthDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public AuthDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "entradas";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT criado_em AS quando, resultado, ip, user_agent AS navegador
                          FROM auditoria_login WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("DELETE FROM refresh_tokens WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM tokens_uso_unico WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM codigos_recuperacao WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        jdbc.sql("DELETE FROM segundo_fator WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/certificado/infra/CertificadoDadosPessoais.java`:

`````java
package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Certificados emitidos. Na exclusão da conta, apagados com o PDF: o código deixa de validar (o nome
 * completo só existe neles).
 */
@Component
public class CertificadoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;
    private final StorageService storage;

    public CertificadoDadosPessoais(JdbcClient jdbc, StorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Override
    public String secao() {
        return "certificados";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT codigo, nome_completo, curso_titulo, turma_nome, carga_horaria_horas,
                               percentual_concluido, emitido_em
                          FROM certificados WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        List<String> pdfs = jdbc.sql(
                        "SELECT pdf_chave FROM certificados WHERE usuario_id = ? AND pdf_chave IS NOT NULL")
                .param(usuarioId)
                .query(String.class)
                .list();
        jdbc.sql("DELETE FROM certificados WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
        pdfs.forEach(storage::excluir);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/forum/infra/ForumDadosPessoais.java`:

`````java
package br.com.celke.cursos.forum.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Perguntas e respostas no fórum. Na exclusão da conta ficam, sem o autor (aparece "Conta excluída"):
 * fazem parte da conversa da turma, e as respostas dos outros dependem delas.
 */
@Component
public class ForumDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public ForumDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "forum";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT 'pergunta' AS tipo, id, titulo, corpo_markdown AS texto, criado_em
                          FROM forum_topicos WHERE autor_id = :usuario
                        UNION ALL
                        SELECT 'resposta', id, NULL, corpo_markdown, criado_em
                          FROM forum_respostas WHERE autor_id = :usuario
                         ORDER BY criado_em, id
                        """).param("usuario", usuarioId).query().listOfRows());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/matricula/infra/MatriculaDadosPessoais.java`:

`````java
package br.com.celke.cursos.matricula.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Matrículas. Na exclusão da conta ficam (ligadas à conta anonimizada): são o registro do serviço
 * contratado e, nas pagas, parte da prova da venda.
 */
@Component
public class MatriculaDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public MatriculaDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "matriculas";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT m.id, c.titulo AS curso, t.nome AS turma, m.inicio, m.fim, m.status,
                               m.forma_pagamento, m.arquivada_em, m.cancelada_em, m.criado_em
                          FROM matriculas m
                          JOIN turmas t ON t.id = m.turma_id
                          JOIN cursos c ON c.id = t.curso_id
                         WHERE m.usuario_id = ? ORDER BY m.id
                        """).param(usuarioId).query().listOfRows());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/pagamento/infra/PagamentoDadosPessoais.java`:

`````java
package br.com.celke.cursos.pagamento.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Pedidos. Na exclusão da conta ficam (valor, status e datas são registro fiscal da venda), sem o
 * e-mail e o nome do comprador.
 */
@Component
public class PagamentoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public PagamentoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "pedidos";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT codigo, email, nome, status, valor_centavos, moeda, forma_pagamento, criado_em,
                               atualizado_em
                          FROM pedidos WHERE usuario_id = ? ORDER BY id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("UPDATE pedidos SET email = :email, nome = NULL WHERE usuario_id = :usuario")
                .param("email", "excluida-" + usuarioId + "@conta-excluida.invalid")
                .param("usuario", usuarioId)
                .update();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/perfil/api/PrivacidadeController.java`:

`````java
package br.com.celke.cursos.perfil.api;

import br.com.celke.cursos.auth.api.CookieRefresh;
import br.com.celke.cursos.perfil.application.PrivacidadeService;
import br.com.celke.cursos.usuario.domain.PoliticaSenha;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil — privacidade")
public class PrivacidadeController {

    private final PrivacidadeService privacidadeService;
    private final CookieRefresh cookieRefresh;

    public PrivacidadeController(PrivacidadeService privacidadeService, CookieRefresh cookieRefresh) {
        this.privacidadeService = privacidadeService;
        this.cookieRefresh = cookieRefresh;
    }

    @GetMapping("/dados")
    @Operation(
            summary = "Baixar meus dados",
            description = "Todos os dados da conta em JSON (conta, matrículas, progresso, fórum, certificados,"
                    + " pedidos, aceites dos termos, entradas e acessos aos vídeos).")
    public ResponseEntity<Map<String, Object>> dados(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("meus-dados.json")
                                .build()
                                .toString())
                .body(privacidadeService.exportar(Long.valueOf(jwt.getSubject())));
    }

    @PostMapping("/exclusao")
    @Operation(
            summary = "Excluir minha conta",
            description = "Exige a senha e a palavra EXCLUIR. Apaga ou anonimiza os dados, encerra as sessões"
                    + " e avisa por e-mail. Não vale para administradores.")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Exclusao request) {
        privacidadeService.excluirConta(Long.valueOf(jwt.getSubject()), request.senha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.limpar().toString())
                .build();
    }

    public record Exclusao(
            @NotBlank @Size(max = PoliticaSenha.MAXIMO) String senha,

            @NotBlank @Pattern(regexp = "EXCLUIR", message = "Digite EXCLUIR para confirmar.")
            String confirmacao) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/perfil/application/PrivacidadeService.java`:

`````java
package br.com.celke.cursos.perfil.application;

import br.com.celke.cursos.auth.application.RefreshTokenService;
import br.com.celke.cursos.auth.application.SessaoValidaValidator;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.email.EmailService;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.usuario.application.UsuarioService;
import br.com.celke.cursos.usuario.domain.Papel;
import br.com.celke.cursos.usuario.domain.Usuario;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Direitos do titular (LGPD, art. 18): a cópia de todos os dados da conta e a exclusão da conta. Cada
 * módulo responde pelas próprias tabelas ({@link DadosPessoais}); o que a lei manda guardar fica, sem
 * nome nem e-mail, até a retenção apagar.
 */
@Service
public class PrivacidadeService {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacidadeService.class);

    private final UsuarioService usuarioService;
    private final List<DadosPessoais> fontes;
    private final RefreshTokenService refreshTokenService;
    private final SessaoValidaValidator sessoes;
    private final StorageService storage;
    private final EmailService emailService;
    private final Clock clock;

    public PrivacidadeService(
            UsuarioService usuarioService,
            List<DadosPessoais> fontes,
            RefreshTokenService refreshTokenService,
            SessaoValidaValidator sessoes,
            StorageService storage,
            EmailService emailService,
            Clock clock) {
        this.usuarioService = usuarioService;
        this.fontes = fontes;
        this.refreshTokenService = refreshTokenService;
        this.sessoes = sessoes;
        this.storage = storage;
        this.emailService = emailService;
        this.clock = clock;
    }

    /** Todos os dados da conta, por seção (conta, matrículas, progresso, fórum, pedidos...). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Long usuarioId) {
        Usuario usuario = buscar(usuarioId);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", clock.instant().toString());
        dados.put("conta", conta(usuario));
        fontes.stream()
                .sorted((a, b) -> a.secao().compareTo(b.secao()))
                .forEach(fonte -> dados.put(fonte.secao(), fonte.exportar(usuarioId)));
        return dados;
    }

    /**
     * Exclui a conta: confere a senha, avisa por e-mail, apaga ou anonimiza os dados de cada módulo,
     * anonimiza a conta e encerra as sessões. Administrador não se exclui por aqui (a plataforma ficaria
     * sem quem a administra).
     */
    @Transactional
    public void excluirConta(Long usuarioId, String senha) {
        Usuario usuario = buscar(usuarioId);
        if (usuario.getPapeis().contains(Papel.ADMIN)) {
            throw new RegraNegocioException(
                    HttpStatus.CONFLICT,
                    "Contas de administrador não são excluídas por aqui. Fale com outro administrador.");
        }
        if (!usuarioService.senhaConfere(usuario, senha)) {
            throw new RegraNegocioException(HttpStatus.BAD_REQUEST, "A senha não confere.");
        }
        // O aviso vai para o e-mail de antes da anonimização (a fila guarda o destinatário)
        emailService.enviar(
                usuario.getEmail(), "Sua conta foi excluída", "conta-excluida", Map.of("nome", usuario.getNome()));
        fontes.forEach(fonte -> fonte.excluir(usuarioId));
        refreshTokenService.revogarTodas(usuarioId);
        usuarioService.anonimizar(usuarioId).ifPresent(this::apagarDepoisDoCommit);
        // O access token já emitido deixa de valer na hora
        sessoes.esquecer(usuarioId);
        LOG.info("Conta {} excluída a pedido do titular", usuarioId);
    }

    private Map<String, Object> conta(Usuario usuario) {
        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("id", usuario.getId());
        conta.put("nome", usuario.getNome());
        conta.put("email", usuario.getEmail());
        conta.put("papeis", usuario.getPapeis().stream().sorted().toList());
        conta.put("criadaEm", usuario.getCriadoEm().toString());
        conta.put(
                "emailConfirmadoEm",
                usuario.getEmailConfirmadoEm() == null
                        ? null
                        : usuario.getEmailConfirmadoEm().toString());
        conta.put("temFoto", usuario.getFotoChave() != null);
        return conta;
    }

    /** A foto sai do bucket só se a exclusão for gravada. */
    private void apagarDepoisDoCommit(String chave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.excluir(chave);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.excluir(chave);
            }
        });
    }

    private Usuario buscar(Long usuarioId) {
        return usuarioService
                .buscarPorId(usuarioId)
                .orElseThrow(() -> new NaoEncontradoException("Conta não encontrada."));
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/progresso/infra/ProgressoDadosPessoais.java`:

`````java
package br.com.celke.cursos.progresso.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Progresso nas aulas. Na exclusão da conta, apagado. */
@Component
public class ProgressoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public ProgressoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "progresso";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT p.aula_id, a.titulo AS aula, p.posicao_segundos, p.vista, p.vista_em, p.revisar,
                               p.atualizado_em
                          FROM progresso_aulas p JOIN aulas a ON a.id = p.aula_id
                         WHERE p.usuario_id = ? ORDER BY p.id
                        """).param(usuarioId).query().listOfRows());
    }

    @Override
    public void excluir(Long usuarioId) {
        jdbc.sql("DELETE FROM progresso_aulas WHERE usuario_id = ?")
                .param(usuarioId)
                .update();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/lgpd/DadosPessoais.java`:

`````java
package br.com.celke.cursos.shared.lgpd;

import java.util.List;
import java.util.Map;

/**
 * Dados pessoais que um módulo guarda de um usuário, para os direitos do titular (LGPD, art. 18):
 * exportar tudo e excluir a conta. Cada módulo implementa para as próprias tabelas; quem pede não
 * conhece as tabelas de ninguém.
 */
public interface DadosPessoais {

    /** Nome da seção na exportação (ex.: {@code matriculas}). */
    String secao();

    /** Registros do usuário, em ordem cronológica. */
    List<Map<String, Object>> exportar(Long usuarioId);

    /**
     * Na exclusão da conta: apaga o que não precisa ficar e anonimiza o que a lei manda guardar. O padrão
     * é manter (registros com prazo legal de guarda, que a retenção apaga depois).
     */
    default void excluir(Long usuarioId) {}
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/lgpd/Linhas.java`:

`````java
package br.com.celke.cursos.shared.lgpd;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Linhas de uma consulta prontas para a exportação: datas como instantes ISO-8601. */
public final class Linhas {

    private Linhas() {}

    public static List<Map<String, Object>> de(List<Map<String, Object>> linhas) {
        return linhas.stream().map(Linhas::linha).toList();
    }

    private static Map<String, Object> linha(Map<String, Object> original) {
        Map<String, Object> linha = new LinkedHashMap<>();
        original.forEach((coluna, valor) -> linha.put(coluna, valor(valor)));
        return linha;
    }

    private static Object valor(Object valor) {
        if (valor instanceof Timestamp t) {
            return t.toInstant().toString();
        }
        if (valor instanceof OffsetDateTime o) {
            return o.toInstant().toString();
        }
        return valor;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/termo/infra/TermoDadosPessoais.java`:

`````java
package br.com.celke.cursos.termo.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Aceites dos termos de uso e da política de privacidade. Na exclusão da conta ficam: são a prova do
 * consentimento (LGPD, art. 8º, § 2º).
 */
@Component
public class TermoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public TermoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "aceitesTermos";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT t.tipo, t.versao, a.aceito_em, a.ip, a.user_agent AS navegador
                          FROM aceites_termos a JOIN termos t ON t.id = a.termo_id
                         WHERE a.usuario_id = ? ORDER BY a.id
                        """).param(usuarioId).query().listOfRows());
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/video/infra/VideoDadosPessoais.java`:

`````java
package br.com.celke.cursos.video.infra;

import br.com.celke.cursos.shared.lgpd.DadosPessoais;
import br.com.celke.cursos.shared.lgpd.Linhas;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Acessos aos vídeos. Na exclusão da conta ficam até a retenção apagá-los (6 meses, prazo do Marco Civil
 * para os registros de acesso a aplicações).
 */
@Component
public class VideoDadosPessoais implements DadosPessoais {

    private final JdbcClient jdbc;

    public VideoDadosPessoais(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String secao() {
        return "acessosVideo";
    }

    @Override
    public List<Map<String, Object>> exportar(Long usuarioId) {
        return Linhas.de(jdbc.sql("""
                        SELECT v.aula_id, a.titulo AS aula, v.acessado_em, v.ip, v.user_agent AS navegador
                          FROM acessos_video v JOIN aulas a ON a.id = v.aula_id
                         WHERE v.usuario_id = ? ORDER BY v.id
                        """).param(usuarioId).query().listOfRows());
    }
}
`````

`backend/src/main/resources/templates/email/conta-excluida.html`:

`````html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Sua conta foi excluída</title>
</head>
<body style="margin:0;padding:24px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px;">
    <h1 style="margin:0 0 16px;font-size:20px;">Sua conta foi excluída</h1>
    <p>Olá, <span th:text="${nome}">aluno</span>!</p>
    <p>Atendemos ao seu pedido: a sua conta na Plataforma de Cursos foi excluída. O seu nome, o seu e-mail,
        a sua foto, o progresso nas aulas e os certificados foram apagados, e você saiu de todos os
        dispositivos.</p>
    <p>Alguns registros ficam pelo prazo que a lei manda guardar, sem o seu nome nem o seu e-mail na conta:
        as matrículas e os pedidos (registro da venda), os aceites dos termos (prova do consentimento) e os
        registros de acesso. Os detalhes estão na política de privacidade.</p>
    <p style="color:#6b7280;font-size:13px;">Se você não pediu a exclusão, responda este e-mail.</p>
</div>
</body>
</html>
`````

`backend/src/test/java/br/com/celke/cursos/auth/AuditoriaLoginRetencaoIT.java`:

`````java
package br.com.celke.cursos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.auth.application.AuditoriaLoginService;
import br.com.celke.cursos.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Retenção da auditoria de login: o que passou de 180 dias sai; o resto fica. */
class AuditoriaLoginRetencaoIT extends IntegrationTest {

    @Autowired
    private AuditoriaLoginService auditoriaLoginService;

    @Autowired
    private JdbcClient jdbc;

    private void registrar(String email, Instant quando) {
        jdbc.sql("""
                        INSERT INTO auditoria_login (email, resultado, ip, criado_em)
                        VALUES (?, 'SENHA_INVALIDA', '203.0.113.7', ?)
                        """).param(email).param(Timestamp.from(quando)).update();
    }

    private long quantos(String email) {
        return jdbc.sql("SELECT count(*) FROM auditoria_login WHERE email = ?")
                .param(email)
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("apaga as tentativas de mais de 180 dias e mantém as recentes")
    void retencao() {
        String antiga = stamp() + "@teste.celke.uno";
        String recente = stamp() + "@teste.celke.uno";
        registrar(antiga, Instant.now().minus(Duration.ofDays(181)));
        registrar(recente, Instant.now().minus(Duration.ofDays(179)));

        assertThat(auditoriaLoginService.apagarVencidas()).isPositive();

        assertThat(quantos(antiga)).isZero();
        assertThat(quantos(recente)).isEqualTo(1);
    }
}
`````

`backend/src/test/java/br/com/celke/cursos/perfil/PrivacidadeIT.java`:

`````java
package br.com.celke.cursos.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import br.com.celke.cursos.support.Mailpit;
import br.com.celke.cursos.usuario.domain.Papel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Direitos do titular: a cópia dos dados em JSON e a exclusão da conta (senha e confirmação, aviso por
 * e-mail, conta anonimizada, sessões encerradas, registros com prazo legal mantidos).
 */
class PrivacidadeIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Mailpit mailpit;

    private MvcTestResult excluir(String token, String senha, String confirmacao) {
        return mvc.post()
                .uri("/api/perfil/exclusao")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"%s\",\"confirmacao\":\"%s\"}".formatted(senha, confirmacao))
                .exchange();
    }

    @Test
    @DisplayName("baixar meus dados: JSON em anexo com a conta e cada seção; sem login, 401")
    void exportar() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        assertThat(mvc.get().uri("/api/perfil/dados")).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult dados = mvc.get()
                .uri("/api/perfil/dados")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
        assertThat(dados)
                .hasStatusOk()
                .hasHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados.json\"");
        assertThat(dados).bodyJson().extractingPath("$.conta.email").isEqualTo(aluno.email());
        assertThat(dados).bodyJson().extractingPath("$.entradas[0].resultado").isEqualTo("SUCESSO");
        for (String secao : new String[] {
            "matriculas", "progresso", "forum", "certificados", "pedidos", "aceitesTermos", "acessosVideo"
        }) {
            assertThat(dados).bodyJson().hasPath("$." + secao);
        }
    }

    @Test
    @DisplayName("excluir: senha errada ou sem a confirmação é recusado; certo anonimiza, encerra e avisa")
    void excluirConta() {
        UsuarioTeste aluno = criarUsuario(Papel.ALUNO);
        String token = login(aluno.email(), aluno.senha());

        assertThat(excluir(token, "senha-errada-qualquer", "EXCLUIR")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(excluir(token, aluno.senha(), "excluir")).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult excluida = excluir(token, aluno.senha(), "EXCLUIR");
        assertThat(excluida).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(excluida.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        // A sessão cai na hora e a conta não entra mais
        assertThat(mvc.get().uri("/api/perfil").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(aluno.email(), aluno.senha())))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        var conta = jdbc.sql("SELECT nome, email, senha_hash, ativo FROM usuarios WHERE id = ?")
                .param(aluno.id())
                .query()
                .singleRow();
        assertThat(conta.get("nome")).isEqualTo("Conta excluída");
        assertThat(conta.get("email")).isEqualTo("excluida-" + aluno.id() + "@conta-excluida.invalid");
        assertThat(conta.get("senha_hash")).isNull();
        assertThat(conta.get("ativo")).isEqualTo(false);
        assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE usuario_id = ?")
                        .param(aluno.id())
                        .query(Long.class)
                        .single())
                .isZero();
        // A auditoria de login fica pelo prazo legal
        assertThat(jdbc.sql("SELECT count(*) FROM auditoria_login WHERE usuario_id = ?")
                        .param(aluno.id())
                        .query(Long.class)
                        .single())
                .isPositive();

        processarFila();
        assertThat(mailpit.aguardarEmailPara(aluno.email()).Subject()).isEqualTo("Sua conta foi excluída");
    }

    @Test
    @DisplayName("administrador não exclui a própria conta por aqui")
    void administrador() {
        UsuarioTeste admin = criarUsuario(Papel.ADMIN);

        assertThat(excluir(login(admin.email(), admin.senha()), admin.senha(), "EXCLUIR"))
                .hasStatus(HttpStatus.CONFLICT);
    }
}
`````

`docs/politica-privacidade-modelo.md`:

`````markdown
# Modelo de política de privacidade

Texto de partida para publicar em **ADM → Termos → Política de privacidade**. Troque o que está entre
`[colchetes]` pelos dados da empresa e revise com quem cuida do jurídico antes de publicar: o modelo
descreve o que a plataforma faz de fato (dados, finalidades, prazos e direitos), não substitui a
análise do caso.

Os prazos abaixo são os que o sistema aplica. Se mudar um prazo na configuração
(`application.yaml`), mude também o texto publicado.

---

```markdown
# Política de privacidade

Última versão publicada nesta página. A [Razão social], CNPJ [00.000.000/0001-00], com sede em
[endereço] ("nós"), é a controladora dos dados pessoais tratados na Plataforma de Cursos. Encarregado
pelo tratamento de dados (DPO): [nome], [e-mail do encarregado].

## 1. Quais dados tratamos e para quê

| Dados | Para quê | Base legal (LGPD, art. 7º) |
|---|---|---|
| Nome, e-mail e senha (guardada só como hash) | Criar e manter a sua conta, entrar, recuperar a senha | Execução de contrato |
| Foto de perfil (opcional) | Mostrar no fórum e no seu perfil | Consentimento (você envia e remove quando quiser) |
| Matrículas, progresso nas aulas, perguntas e respostas no fórum, certificados | Prestar o curso: liberar o conteúdo, retomar de onde parou, responder dúvidas, emitir e validar o certificado | Execução de contrato |
| Pedidos (curso, valor, forma de pagamento, situação, e-mail e nome do comprador) | Vender o curso e liberar o acesso depois do pagamento | Execução de contrato e obrigação legal |
| Notificações do meio de pagamento (Hotmart) | Confirmar pagamentos, reembolsos e disputas | Execução de contrato |
| Endereço IP, navegador, data e hora das entradas na conta e dos acessos às aulas em vídeo | Segurança da conta (bloqueio de tentativas) e guarda dos registros de acesso | Obrigação legal (Marco Civil da Internet, art. 15) e legítimo interesse |
| Aceites dos termos de uso e desta política (com IP e navegador) | Comprovar o seu consentimento | Obrigação legal (LGPD, art. 8º) |
| Registro das alterações feitas na plataforma (quem, o quê, quando, de onde) | Segurança, prevenção a fraudes e defesa em processos | Legítimo interesse e exercício regular de direitos |

Não vendemos dados pessoais e não usamos os seus dados para publicidade.

## 2. Com quem compartilhamos

- **Meio de pagamento** ([Hotmart]): recebe o seu nome e e-mail para preencher o checkout e nos avisa
  do resultado do pagamento. O tratamento dos dados de pagamento segue a política deles.
- **Envio de e-mails** ([provedor de e-mail]): recebe o seu e-mail e o conteúdo das mensagens que a
  plataforma envia (confirmação, recuperação de senha, matrícula).
- **Hospedagem** ([provedor da VPS]): os servidores onde ficam o banco, os vídeos e os backups, no
  [Brasil / país].
- Autoridades, quando a lei ou uma ordem judicial exigir.

## 3. Por quanto tempo guardamos

| Dados | Prazo |
|---|---|
| Conta (nome, e-mail, senha, foto), progresso e certificados | Enquanto a conta existir. Na exclusão da conta, apagados |
| Perguntas e respostas no fórum | Enquanto a turma existir; na exclusão da conta, ficam sem o seu nome ("Conta excluída") |
| Matrículas e pedidos (valor, situação, datas) | 5 anos depois da compra (prazo de prescrição do Código de Defesa do Consumidor, art. 27, e guarda fiscal). Na exclusão da conta, o e-mail e o nome saem do pedido |
| Corpo das notificações do meio de pagamento (inclui e-mail e nome do comprador) | 180 dias |
| Entradas na conta (e-mail, IP, navegador, resultado) e acessos às aulas em vídeo (IP, navegador) | 6 meses (Marco Civil da Internet, art. 15) |
| Registro das alterações na plataforma (inclui e-mail, IP e os valores antes e depois de cada alteração, sem senhas nem segredos) | 5 anos |
| Aceites dos termos e desta política | Pelo tempo necessário para comprovar o consentimento |
| Logs técnicos dos servidores (IP, endereço acessado, data e hora) | 180 dias |
| Cópias de segurança (backups) | Até 6 meses (diárias por 7 dias, semanais por 4 semanas, mensais por 6 meses); um dado apagado da plataforma some dos backups nesse prazo |

## 4. Seus direitos (LGPD, art. 18)

No seu perfil, em **Privacidade**:

- **Baixar meus dados**: uma cópia, em arquivo JSON, de tudo o que guardamos sobre você.
- **Excluir minha conta**: apaga o seu nome, e-mail, senha, foto, progresso e certificados e encerra
  todas as sessões. O que a lei manda guardar (item 3) fica pelo prazo indicado, sem o seu nome nem o
  seu e-mail na conta. Você recebe a confirmação por e-mail.

Para corrigir dados, use o seu perfil. Para os demais direitos (confirmação do tratamento, informação
sobre compartilhamento, revogação do consentimento, revisão de decisões, petição à ANPD), escreva para
[e-mail do encarregado]. Respondemos em até 15 dias.

## 5. Segurança

Conexões sempre em HTTPS; senhas guardadas só como hash (Argon2); verificação em duas etapas para os
administradores; segredos de terceiros cifrados; acesso aos vídeos e arquivos só por links temporários;
registro de todas as alterações; backups diários cifrados. Em caso de incidente que possa causar risco
ou dano relevante, avisamos você e a ANPD.

## 6. Cookies

Usamos um único cookie, necessário para manter você conectado (sessão), `HttpOnly` e restrito à
plataforma. Não usamos cookies de publicidade nem de rastreamento.

## 7. Mudanças nesta política

Quando esta política mudar, você será avisado ao entrar e precisará aceitar a nova versão para
continuar usando a plataforma.
```
`````

`web/aluno/src/paginas/perfil/Privacidade.tsx`:

`````tsx
import { Alerta, Botao, Card, CampoSenha, CampoTexto, Dialogo } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import {
  erroConfirmacaoExclusao,
  nomeArquivoDados,
  PALAVRA_EXCLUSAO,
  urlPoliticaPrivacidade,
} from './privacidade.ts';

/**
 * Direitos do titular: baixar a cópia de todos os dados da conta e excluir a conta. A política de
 * privacidade explica o que é apagado e o que fica pelo prazo legal.
 */
export function Privacidade() {
  const { executar, executando } = useAcao();
  const [excluindo, setExcluindo] = useState(false);

  const baixar = () =>
    executar(async () => {
      const dados = await api.get<unknown>('/api/perfil/dados');
      const arquivo = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(arquivo);
      link.download = nomeArquivoDados(new Date());
      link.click();
      URL.revokeObjectURL(link.href);
    }, 'Cópia dos dados baixada');

  return (
    <Card titulo="Privacidade">
      <div className="pc-pilha">
        <p>
          Baixe uma cópia de tudo o que a plataforma guarda sobre você: conta, matrículas,
          progresso, fórum, certificados, pedidos, aceites dos termos e registros de acesso.
        </p>
        <div>
          <Botao
            rotulo="Baixar meus dados"
            icone="download"
            aparencia="contorno"
            carregando={executando}
            onClick={() => void baixar()}
          />
        </div>
        <p>
          Excluir a conta apaga o seu nome, e-mail, foto, progresso e certificados e encerra todas
          as sessões. Matrículas, pedidos, aceites dos termos e registros de acesso ficam pelo prazo
          que a lei manda guardar, sem o seu nome nem o seu e-mail.{' '}
          <a
            className="pc-link"
            href={urlPoliticaPrivacidade(window.location)}
            target="_blank"
            rel="noreferrer"
          >
            Política de privacidade
          </a>
        </p>
        <div>
          <Botao
            rotulo="Excluir minha conta"
            icone="trash-2"
            severidade="err"
            aparencia="contorno"
            onClick={() => setExcluindo(true)}
          />
        </div>
      </div>
      {excluindo && <DialogoExclusao aoFechar={() => setExcluindo(false)} />}
    </Card>
  );
}

function DialogoExclusao({ aoFechar }: { aoFechar: () => void }) {
  const [senha, setSenha] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<{ senha?: string; confirmacao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);

  const excluir = async () => {
    const novos = {
      senha: senha ? undefined : 'Informe a sua senha.',
      confirmacao: erroConfirmacaoExclusao(confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (novos.senha || novos.confirmacao) return;
    setEnviando(true);
    try {
      await api.post('/api/perfil/exclusao', { senha, confirmacao: confirmacao.trim() });
      // A API encerrou as sessões e limpou o cookie: recarrega na entrada, descartando o token da aba
      window.location.replace(`${CAMINHOS.entrar}?conta=excluida`);
    } catch (erro) {
      setErroGeral(mensagemDeErro(erro));
      setEnviando(false);
    }
  };

  return (
    <Dialogo
      aberto
      titulo="Excluir minha conta"
      subtitulo="Não dá para desfazer."
      aoFechar={aoFechar}
      rodape={
        <>
          <Botao rotulo="Voltar" aparencia="contorno" onClick={aoFechar} />
          <Botao
            rotulo="Excluir a conta"
            icone="trash-2"
            severidade="err"
            carregando={enviando}
            onClick={() => void excluir()}
          />
        </>
      }
    >
      <div className="pc-pilha">
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Sua senha"
          valor={senha}
          aoMudar={setSenha}
          erro={erros.senha}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoTexto
          rotulo={`Digite ${PALAVRA_EXCLUSAO} para confirmar`}
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="off"
        />
      </div>
    </Dialogo>
  );
}
`````

`web/aluno/src/paginas/perfil/privacidade.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import {
  erroConfirmacaoExclusao,
  nomeArquivoDados,
  urlPoliticaPrivacidade,
} from './privacidade.ts';

describe('erroConfirmacaoExclusao', () => {
  it('só aceita a palavra EXCLUIR, em maiúsculas', () => {
    expect(erroConfirmacaoExclusao('EXCLUIR')).toBeUndefined();
    expect(erroConfirmacaoExclusao(' EXCLUIR ')).toBeUndefined();
    expect(erroConfirmacaoExclusao('excluir')).toBe('Digite EXCLUIR para confirmar.');
    expect(erroConfirmacaoExclusao('')).toBe('Digite EXCLUIR para confirmar.');
  });
});

describe('nomeArquivoDados', () => {
  it('leva a data local com zeros à esquerda', () => {
    expect(nomeArquivoDados(new Date(2026, 0, 5, 23, 59))).toBe('meus-dados-2026-01-05.json');
  });
});

describe('urlPoliticaPrivacidade', () => {
  it('troca o subdomínio do aluno pelo do site', () => {
    expect(urlPoliticaPrivacidade({ protocol: 'https:', host: 'aluno.celke.uno' })).toBe(
      'https://cursos.celke.uno/privacidade/',
    );
    expect(urlPoliticaPrivacidade({ protocol: 'http:', host: 'aluno.localhost:8088' })).toBe(
      'http://cursos.localhost:8088/privacidade/',
    );
  });
});
`````

`web/aluno/src/paginas/perfil/privacidade.ts`:

`````ts
/** Regras puras do card de privacidade do perfil. */

export const PALAVRA_EXCLUSAO = 'EXCLUIR';

export function erroConfirmacaoExclusao(texto: string): string | undefined {
  return texto.trim() === PALAVRA_EXCLUSAO
    ? undefined
    : `Digite ${PALAVRA_EXCLUSAO} para confirmar.`;
}

/** Nome do arquivo da cópia dos dados, com a data local (meus-dados-2026-01-10.json). */
export function nomeArquivoDados(agora: Date): string {
  const dia = [agora.getFullYear(), agora.getMonth() + 1, agora.getDate()]
    .map((n) => String(n).padStart(2, '0'))
    .join('-');
  return `meus-dados-${dia}.json`;
}

/** Política de privacidade no site público (aluno.<domínio> → cursos.<domínio>/privacidade/). */
export function urlPoliticaPrivacidade(local: { protocol: string; host: string }): string {
  return `${local.protocol}//${local.host.replace(/^aluno\./, 'cursos.')}/privacidade/`;
}
`````

`web/e2e/aluno/privacidade.spec.ts`:

`````ts
import { readFile } from 'node:fs/promises';
import { ALUNO_URL } from '../support/config.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { aguardarEmail } from '../support/mailpit.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

test.describe('Privacidade', () => {
  test('baixa a cópia dos dados em JSON', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Baixar meus dados' }).click();
    const arquivo = await download;
    expect(arquivo.suggestedFilename()).toMatch(/^meus-dados-\d{4}-\d{2}-\d{2}\.json$/);
    const dados = JSON.parse(await readFile(await arquivo.path(), 'utf-8')) as {
      conta: { email: string };
      entradas: unknown[];
    };
    expect(dados.conta.email).toBe(email);
    expect(dados.entradas.length).toBeGreaterThan(0);
  });

  test('exclui a conta com a senha e a confirmação; ela não entra mais', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    await page.getByRole('button', { name: 'Excluir minha conta' }).click();
    const dialogo = page.getByRole('dialog');
    await dialogo.getByRole('button', { name: 'Excluir a conta' }).click();
    await expect(dialogo.getByText('Informe a sua senha.')).toBeVisible();
    await expect(dialogo.getByText('Digite EXCLUIR para confirmar.')).toBeVisible();

    await dialogo.getByRole('textbox', { name: 'Sua senha' }).fill(SENHA_NOVA);
    await dialogo.getByRole('textbox', { name: 'Digite EXCLUIR para confirmar' }).fill('EXCLUIR');
    await dialogo.getByRole('button', { name: 'Excluir a conta' }).click();

    await expect(page).toHaveURL(/\/entrar\?conta=excluida/);
    await expect(page.getByText('Sua conta foi excluída.', { exact: false })).toBeVisible();
    expect(await aguardarEmail(email, 'Sua conta foi excluída')).toBeTruthy();
    const login = await request.post(`${ALUNO_URL}/api/auth/login`, {
      data: { email, senha: SENHA_NOVA },
    });
    expect(login.status()).toBe(401);
  });
});
`````

## Antes de executar

Fase 18 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/lgpd
```

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: com um aluno, Perfil → Privacidade → "Baixar meus dados" baixa o JSON; "Excluir minha
  conta" leva à entrada com o aviso, o e-mail chega no Mailpit e o login é recusado.

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md
git commit -m "feat(perfil): baixar os dados e excluir a conta pelo perfil"
git push -u origin feature/lgpd
```

Abra o Pull Request de `feature/lgpd` para `homolog`.

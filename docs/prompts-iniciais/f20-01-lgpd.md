# LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 20
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
  (Fase 20), capítulos (Parte 13), `README.md` (funcionalidade e link do modelo) e `web/e2e/README.md`.

## Arquivos

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

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

      # A transcodificação de vídeo é testada com o ffmpeg de verdade. Build estático (sem o apt, cujo
      # espelho trava no meio dos downloads), guardado em cache entre as execuções
      - name: Cache do ffmpeg
        uses: actions/cache@v6
        with:
          path: ~/ffmpeg
          key: ffmpeg-estatico-7-${{ runner.os }}

      - name: Instalar ffmpeg
        timeout-minutes: 10
        run: |
          if [ ! -x ~/ffmpeg/ffmpeg ]; then
            mkdir -p ~/ffmpeg
            curl -fsSL --retry 3 --retry-delay 5 --max-time 300 \
              https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz \
              | tar -xJ -C ~/ffmpeg --strip-components=1 --wildcards '*/ffmpeg' '*/ffprobe'
          fi
          echo "$HOME/ffmpeg" >> "$GITHUB_PATH"
          ~/ffmpeg/ffmpeg -version | head -1

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

  frontend:
    name: Frontend (lint, tipos, unitários e build)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: web
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # O lock guarda o kole-react como git+ssh; o repositório é público e vem por HTTPS
      - name: Dependências
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund

      # Falha com vulnerabilidade alta ou crítica nas dependências que vão para o navegador
      - name: Auditoria das dependências
        run: npm audit --omit=dev --audit-level=high
      - run: npm run format:check
      - run: npm run lint
      - run: npm run typecheck
      - run: npm test
      - run: npm run build

  e2e:
    name: e2e (stack completa)
    needs: [testes, frontend]
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

      - name: Imagem dos fronts
        uses: docker/build-push-action@v7
        with:
          context: web
          target: web
          tags: plataforma-cursos-web:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web
          cache-to: type=gha,mode=max,scope=web

      - name: Imagem do gerador do site
        uses: docker/build-push-action@v7
        with:
          context: web
          target: site
          tags: plataforma-cursos-site:e2e
          load: true
          labels: org.opencontainers.image.source=https://github.com/${{ github.repository }}
          cache-from: type=gha,scope=web

      # Vulnerabilidades críticas com correção disponível nas imagens (sistema e dependências Java)
      - name: Varredura da imagem da API (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-api:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do worker (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-worker:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem dos fronts (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-web:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Varredura da imagem do site (Trivy)
        uses: aquasecurity/trivy-action@v0.36.0
        with:
          image-ref: plataforma-cursos-site:e2e
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"

      - name: Subir a stack de e2e
        run: $COMPOSE_E2E up -d --no-build --wait

      - name: Jornadas e2e
        working-directory: backend
        run: ./mvnw -B verify -Pe2e

      - uses: actions/setup-node@v6
        with:
          node-version: "24"
          cache: npm
          cache-dependency-path: web/package-lock.json

      # Navegador real contra os fronts servidos pelo container web, atrás do Caddy
      - name: Jornadas e2e dos fronts (Playwright)
        working-directory: web
        run: |
          git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
          npm ci --no-audit --no-fund
          npx playwright install --with-deps chromium
          npm run e2e

      # Meta de Lighthouse do site público (desempenho, acessibilidade, boas práticas e SEO)
      - name: Lighthouse do site
        working-directory: web
        run: npm run lighthouse

      # Só depois do merge, e só se o e2e passou: as imagens publicadas são exatamente as testadas
      - name: Publicar imagens no GHCR
        if: github.event_name == 'push'
        env:
          REGISTRO: ghcr.io/${{ github.repository_owner }}
        run: |
          echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
          for imagem in api worker web site; do
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
            web/playwright-report
            web/test-results

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

`docs/capitulos.md`:

`````markdown
# Roteiro do tutorial — capítulos

Roteiro de gravação da Plataforma de Cursos. Os capítulos seguem a ordem das fases do
[plano de implementação](plano-implementacao.md): primeiro todo o backend, com infraestrutura,
testes, pipeline e deploy; depois o frontend. Cada capítulo é gravado executando os
[prompts](prompts/README.md) da etapa correspondente.

## Formato de cada capítulo

Cada capítulo traz: **Objetivo**, **Conteúdo**, **Segurança** (quando houver), **Testes** e
**Entrega**. A entrega é o que quem acompanha o tutorial precisa ter funcionando no fim do
capítulo.

| Parte | Capítulos | Fases do plano |
|---|---|---|
| 1. Fundação | 1–5 | 0, 1.1–1.3 |
| 2. Autenticação e segurança | 6–10 | 1.4–1.6 |
| 3. Qualidade e entrega contínua | 11–14 | 1.7–1.9 |
| 4. Domínio de cursos | 15–18 | 2, 3 |
| 5. Vídeo | 19–24 | 4, 5 |
| 6. Restante do domínio | 25–28 | 6–9 |
| 7. Operação | 29–31 | 10 |
| 8. Frontend com `@celke/kole-react` | 32–36 | 11–14 |
| 9. Migração do legado (opcional) | 37 | 15 |
| 10. Vendas e pagamentos | 38–40 | 16 |
| 11. Auditoria e logs | 41 | 17 |

---

## Parte 1 — Fundação

### Cap. 1 — Visão geral e arquitetura

**Fase:** 0

- **Objetivo:** entender o que será construído, o que fica de fora e como as peças se ligam.
- **Conteúdo:**
  - Funcionalidades que vêm do CLMS legado: cursos, turmas, módulos, aulas em vídeo, matrícula, progresso, arquivos de apoio, fórum, certificado, perfil e termos.
  - O que fica de fora: LeadLovers, automação de e-mail, clube de desafios. A venda pela Hotmart entra no fim, na Parte 10, numa estrutura que aceita outros meios de pagamento.
  - Arquitetura: uma VPS da Hostinger (2 vCPU, 8 GB, 100 GB NVMe) com tudo em Docker — Caddy (HTTPS e roteamento), API e worker Spring Boot, PostgreSQL 18 e Garage (S3) — servindo produção e uma homologação leve.
  - Domínios, todos subdomínios de `celke.uno` (o domínio principal pertence a outras aplicações): `cursos.` (site), `aluno.`, `adm.` e `media.`, e os equivalentes `homolog-*`.
  - Fluxo do vídeo: upload direto ao Garage → worker com ffmpeg → HLS → playlist assinada pela API.
  - Modelo de dados enxuto: usuário, papel, curso, turma, módulo, aula, matrícula, vídeo, progresso, fórum, certificado, termo.
- **Entrega:** diagrama da arquitetura e lista de funcionalidades.

### Cap. 2 — Repositório, branches e fluxo de trabalho

**Fase:** 0 · **Prompts:** [f00-01](prompts/f00-01-estrutura-inicial.md), [f00-02](prompts/f00-02-orientacoes-para-ia.md)

- **Objetivo:** deixar o repositório pronto para o fluxo de PRs com testes e deploy.
- **Conteúdo:**
  - Repositório `celkecursos/plataforma-cursos` com README e `.gitignore` (segredos, `target/`, `node_modules/`).
  - Branches: `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main`.
  - A `main` recebe código só da `homolog`; alteração reprovada na homologação é desfeita com `git revert`; depois de um hotfix, merge da `main` de volta na `homolog`.
  - Exclusão automática da branch após o merge; environments `homolog` e `production` no GitHub.
  - Como os prompts recriam o projeto passo a passo.
  - Orientações para agentes de IA versionadas em `.claude/`: rules que carregam pelo caminho tocado,
    mensagens de commit em Conventional Commits sem coautoria de IA (`/gc` e `/pc`), `/nova-branch`,
    `/abrir-pr`, `/revisar-dod` e o pipeline SDD (spec → tasks → código) com três portões humanos.
- **Entrega:** `main` e `homolog` no GitHub e a branch `feature/base-projeto` criada.

### Cap. 3 — Esqueleto do backend e padrão de testes

**Fase:** 1.1 · **Prompt:** [f01-01](prompts/f01-01-esqueleto-backend-e-testes.md)

- **Objetivo:** criar o projeto Java e as regras que todas as próximas etapas seguem.
- **Conteúdo:**
  - Spring Boot 4.1 com Java 25, gerado no Spring Initializr, organizado por funcionalidade (`shared/`, `auth/`, `curso/`...; cada módulo com `api/`, `application/`, `domain/`, `infra/`).
  - Dependências mínimas: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc. As demais entram na etapa que as usa.
  - `application.yaml` sem valores padrão para segredos; perfil `worker` sem servidor HTTP.
  - `CLAUDE.md` da raiz e do backend; skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e`, cada uma com o padrão do seu nível.
  - Build: Surefire (`*Test`), Failsafe (`*IT`), JaCoCo com 80% de linhas somando os dois, Spotless com palantir-java-format.
  - Dockerfile multi-stage com targets `api` e `worker` (com ffmpeg), jar em camadas.
- **Segurança:**
  - Tudo exige autenticação; públicos só `GET` de health/probes e da documentação.
  - Sem sessão, CSRF, formulário, HTTP Basic nem usuário embutido do Spring Security.
  - Actuator expõe só `health`, sem detalhes; Swagger só com `DOCS_ENABLED=true`.
  - Imagens rodam com usuário sem root.
- **Testes:** base `support/Containers` (PostgreSQL 18 no Testcontainers) e `support/IntegrationTest` (`MockMvcTester`); `SegurancaPadraoIT` como referência.
- **Entrega:** `./mvnw verify` verde e as imagens `api` e `worker` construídas.

### Cap. 4 — Ambiente local com Docker

**Fase:** 1.2 · **Prompt:** [f01-02](prompts/f01-02-ambiente-local.md)

- **Objetivo:** subir toda a stack local com um comando.
- **Conteúdo:**
  - `infra/compose.yml` com PostgreSQL 18, Garage, API, worker e Mailpit, todos com versão fixa.
  - Garage em modo `--single-node --default-bucket`: na primeira subida ele cria o bucket privado `midia` (prefixos `uploads/` e `videos/`) e a chave da aplicação; `garage.toml` com `replication_factor = 1` e `db_engine = "sqlite"`, sem segredos.
  - `.env` + `.env.example`; `gerar-env.sh` cria o `.env` com segredos aleatórios; variáveis obrigatórias com `${VARIAVEL:?mensagem}`.
  - Healthchecks e `depends_on: condition: service_healthy`; o worker sobe depois da API, que aplica as migrations.
  - Portas do host configuráveis no `.env`, para quando a porta padrão já estiver em uso.
- **Segurança:** portas publicadas só em `127.0.0.1`; nenhum segredo no yaml nem no `garage.toml`; `.env` com permissão 600.
- **Entrega:** `docker compose up --wait` sobe tudo saudável, a API responde em `/actuator/health`, o Swagger abre e o painel do Mailpit fica acessível.

### Cap. 5 — Padrões compartilhados

**Fase:** 1.3 · **Prompt:** [f01-03](prompts/f01-03-padroes-compartilhados.md)

- **Objetivo:** criar a base que todos os módulos usam.
- **Conteúdo:**
  - Erros no formato `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, exceções de negócio, erros do Spring MVC traduzidos, erro inesperado só com o id da requisição; 401/403 da segurança no mesmo formato.
  - Id de correlação (`X-Request-Id`) no log e na resposta; logs em JSON (ECS).
  - DTOs como `record`, paginação padrão (`Pagina<T>`), `Clock` único.
  - `StorageService` com AWS SDK v2 (path-style): URLs pré-assinadas de GET/PUT com o endereço público, upload em partes, consulta, exclusão.
  - Configuração do bucket aplicada pela API ao subir: CORS para os hosts do front (expondo o `ETag`) e expiração de `uploads/` abandonados.
  - Fila no PostgreSQL: tabela `jobs`, `SELECT ... FOR UPDATE SKIP LOCKED`, lease, backoff exponencial, erro permanente, processador por tipo e consumo só no worker.
  - `EmailService` enfileira; o worker monta o HTML com Thymeleaf e envia pelo SMTP (Mailpit no desenvolvimento, Iagente nos servidores).
  - Configuração obrigatória em `@ConfigurationProperties` validadas.
- **Segurança:** nenhuma resposta com stack trace ou mensagem interna; id de correlação recebido só é aceito se for seguro para o log; nome de template restrito a `[a-z0-9-]`; variáveis escapadas no HTML do e-mail; preflight do CORS recusado para origens fora da lista.
- **Testes:** handler de erros e 401/403; `StorageService` contra o Garage real; dois consumidores da fila nunca pegam o mesmo job; backoff e lease vencido; e-mail conferido no Mailpit.
- **Entrega:** `./mvnw verify` verde com Garage e Mailpit no Testcontainers, e a stack local aplicando o CORS no bucket.

---

## Parte 2 — Autenticação e segurança

### Cap. 6 — Usuários e senhas

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** guardar usuários e senhas com segurança.
- **Conteúdo:**
  - Migrations de `usuarios` e `papeis` (ALUNO, ADMIN).
  - Senhas com Argon2; hash `$2y$` do Laravel aceito e regravado em Argon2 no login.
  - Política de senha por tamanho (mínimo 12 para admin, máximo 128).
  - O primeiro admin é criado por comando (`docker compose run --rm api criar-admin <email>`), com a senha digitada na hora — nunca por migration.
  - Seed do profile `test` e `support/UsuariosSeed`.
- **Testes:** hash da senha e criação do admin.
- **Entrega:** admin criado pelo comando e gravado com a senha em hash.

### Cap. 7 — Login com JWT e refresh em cookie

**Fase:** 1.4 · **Prompt:** [f01-04](prompts/f01-04-usuarios-e-login.md)

- **Objetivo:** sessão segura sem guardar token em `localStorage`.
- **Conteúdo:**
  - Access token de 10 minutos no corpo da resposta, guardado pelo frontend só em memória.
  - Refresh token opaco, com o hash salvo no banco e rotação a cada uso. Se um refresh já usado reaparecer, toda a família de tokens é revogada.
  - Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, **sem `Domain`**, para não ser enviado às outras aplicações de `celke.uno`.
  - JWT assinado e validado pelo Resource Server do Spring Security: só HS256, emissor e validade conferidos.
  - Login com a mesma resposta e o mesmo tempo para e-mail inexistente e senha errada.
  - Logout e "sair de todos os dispositivos"; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`.
- **Testes:** token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` ou de outro emissor é recusado; refresh reutilizado revoga a família; atributos do cookie conferidos.
- **Entrega:** login, refresh e logout funcionando pelo Swagger.

### Cap. 8 — Autorização

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** cada rota acessível só por quem deve.
- **Conteúdo:**
  - Papéis vindos do token, com `@PreAuthorize`.
  - `SecurityFilterChain` em modo "nega tudo por padrão", com cada rota pública declarada junto do método HTTP — nenhuma rota fica liberada para qualquer método por engano (como um `/api/usuarios` aberto).
- **Testes:** matriz parametrizada rota × papel (anônimo / ALUNO / ADMIN) com o status esperado; método não declarado numa rota pública exige token.
- **Entrega:** aluno recebe 403 em rota de admin.

### Cap. 9 — Proteções extras

**Fase:** 1.5 · **Prompt:** [f01-05](prompts/f01-05-protecoes.md)

- **Objetivo:** dificultar força bruta, abuso e CSRF.
- **Conteúdo:**
  - Limite por IP com Bucket4j em login e refresh, num filtro que roda antes da segurança e conta até as requisições inválidas; 429 com `Retry-After`.
  - Bloqueio temporário após falhas seguidas: 5 por e-mail + IP (o dono ainda entra de outro lugar) e 20 por e-mail vindas de qualquer IP; verificado antes de calcular o hash.
  - Proteção CSRF no refresh e no logout: `Origin` e `Sec-Fetch-Site` conferidos, porque outras aplicações em `*.celke.uno` são o mesmo site para o navegador e o `SameSite=Strict` não as barra.
  - Auditoria de login: resultado, IP e user-agent de toda tentativa, gravada mesmo quando o login falha.
  - IP real do cliente atrás do proxy: `X-Forwarded-For` aceito só de proxy interno (`forward-headers-strategy: native`).
  - Sem CORS na API: o Caddy serve front e API na mesma origem.
- **Testes:** bloqueio com relógio ajustável (bordas de 15 min e da janela de 1 h); filtro de limite por IP direto, com limite pequeno; 429 e auditoria pela API; refresh e logout de outra origem recusados sem tocar na sessão.
- **Entrega:** tentativas seguidas de login bloqueadas e registradas na auditoria.

### Cap. 10 — Cadastro, confirmação de e-mail e recuperação de senha

**Fase:** 1.6 · **Prompt:** [f01-06](prompts/f01-06-cadastro-e-recuperacao.md)

- **Objetivo:** o aluno cria a conta e recupera a senha sozinho.
- **Conteúdo:**
  - Cadastro com conta pendente e link de confirmação válido por 48 horas; login só depois de confirmar.
  - Recuperação de senha com link de 1 hora; a nova senha encerra todas as sessões e também confirma o e-mail.
  - Tokens de uso único com só o hash no banco; um link novo invalida o anterior.
  - E-mails pela fila, montados com Thymeleaf e enviados pelo worker: Mailpit no desenvolvimento, Iagente nos servidores (remetente de `celke.uno` com SPF e DKIM).
  - Healthcheck do worker por arquivo de batimento, para o compose e o deploy saberem que ele subiu.
- **Segurança:**
  - Cadastro, reenvio e recuperação respondem sempre o mesmo, exista ou não a conta; quem descobre o que aconteceu é o dono da caixa, pelo e-mail que recebe.
  - No máximo 3 e-mails por hora por conta e tipo, contra uso da plataforma para lotar a caixa de alguém.
  - Senha de aluno de 10 a 128 caracteres; limite por IP também nas rotas da conta.
- **Testes:** jornadas completas lendo o link no Mailpit; link de uso único; link antigo invalidado pelo reenvio; e-mail inexistente sem envio; limite de e-mails por hora.
- **Entrega:** cadastro → e-mail → confirmação → login, e recuperação de senha completa, com os e-mails enviados pelo worker.

---

## Parte 3 — Qualidade e entrega contínua

### Cap. 11 — Testes e2e e pipeline no GitHub Actions

**Fase:** 1.7 · **Prompt:** [f01-07](prompts/f01-07-e2e-e-pipeline.md)

- **Objetivo:** cada PR testado com a stack inteira, usando as imagens que vão para produção.
- **Conteúdo:**
  - `infra/compose.prod.yml`: a stack dos servidores, com Caddy na frente e imagens do GHCR pela tag do commit.
  - `infra/caddy/Caddyfile`: hosts pelo ambiente, só `/api` e o health check chegam à API, headers de segurança.
  - `infra/compose.e2e.yml` + `infra/e2e.env`: a mesma stack com HTTP em `*.localhost`, Mailpit e o seed do profile `e2e`, num projeto Compose separado do ambiente local.
  - Perfil Maven `e2e` no backend (`src/e2e/java`, REST Assured, Awaitility); os sites são escolhidos pelo cabeçalho `Host`, sem depender da resolução de `*.localhost`.
  - Jornadas `CadastroE2E` (e-mail entregue pelo worker de verdade), `SessaoE2E` (login → refresh → logout pelo Caddy) e `ProxyE2E` (headers, rotas bloqueadas, `X-Forwarded-For` forjado sem efeito).
  - `pipeline.yml`: testes → build das imagens com cache → e2e → relatórios e `docker compose logs` anexados quando falha; PR e push para `homolog` e `main`.
  - Regras de proteção das branches com os checks obrigatórios.
- **Segurança:** o e2e prova pelo próprio limite por IP que o cliente não consegue forjar o IP atrás do Caddy; Swagger e endpoints internos do Actuator não saem do proxy.
- **Testes:** as próprias jornadas e2e.
- **Entrega:** PR com o pipeline verde e as regras de proteção ativas.

### Cap. 12 — Preparar a VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** servidor seguro antes de receber a aplicação.
- **Conteúdo:**
  - `preparar-vps.sh` numa VPS Ubuntu 24.04 nova: Docker oficial com rotação de logs, administrador com sudo e usuário `deploy` sem sudo, SSH só por chave e sem root, fail2ban, atualizações automáticas, swap de 2 GB.
  - Firewall do painel da Hostinger + `ufw`: só 22, 80 e 443.
  - Cuidado: o Docker contorna o `ufw` nas portas que publica. Por isso só o Caddy publica portas; PostgreSQL, Garage e API ficam só na rede interna.
  - A configuração do SSH vale antes da que vem na imagem (`50-cloud-init.conf`), e o script só termina depois de você confirmar o login do administrador num segundo terminal.
  - DNS dos seis subdomínios (produção e homologação) apontando para a VPS.
- **Entrega:** acesso SSH só por chave e portas fechadas conferidas de fora.

### Cap. 13 — Produção e homologação com Docker na VPS

**Fase:** 1.8 · **Prompt:** [f01-08](prompts/f01-08-vps.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** os dois ambientes rodando na mesma VPS sem um derrubar o outro.
- **Conteúdo:**
  - `compose.prod.yml` por ambiente (`AMBIENTE`), com rede e apelidos próprios (`producao-api`, `homolog-api`); `compose.homolog.yml` por cima, com limites menores e o Swagger.
  - `/opt/plataforma-cursos/{caddy,producao,homolog}`, cada ambiente com `.env` próprio (permissão 600) gerado com segredos aleatórios e diferentes.
  - Um Caddy só nas portas 80/443, ligado às redes dos dois ambientes; `Caddyfile` com trechos comuns e um arquivo de sites por ambiente — o e2e usa o mesmo `Caddyfile`.
  - Homologação restrita por IP e com `noindex` (senha do Caddy não serve: ocuparia o cabeçalho `Authorization` do JWT); `adm.` de produção restrito por IP.
  - Headers no Caddy: HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`; `lb_try_duration` para a API reiniciar sem erro 502.
  - Limites de memória e CPU por container (produção: API 1 GB, worker 2 GB e 1,5 CPU, PostgreSQL 1 GB, Garage 768 MB; homologação menor, com o worker em meia CPU) e `-Xmx` de acordo.
- **Entrega:** `homolog-aluno.celke.uno/api` e `aluno.celke.uno/api` respondendo com HTTPS.

### Cap. 14 — Deploy automático com rollback

**Fase:** 1.9 · **Prompt:** [f01-09](prompts/f01-09-deploy-automatico.md) · **Roteiro:** [vps.md](vps.md)

- **Objetivo:** merge na branch = versão no ar, com volta automática se falhar.
- **Conteúdo:**
  - Imagens com tag = SHA do commit, nunca `latest`, publicadas no GHCR pelo próprio job de e2e: vai para o servidor exatamente a imagem que passou nas jornadas.
  - `deploy.sh` na VPS: valida os argumentos, recebe só os arquivos de compose esperados, faz backup do banco, troca a tag, espera todos os containers ficarem saudáveis e, se algum não ficar, volta para a versão anterior (arquivos e tag).
  - Um deploy por vez (`flock`), deploy que continua mesmo se a conexão SSH cair, últimas versões e backups guardados.
  - Chave SSH presa ao script no `authorized_keys` (`command=...,no-pty,no-port-forwarding`) e impressão digital da VPS fixada no Actions.
  - Push na `homolog` → homologação; push na `main` → produção, com aprovação opcional; só PRs cancelam execuções antigas do pipeline.
  - Migrations compatíveis com a versão anterior, porque o rollback não desfaz migration.
- **Segurança:** a chave do deploy não abre shell, terminal nem túnel; o script recusa ambiente ou SHA fora do formato (inclusive tentativa de injetar comandos pelo SSH) e só extrai os arquivos esperados do pacote.
- **Testes:** simulação local de uma VPS: versão boa no ar, versão quebrada com rollback automático, backup restaurável, argumentos inválidos e deploy simultâneo recusados; chave presa ao script conferida com um `sshd` real.
- **Entrega:** deploy nos dois ambientes pelo pipeline e um rollback testado de propósito.

---

## Parte 4 — Domínio de cursos

### Cap. 15 — Curso, turma, módulo e aula

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** modelar o conteúdo dos cursos.
- **Conteúdo:**
  - Curso → turmas → módulos → aulas. A turma é a oferta do curso: tem os próprios módulos e aulas e, no próximo capítulo, as matrículas.
  - Status como enum e `CHECK` no banco (nada de `[3,4,13,14]`): curso em rascunho, publicado ou arquivado; turma em preparação, ativa ou encerrada; módulo e aula ativos ou inativos.
  - Slug para o site, gerado do título quando não informado (sem acento, minúsculas e hífens) e único no banco.
  - Ordem de módulos e aulas sempre 1, 2, 3...: item novo entra no fim, a exclusão refaz a sequência e a reordenação recebe a lista completa.
  - Restrição única adiável (`DEFERRABLE INITIALLY DEFERRED`): o PostgreSQL só confere a ordem no commit, e a troca de posições acontece numa transação.
  - Trava do pai (`SELECT ... FOR UPDATE`) para duas criações simultâneas não pegarem a mesma posição.
  - Exclusão só de item vazio (`ON DELETE RESTRICT`); o que tem conteúdo é arquivado pelo status.
- **Testes:** slug e ordenação em tabela (lista incompleta, repetida, de outro pai, vazia); reordenação real no PostgreSQL; oito módulos criados ao mesmo tempo recebem 1 a 8.
- **Entrega:** tabelas criadas pelo Flyway e regras de ordem garantidas pelo banco.

### Cap. 16 — CRUD administrativo e catálogo público

**Fase:** 2 · **Prompt:** [f02-01](prompts/f02-01-cursos-e-catalogo.md)

- **Objetivo:** o admin gerencia os cursos e o site lista os publicados.
- **Conteúdo:**
  - Endpoints `/api/adm/**` com validação, `Location` na criação e `PATCH .../status`; reordenação de módulos e aulas.
  - Capa do curso em duas etapas: o navegador envia a imagem direto ao Garage por URL pré-assinada e a API confirma, reduz para até 1280 × 720 e regrava em JPEG.
  - Catálogo público: cursos publicados e curso por slug, com o conteúdo ativo da turma ativa mais recente.
  - Capa no catálogo por um endereço estável da API, que redireciona para uma URL assinada nova: a URL assinada vence em uma hora e quebraria o HTML pré-renderizado do site.
- **Segurança:**
  - `/api/adm/**` exige ADMIN no `SecurityConfig` e de novo com `@PreAuthorize`: o aluno recebe 403 antes de o corpo ser lido, e um controller novo sem a anotação continua protegido.
  - A imagem é conferida pelo conteúdo, não pela extensão nem pelo `Content-Type`: HTML enviado como `image/png` é recusado.
  - A re-codificação descarta o EXIF (GPS e dados da câmera) e qualquer conteúdo escondido no arquivo.
  - Dimensões lidas do cabeçalho antes de decodificar (proteção contra "bomba de descompressão"); 5 MB conferidos de novo no arquivo enviado, porque a URL pré-assinada não limita o tamanho.
  - A confirmação só aceita a chave gerada para o mesmo curso; o arquivo enviado é apagado em qualquer resultado.
  - Catálogo sem dados internos (ids de curso, status, chaves do bucket); rascunho e arquivado respondem 404.
- **Testes:** matriz de papéis de todas as rotas com ids reais; CRUD com `ProblemDetail`; capa com o Garage real (redução, EXIF removido, troca, remoção, envios recusados); catálogo com turmas, módulos e aulas inativos; e2e do admin criando o curso com capa e o visitante vendo no catálogo.
- **Entrega:** curso completo cadastrado pelo Swagger, com capa, visível no catálogo.

### Cap. 17 — Matrícula

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** ligar alunos às turmas.
- **Conteúdo:**
  - `matriculas` com início, fim opcional (acesso sem prazo), status (enum), forma de pagamento e transação.
  - Status gravado só `ATIVA` ou `CANCELADA`; vigente, vencida ou aguardando início são calculados com o `Clock`, sem job que marque matrículas.
  - Índices únicos parciais: uma matrícula ativa por aluno e turma, e cada transação gera uma matrícula só (base para o webhook de pagamento).
  - Matrícula manual pelo admin: matricular por e-mail, alterar período, cancelar (o histórico fica).
  - Endpoints do aluno: meus cursos (ativos, arquivados, expirados), arquivar e desarquivar.
  - Fronteira entre módulos: a matrícula guarda ids e consulta o conteúdo por um serviço exportado pelo módulo de cursos.
- **Segurança:** tudo da área do aluno usa o id do token, nunca um id enviado pelo cliente; matrícula de outro aluno responde 404.
- **Testes:** situação da matrícula em tabela; matrícula repetida, turma encerrada, transação repetida; abas de meus cursos; turma com matrículas não pode ser excluída.
- **Entrega:** aluno matriculado vê seus cursos.

### Cap. 18 — Política única de acesso

**Fase:** 3 · **Prompt:** [f03-01](prompts/f03-01-matricula-e-acesso.md)

- **Objetivo:** um ponto só decide o que o aluno pode ver.
- **Conteúdo:**
  - `AcessoAulaPolicy`: decide o acesso a aula, vídeo, arquivo, fórum, progresso e certificado; `AcessoAulaService.exigirAcesso` é a porta de entrada dos outros módulos.
  - Regras: matrícula ativa e dentro do período (fim inclusivo), ou aula gratuita de turma ativa e curso publicado.
  - A turma inteira (lista de módulos e aulas) só com matrícula vigente: a aula gratuita não abre a turma.
  - Data e hora por `Clock` injetado.
- **Segurança:**
  - Recurso de outro aluno responde 404, para não revelar que existe.
  - Aula gratuita de curso que voltou para rascunho não vaza; aula ou módulo inativo responde 404.
- **Testes:** unitários com todas as combinações de status × datas (instante antes, exato e depois de cada limite); matriz de acesso ao conteúdo; IDOR (aluno A tentando ver dados do aluno B); e2e com a matrícula vencendo.
- **Entrega:** aluno sem matrícula ou com matrícula vencida recebe 403.

---

## Parte 5 — Vídeo

### Cap. 19 — Upload direto para o Garage

**Fase:** 4

- **Objetivo:** enviar vídeos grandes sem passar pela API.
- **Conteúdo:**
  - O ADM pede URLs pré-assinadas de upload multipart, e o navegador envia direto para `media.celke.uno` (prefixo `uploads/` do bucket `midia`).
  - A API valida tipo e tamanho antes de assinar.
- **Segurança:** depois do upload, a API confere o tamanho e o tipo real do arquivo antes de enfileirar.
- **Testes:** upload multipart contra o Garage do Testcontainers; arquivo com extensão falsa rejeitado.
- **Entrega:** vídeo em `uploads/` e job criado na fila.

### Cap. 20 — Transcodificação com ffmpeg

**Fase:** 4

- **Objetivo:** reduzir o tamanho do vídeo e prepará-lo para streaming.
- **Conteúdo:**
  - Worker consome a fila: `ffprobe` para analisar, ffmpeg para gerar HLS em 720p + 1080p (sem aumentar a resolução de origem), H.264 CRF 23–26, `maxrate`, AAC 128k, segmentos de 6 s.
  - Thumbnail e duração; resultado em `videos/`; o original é apagado depois que a transcodificação dá certo.
  - Status `pendente → processando → pronto/erro`, reprocessar pelo ADM.
  - Job volta para a fila se o worker parar no meio (`stop_grace_period` + lease com expiração).
  - Comparativo de tamanho antes e depois.
- **Segurança:** ffmpeg chamado via `ProcessBuilder` com lista de argumentos, nunca concatenando uma string de shell.
- **Testes:** transcodificar um vídeo de 3 s; e2e de upload até o status "pronto".
- **Entrega:** vídeo convertido em HLS com o tamanho reduzido registrado.

### Cap. 21 — Limites da VPS

**Fase:** 4

- **Objetivo:** a transcodificação não pode derrubar a API.
- **Conteúdo:**
  - `cpus` e limite de memória no container do worker; um job por vez; prioridade baixa no processo.
  - Medição do tempo de processamento por minuto de vídeo na VPS.
  - Conta de quantas horas de vídeo cabem no disco.
- **Entrega:** tabela com o tempo medido e a API respondendo normalmente durante a transcodificação.

### Cap. 22 — Playlist assinada pela API

**Fase:** 5

- **Objetivo:** só quem tem acesso assiste, e o bucket continua privado.
- **Conteúdo:**
  - `GET /api/aulas/{id}/video.m3u8` verifica a `AcessoAulaPolicy` e reescreve a playlist com URLs pré-assinadas de cada segmento, válidas pela duração do vídeo + margem.
  - Substitui os cookies assinados de CDN, que não existem nesta hospedagem.
- **Testes:** sem matrícula, 403; com matrícula, segmentos assinados que baixam e depois expiram.
- **Entrega:** vídeo tocando por uma playlist assinada.

### Cap. 23 — Criptografia AES-128 do HLS (nível extra)

**Fase:** 5 (opcional)

- **Objetivo:** um segmento copiado não serve para nada sem a chave.
- **Conteúdo:** segmentos cifrados; a chave só é entregue por um endpoint autenticado que passa pela `AcessoAulaPolicy`.
- **Testes:** a chave sem token ou sem matrícula é recusada.
- **Entrega:** segmento baixado direto do Garage não toca sem a chave.

### Cap. 24 — Progresso e log de acesso

**Fase:** 5

- **Objetivo:** o aluno retoma de onde parou e acompanha o avanço.
- **Conteúdo:**
  - Posição do vídeo, aula vista / revisar, percentual da turma.
  - Log de acesso com retenção definida (LGPD).
- **Testes:** progresso de outro aluno responde 404; percentual calculado com as bordas (nenhuma aula, todas as aulas).
- **Entrega:** progresso salvo e refletido na turma.

---

## Parte 6 — Restante do domínio

### Cap. 25 — Arquivos de apoio

**Fase:** 6

- **Objetivo:** entregar código-fonte e materiais com o mesmo controle do vídeo.
- **Conteúdo:** arquivos por aula e por módulo, enviados pelo ADM e baixados por URL pré-assinada curta, sempre pela `AcessoAulaPolicy`.
- **Testes:** download permitido e bloqueado.
- **Entrega:** aluno matriculado baixa o arquivo; sem matrícula, 403.

### Cap. 26 — Fórum por aula e por turma

**Fase:** 7

- **Objetivo:** dúvidas respondidas dentro da plataforma.
- **Conteúdo:** perguntas e respostas com anexo validado; moderação pelo admin (ocultar, fixar, responder como instrutor); rate limit na criação.
- **Segurança:** conteúdo em Markdown convertido para HTML filtrado, contra XSS.
- **Testes:** XSS, IDOR em anexo, aluno sem matrícula; e2e de pergunta e resposta.
- **Entrega:** aluno pergunta, admin responde, aluno vê a resposta.

### Cap. 27 — Certificado em PDF

**Fase:** 8

- **Objetivo:** certificado gerado e verificável.
- **Conteúdo:**
  - Regra de conclusão (percentual de aulas vistas) configurável por turma; nome confirmado pelo aluno.
  - PDF com openhtmltopdf e código de validação público, usado na página "validar certificado" do site.
- **Testes:** regra de conclusão com as bordas; e2e de conclusão → certificado → validação.
- **Entrega:** certificado baixado e validado pelo código.

### Cap. 28 — Perfil e termos

**Fase:** 9

- **Objetivo:** o aluno cuida dos próprios dados e aceita os termos vigentes.
- **Conteúdo:**
  - Dados, troca de senha (exige a senha atual) e foto de perfil redimensionada, sem metadados EXIF.
  - Termos versionados: aceite obrigatório no primeiro acesso e a cada nova versão.
- **Testes:** troca de senha com usuário próprio do teste; e2e de novo termo bloqueando as rotas até o aceite.
- **Entrega:** perfil editável e aceite de termos funcionando.

---

## Parte 7 — Operação

Fica antes do frontend porque precisa estar pronta antes de entrarem dados reais em produção.

### Cap. 29 — Backup e restauração

**Fase:** 10

- **Objetivo:** recuperar tudo se a VPS for perdida.
- **Conteúdo:**
  - `pg_dump` diário.
  - Backup do Garage com restic **para fora da VPS**: o snapshot da Hostinger sozinho não basta, porque o vídeo e o banco estão no mesmo disco.
  - Teste de restauração documentado.
- **Entrega:** restauração completa executada a partir do backup.

### Cap. 30 — Monitoramento e disco

**Fase:** 10

- **Objetivo:** saber do problema antes do aluno.
- **Conteúdo:**
  - Uptime externo e alertas; alerta de disco e de falhas no worker.
  - Rotação de logs do Docker; limpeza de uploads órfãos e dos dados da homologação.
  - Plano de saída dos vídeos para um S3 externo (R2/B2) quando o disco apertar: só muda o endpoint no `.env`.
- **Entrega:** alertas chegando num teste provocado.

### Cap. 31 — Checklist final de segurança

**Fase:** 10

- **Objetivo:** revisar a API inteira antes de abrir para alunos.
- **Conteúdo:**
  - Revisão das categorias do OWASP ASVS nível 1.
  - Varredura com OWASP ZAP contra a homologação.
  - Trivy no pipeline e atualização de dependências.
- **Entrega:** relatório sem achados de severidade alta.

---

## Parte 8 — Frontend com `@celke/kole-react`

### Cap. 32 — A biblioteca `@celke/kole-react`

**Pré-requisito do frontend** (repositório da biblioteca)

- **Objetivo:** conhecer a biblioteca de layout usada pelos fronts e como instalá-la.
- **Conteúdo:** moldura (`Estrutura`, `TelaLogin`), formulários, tabelas, diálogos, avisos e o visualizador de arquivos; instalação do GitHub (`github:celkecursos/kole-react#<commit>`). Os componentes próprios da área do aluno (player de vídeo, lista de aulas, fórum, campo de imagens) ficam no app.
- **Testes:** componentes testados com Vitest no repositório da biblioteca.
- **Entrega:** commit da biblioteca fixado no `web/package.json`.

### Cap. 33 — Workspace web e a biblioteca

**Fase:** 11

- **Objetivo:** base dos três frontends servida pelo Caddy.
- **Conteúdo:**
  - Pacote único em `web/` com `aluno` e `adm` (Vite + React + TypeScript); o `site` entra na Fase 14.
  - `@celke/kole-react` instalada do GitHub com commit fixo.
  - Client HTTP com token em memória, refresh silencioso e fila de requisições durante o refresh; proteção de rotas por papel; tratamento de termo pendente.
  - Telas de login, cadastro, confirmação de e-mail e recuperação de senha.
  - Imagem `web`: build dos fronts + Caddy servindo `/srv/site`, `/srv/aluno` e `/srv/adm`; proxy do Vite `/api` no desenvolvimento.
- **Segurança:** CSP (`connect-src 'self' https://media.celke.uno`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html`.
- **Testes:** Vitest + Testing Library; Playwright no pipeline (login do aluno e do admin, aluno barrado no ADM, logout).
- **Entrega:** login funcionando em `aluno.celke.uno` e `adm.celke.uno`.

### Cap. 34 — ADM

**Fase:** 12

- **Objetivo:** o admin opera a plataforma pelo navegador.
- **Conteúdo:** cursos, turmas, módulos e aulas (reordenação pelos botões subir e descer); upload de vídeo multipart com progresso e status de processamento; arquivos de apoio, matrículas, moderação do fórum e termos.
- **Testes:** Playwright do admin criando um curso completo e enviando um vídeo curto até "pronto".
- **Entrega:** curso publicado inteiramente pelo ADM.

### Cap. 35 — Área do aluno

**Fase:** 13

- **Objetivo:** a experiência completa do aluno.
- **Conteúdo:** dashboard de cursos; página da turma com progresso; aula com player, lista lateral, anterior/próxima, vista/revisar, arquivos e fórum (com imagens nas perguntas e respostas, Fase 18); certificados e perfil.
- **Testes:** Testing Library e Playwright (login → turma → aula → vídeo toca → marca como vista → progresso atualizado).
- **Entrega:** aluno assiste a uma aula do início ao fim.

### Cap. 36 — Site moderno

**Fase:** 14

- **Objetivo:** vitrine pública rápida e bem indexada.
- **Conteúdo:**
  - Pré-renderização estática (cada página vira um HTML) em `cursos.celke.uno`, gerada pelo container `site` a partir da API do próprio ambiente ao subir e a cada 5 minutos.
  - Home, catálogo, página do curso, validar certificado e páginas legais.
  - Layout novo com os mesmos tokens visuais da `@celke/kole-react`.
  - SEO: meta tags, Open Graph, sitemap, `robots.txt`.
- **Testes:** meta de Lighthouse verificada no pipeline.
- **Entrega:** site publicado com a nota de Lighthouse atingida.

---

## Parte 9 — Migração do legado (opcional)

### Cap. 37 — Importar dados do MySQL do CLMS

**Fase:** 15

- **Objetivo:** trazer os alunos e cursos atuais para a nova plataforma.
- **Conteúdo:**
  - ETL de usuários, cursos, turmas, módulos, aulas, matrículas e progresso.
  - Status numéricos do legado (`3, 4, 13, 14, 15`...) mapeados para os enums novos.
  - Senhas bcrypt do Laravel aproveitadas: o hash `$2y$` é aceito no primeiro login e regravado em Argon2.
  - Reprocessamento dos vídeos já existentes no S3 para HLS.
- **Entrega:** alunos do legado entram com a senha atual e encontram seus cursos.

---

## Parte 10 — Vendas e pagamentos

### Cap. 38 — Meios de pagamento e o webhook da Hotmart

**Fase:** 16

- **Objetivo:** receber as notificações da Hotmart com segurança, numa estrutura que aceita outros meios de pagamento sem mexer no núcleo.
- **Conteúdo:**
  - Webhook 1.0.0 (formato antigo, não recomendado) × 2.0.0: corpo JSON com `id` por evento, `X-HOTMART-HOTTOK` no cabeçalho, `xcod` em `data.purchase.origin`.
  - A porta `GatewayPagamento` (um bean por meio): campos de configuração, capacidades, teste das credenciais, checkout, autenticação, interpretação e consulta; `GatewaysRegistrados` por tipo.
  - Meio de pagamento configurado pelo painel: campos comuns em JSON, segredos cifrados com AES-256-GCM (`PAGAMENTOS_CHAVE`, IV por gravação, webhook token como dado associado), um ativo por vez com índice único parcial.
  - Migration `V15`: meios de pagamento, ofertas, pedidos, histórico e eventos recebidos; usuário sem senha; matrícula ligada ao pedido.
  - `POST /api/pagamentos/webhooks/{token}`: limite de tamanho e por IP, hottok comparado em tempo constante, `INSERT ... ON CONFLICT DO NOTHING` como idempotência, corpo mascarado (documento, telefone, IP), job na fila e 200 na hora.
  - Por que só responder erro quando nada foi gravado: a Hotmart reenvia e desativa a URL que continua falhando.
  - Homologação restrita por IP com exceção só para o caminho do webhook.
- **Segurança:** segredos nunca voltam na API nem ficam em texto puro no banco; 401 sem gravar nada; 413 para corpo grande.
- **Testes:** interpretação de cada fixture (campos opcionais do afiliado, status desconhecido, data em número e em texto), hottok, cifra (ida e volta, IV diferente, chave errada), integração do webhook (duplicado, 401, 404, corpo inválido, 413) e matriz de papéis.
- **Entrega:** "Enviar teste" da Hotmart aparece em "Eventos recebidos" na homologação.

### Cap. 39 — Pedido, status e matrícula

**Fase:** 16

- **Objetivo:** transformar cada notificação na matrícula certa, mesmo com eventos repetidos e fora de ordem.
- **Conteúdo:**
  - Status interno do pedido, igual para todos os meios, e a tabela de transições num lugar só (`TransicoesPedido`): reembolso e chargeback sempre valem; aprovação depois do reembolso não reabre.
  - Worker com uma transação por evento e trava no pedido (`SELECT ... FOR UPDATE`); pedido achado pelo `xcod`, pela transação ou criado como `EXTERNO` (afiliado, link direto).
  - Matrícula pelo pagamento: criar, estender quem já está matriculado, reativar depois de disputa, cancelar no reembolso.
  - Conta do comprador sem senha e o link de criar senha no e-mail da matrícula (nunca senha por e-mail).
  - Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que consulta o pedido.
  - Avisos ao admin (chargeback, disputa, evento com erro), reprocessamento pelo painel, retenção do corpo dos eventos e a reconciliação pela API de vendas (ligada por configuração).
- **Testes:** todas as transições; integração do fluxo completo (aprovado → acesso e e-mail → reembolso tira o acesso), venda sem `xcod` criando a conta, boleto aguardando, evento antigo ignorado, dois eventos do mesmo pedido ao mesmo tempo; jornada e2e pelo Caddy até o e-mail no Mailpit.
- **Entrega:** compra aprovada libera o curso e o reembolso tira, sem matrícula nem e-mail duplicado.

### Cap. 40 — Comprar pelo site, painel de vendas e testes de ponta a ponta

**Fase:** 16

- **Objetivo:** o aluno compra pelo site e o admin acompanha as vendas pelo ADM.
- **Conteúdo:**
  - ADM: meios de pagamento com o formulário montado a partir dos campos do gateway, "Testar credenciais", ativação e a URL do webhook pronta para copiar; aba "Venda" da turma com as ofertas; pedidos com a linha do tempo; eventos recebidos com o corpo e "Reprocessar".
  - Aluno: `/comprar/:oferta` (volta para a compra depois de entrar ou criar a conta) e `/pagamento/retorno`, que acompanha o pedido a cada 3 s; "Crie sua senha" para quem veio da compra.
  - Site: preço e botão "Comprar" de cada oferta na página do curso, com as ofertas nos dados estruturados.
  - Como adicionar outro meio de pagamento (Mercado Pago, Stripe...) só com um valor no enum e um bean novo.
- **Testes:** Vitest da página de retorno e do formulário; Playwright do admin cadastrando a Hotmart (validação e credencial errada), da compra interceptando o `pay.hotmart.com`, do boleto, do reembolso e do evento ignorado reprocessado.
- **Entrega:** compra de ponta a ponta na homologação com a Hotmart de teste.

---

## Parte 11 — Auditoria e logs

### Cap. 41 — Trilha de auditoria e logs persistentes

**Fase:** 17

- **Objetivo:** saber quem fez o quê nos dados, de onde e quando, e guardar o log técnico por meses.
- **Conteúdo:**
  - Por que não Hibernate Envers, JaVers ou Spring Data Auditing: uma tabela por entidade, outra camada de modelo ou só "quem alterou por último"; aqui, uma tabela única com ação, origem e IP.
  - Ouvinte dos eventos do Hibernate (`PostInsert`, `PostUpdate`, `PostDelete`) que grava na mesma transação, com só os campos que mudaram na alteração.
  - Contexto da ação: usuário do token, rota com as variáveis, IP, navegador, id da requisição; job da fila pelo MDC; "sistema" nos agendamentos.
  - `@DadoSensivel` (hash de senha, segredos, tokens como `***`) e `@SemAuditoria` (progresso, logs, tokens de sessão).
  - Só inclusão garantida pelo banco (gatilho que recusa `UPDATE` e `DELETE`) e a retenção como única exceção.
  - Tela de consulta no ADM com filtros e o antes/depois lado a lado.
  - Logs da API e do worker em arquivo num volume, com rotação diária no horário de Brasília, compressão e limite por idade e por tamanho; como ler na VPS.
- **Segurança:** a trilha não guarda segredos; o ADM só lê; nem a própria aplicação consegue alterar ou apagar fora da retenção.
- **Testes:** unitários da conversão de valores e do contexto; integração com ADM, aluno, worker, segredo oculto, operação recusada sem rastro, gatilho e retenção; Playwright da tela.
- **Entrega:** a alteração de um curso aparece na auditoria com o antes e o depois; os logs ficam no volume depois de um deploy.

---

## Parte 12 — Segurança reforçada

### Cap. 42 — Segundo fator dos administradores e senhas fortes

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma senha vazada não basta para entrar no ADM, e as senhas fáceis de adivinhar ficam de fora.
- **Conteúdo:**
  - TOTP (RFC 6238) sem biblioteca: HMAC-SHA1, passos de 30 s, 6 dígitos, tolerância de um passo, Base32 e o endereço `otpauth://` do QR code; testes com os vetores da RFC.
  - Login em duas etapas: a senha certa devolve um desafio de 5 minutos (sem sessão); o código abre a sessão; o código errado conta no bloqueio; o mesmo código não vale duas vezes.
  - Primeiro acesso com o QR code e os 10 códigos de recuperação de uso único (só o hash no banco); segredo cifrado com AES-256-GCM e dado associado (`TOTP_CHAVE`); comando `redefinir-2fa`.
  - Política de senha: a lista de senhas comuns e vazadas (gerada por comando a partir de uma lista pública), nome e e-mail dentro da senha, sequências de teclado e de alfabeto, repetições; a mesma regra no cadastro, na redefinição, no perfil e no `criar-admin`; o medidor de força da biblioteca nas telas.
  - Front: a tela da segunda etapa compartilhada entre o ADM e a área do aluno, com o QR code gerado no navegador.
- **Segurança:** a sessão do administrador nunca abre só com a senha; o segredo do aplicativo não sai do banco em texto.
- **Testes:** unitários do TOTP (RFC), da política e do login em duas etapas; integração do cadastro, do código errado, do código de recuperação de uso único e do segredo cifrado; Playwright do admin entrando com o código.
- **Entrega:** o admin cadastra o aplicativo no primeiro acesso e passa a entrar com o código.

### Cap. 43 — Menos privilégio: banco, limites, contas e dependências

**Fase:** 19 · **Prompt:** [f19-01](prompts/f19-01-seguranca.md)

- **Objetivo:** uma falha na aplicação causa o menor estrago possível.
- **Conteúdo:**
  - Usuário do banco só de dados para a API e o worker; o serviço `migracao` com o dono do schema, o `afterMigrate.sql` que cria e libera o usuário da aplicação, e o deploy que completa o `.env` dos servidores.
  - Limite por usuário nas gravações e no checkout (Bucket4j, depois da autenticação).
  - ADM → Usuários: bloquear, desbloquear e encerrar as sessões, com o access token já emitido recusado na hora (validador do JWT com a situação da conta em memória por 30 s).
  - Compra com confirmação (nenhum pedido ao abrir a página) e checkout só em HTTPS nos domínios do meio de pagamento.
  - Token dos links de e-mail fora da barra de endereço.
  - Dependabot e `npm audit` no pipeline.
  - O teste que varre todas as rotas da API e falha se uma rota nova nascer aberta.
- **Testes:** integração do usuário do banco (dados sim, estrutura não), da tela de usuários e da varredura das rotas; unitário do limite por usuário; Playwright do bloqueio e da compra confirmada.
- **Entrega:** a aplicação não consegue apagar tabelas; um aluno bloqueado sai na hora.

---

## Parte 13 — Privacidade

### Cap. 44 — LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 20 · **Prompt:** [f20-01](prompts/f20-01-lgpd.md)

- **Objetivo:** o aluno exerce os direitos do titular sozinho, pelo perfil, e a política de privacidade diz exatamente o que o sistema faz.
- **Conteúdo:**
  - O que é dado pessoal na plataforma, por módulo, e por que cada módulo responde pelas próprias tabelas (`DadosPessoais`).
  - Baixar meus dados: um JSON com a conta e uma seção por módulo, datas em ISO-8601.
  - Excluir a conta: o que é apagado (progresso, certificados, foto, sessões, segundo fator), o que é anonimizado (conta, pedidos) e o que fica pelo prazo legal (matrículas e pedidos, aceites, registros de acesso, auditoria, backups); a anonimização em vez de apagar a linha do usuário.
  - Retenção: a auditoria de login passa a ser limpa depois de 180 dias, como o log de acesso aos vídeos.
  - O modelo da política de privacidade: controlador e encarregado, dados e finalidades, bases legais, compartilhamento, prazos, direitos, segurança, cookies.
- **Segurança:** a exclusão exige a senha e a palavra de confirmação; o access token cai na hora; administrador não se exclui por aqui.
- **Testes:** integração da exportação e da exclusão (dados anonimizados, sessões encerradas, auditoria mantida, e-mail enviado) e da retenção; Playwright de baixar os dados e excluir a conta.
- **Entrega:** um aluno baixa os dados e exclui a conta pelo perfil; a política publicada bate com o sistema.
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

`docs/plano-implementacao.md`:

`````markdown
# Plano de implementação — Plataforma de Cursos

Sistema do aluno (cursos, aulas em vídeo, fórum, certificado) com backend Java, frontend React
usando `@celke/kole-react`, tudo em Docker numa VPS da Hostinger. Este plano também é o roteiro do tutorial:
cada fase vira uma branch e um ou mais capítulos.

**Prompts:** cada etapa implementada tem um prompt em [`docs/prompts/`](prompts/README.md). Executados
na ordem do índice, eles recriam o projeto do zero.

**Ordem de trabalho:** primeiro todo o backend (com infraestrutura, CI e deploy), depois o frontend,
com a `@celke/kole-react` instalada do GitHub (commit fixo no `package.json`).

---

## 1. Decisões

| Tema | Decisão |
|---|---|
| Java | **Java 25 LTS** |
| Backend | **Spring Boot 4.1.x** (última correção disponível ao criar o projeto); organizado por funcionalidade; profiles `api` e `worker` no mesmo código. Atualizar para 4.2 numa branch `fix/` quando sair |
| Banco | **PostgreSQL 18** + Flyway |
| Fila | Tabela no PostgreSQL com `SELECT ... FOR UPDATE SKIP LOCKED` (sem Redis/RabbitMQ) |
| S3 na VPS | **Garage** (`dxflrs/garage:v2.4.1`), modo `--single-node`. Escolhido por ser leve, ter configuração curta e suportar tudo que o projeto usa (veja abaixo). O MinIO comunitário parou de publicar imagens Docker e entrou em modo de manutenção |
| Acesso ao S3 | Somente AWS SDK v2 com path-style; trocar de servidor = trocar endpoint no `.env` |
| Vídeo | Upload direto por URL pré-assinada → ffmpeg → HLS 720p + 1080p → playlist reescrita pela API com segmentos assinados |
| E-mail | **SMTP da Iagente** |
| Frontend | Pacote único em `web/` com `aluno`, `adm` e o gerador do `site`; `@celke/kole-react` instalada **do GitHub** com commit fixo |
| Hospedagem | Tudo em Docker na VPS (2 vCPU, 8 GB, 100 GB NVMe); Caddy com HTTPS automático serve os fronts e faz proxy de `/api` |
| Ambientes | Produção + homologação leve na mesma VPS |
| Branches | `main` (produção), `homolog` (homologação), `feature/nome` e `fix/nome` temporárias, `hotfix/nome` a partir da `main` |
| CI | GitHub Actions: testes + e2e em todo PR; build e e2e refeitos na `main` |
| Deploy | SSH com chave presa a `deploy.sh`, imagem com tag = SHA, health check e rollback |

### Garage: o que o projeto usa e o que ele não tem

- ✅ URLs pré-assinadas, upload multipart, CORS por bucket, lifecycle com `Expiration` e `AbortIncompleteMultipartUpload` (limpa uploads abandonados)
- ❌ Versionamento, ACLs e bucket policies. Não fazem falta: o bucket é privado e o acesso é por chave com permissão por bucket.
- Um bucket privado `midia`, com os prefixos `uploads/` (originais enviados) e `videos/` (HLS e thumbnails). O próprio Garage cria o bucket e a chave da aplicação na primeira subida (`--default-bucket`); a expiração de uploads abandonados é feita por prefixo.

### Domínios (somente subdomínios de `celke.uno`)

O domínio principal `celke.uno` já é usado por outras aplicações e não será tocado.

| Produção | Homologação | Serve |
|---|---|---|
| `cursos.celke.uno` | `homolog-cursos.celke.uno` | site |
| `aluno.celke.uno` | `homolog-aluno.celke.uno` | SPA do aluno + `/api` |
| `adm.celke.uno` | `homolog-adm.celke.uno` | SPA do ADM + `/api` (restrito por IP) |
| `media.celke.uno` | `homolog-media.celke.uno` | Garage (subdomínio próprio: a assinatura depende do host) |

> Nomes propostos — confirmar que nenhum desses subdomínios já está em uso antes da etapa 1.8 (VPS).

Cuidados por dividir o domínio com outras aplicações:

- Cookies **sem** atributo `Domain` (cookie só do host). Com `Domain=celke.uno`, todas as outras aplicações dos subdomínios receberiam o refresh token.
- A CSP e o CORS do Garage listam os subdomínios exatos, nunca `*.celke.uno`.
- HSTS sem `includeSubDomains`, a não ser que todas as aplicações de `celke.uno` já usem HTTPS.

### SMTP Iagente

- Host, porta e credenciais vêm do painel da Iagente (porta 587 com STARTTLS é o padrão esperado; confirmar no painel).
- Remetente: endereço de `celke.uno` com SPF e DKIM configurados na Iagente.
- Credenciais só no `.env` de cada ambiente. Homologação com remetente identificado ("[HOMOLOG]") ou envio restrito a e-mails de teste.
- No dev e no e2e: Mailpit no compose (captura os e-mails sem enviar).

---

## 2. Fluxo de branches e pipeline

```
feature/x ─┐
fix/y ─────┼─PR─▶ homolog ──PR──▶ main
           │      (deploy          (build refeito,
           │       homologação)     deploy produção)
hotfix/z ──────────────────────PR─▶ main ──merge──▶ homolog
```

| Evento | Testes | e2e | Deploy |
|---|---|---|---|
| PR → `homolog` ou → `main` | ✅ | ✅ | — |
| Push em `homolog` | ✅ | ✅ | homologação |
| Push em `main` | ✅ | ✅ | produção (aprovação opcional no environment `production`) |

Regras:

- `main` recebe código **somente** da `homolog` (ou de `hotfix/*`). Alteração reprovada na homologação é desfeita com `git revert` na `homolog` antes de promover.
- Depois de um hotfix, merge da `main` de volta na `homolog`.
- Branch de trabalho apagada automaticamente após o merge (Settings → *Automatically delete head branches*).
- Branches curtas: merge em poucos dias. A `feature/base-projeto` é a única exceção.

**e2e por etapa:**

- **Fases de backend:** e2e de API — o compose completo sobe no Actions e os testes chamam a API por HTTP, passando pelo Caddy (REST Assured num módulo separado, ou Playwright em modo API).
- **Fases de frontend:** acrescenta Playwright no navegador.

---

## 3. Estrutura do repositório

```
plataforma-cursos/
├── CLAUDE.md                    regras gerais do repositório
├── .claude/                     orientações para agentes de IA: rules, skills (commits, especificação),
│                                agentes e comandos do pipeline SDD (/sdd → /gerar-tasks → /implementar-tasks)
├── .sdd/                        specs e tasks geradas pelo pipeline SDD (quando usado)
├── backend/
│   ├── CLAUDE.md                regras do projeto Java (código e testes)
│   ├── .claude/skills/          criar-teste-unitario, criar-teste-integracao, criar-teste-e2e
│   │                            (cada uma com SKILL.md = processo e padrao.md = padrão)
│   ├── Dockerfile               targets: api | worker (com ffmpeg)
│   ├── pom.xml                  Surefire (*Test), Failsafe (*IT), JaCoCo, Spotless, perfil e2e
│   └── src/
│       ├── main/java/br/com/celke/cursos/
│       │   ├── shared/          config, security, erros, storage S3, fila, e-mail
│       │   ├── auth/
│       │   ├── usuario/
│       │   ├── curso/           curso, turma, módulo, aula
│       │   ├── matricula/       matrícula + AcessoAulaPolicy
│       │   ├── video/           upload, transcodificação, playlist
│       │   ├── progresso/
│       │   ├── arquivo/
│       │   ├── forum/
│       │   ├── certificado/
│       │   └── perfil/
│       │   (cada módulo: api/ · application/ · domain/ · infra/)
│       ├── test/java/           unitários (*Test) e integração (*IT) + support/
│       └── e2e/java/            e2e caixa-preta (*E2E: REST Assured + Awaitility)
├── web/                         (a partir da Fase 11) um pacote só, um build por app
│   ├── package.json
│   ├── compartilhado/           cliente HTTP, sessão, rotas protegidas, validação
│   ├── aluno/
│   ├── adm/
│   ├── site/                    gerador do site público (Node, sem dependências)
│   ├── e2e/                     Playwright contra a stack de e2e
│   ├── Dockerfile               build dos fronts + Caddy interno (aluno :8081, ADM :8082)
│   └── Caddyfile
├── infra/
│   ├── compose.yml              ambiente local (inclui Mailpit)
│   ├── compose.prod.yml         um ambiente dos servidores: API, worker, web, PostgreSQL, Garage
│   ├── compose.homolog.yml      sobreposição: limites menores e Swagger
│   ├── compose.e2e.yml          sobreposição: Caddy com *.localhost, Mailpit, seed e2e
│   ├── e2e.env                  variáveis da stack de e2e (só valores de teste)
│   ├── garage/garage.toml
│   ├── caddy/
│   │   ├── compose.yml          Caddy único da VPS, ligado às redes dos dois ambientes
│   │   ├── Caddyfile            trechos comuns (headers, api, mídia, restrição por IP)
│   │   ├── sites/               producao.caddy, homolog.caddy
│   │   ├── e2e.caddy            sites da stack de e2e
│   │   └── .env.example
│   └── scripts/                 gerar-env.sh, gerar-env-servidor.sh, preparar-vps.sh, deploy.sh, backup...
├── docs/
│   ├── plano-implementacao.md
│   ├── prompts/                 um prompt por fase (especificação e verificação)
│   └── prompts-iniciais/        sequência completa para criar o projeto do zero
└── .github/workflows/
    └── pipeline.yml
```

---

## 4. Fases

Cada fase = uma branch = um PR para `homolog`. As regras gerais ficam no `CLAUDE.md` da raiz; as do
backend, no `backend/CLAUDE.md`, com o padrão de cada nível de teste nas skills
`/criar-teste-unitario`, `/criar-teste-integracao` e `/criar-teste-e2e` (`backend/.claude/skills/`).
Definição de pronto de **todo** PR:

- [ ] `./mvnw verify` do backend passando (unitários + integração + cobertura + Spotless); `npm test` a partir da Fase 11
- [ ] e2e passando no Actions
- [ ] Rotas novas com matriz de papéis (anônimo / ALUNO / ADMIN) e, se usam a `AcessoAulaPolicy`, matriz de acesso ao conteúdo
- [ ] Endpoints novos documentados no OpenAPI
- [ ] Migrations compatíveis com a versão anterior (expandir → depois contrair)
- [ ] Nenhum segredo no código ou no compose
- [ ] Funcionando na homologação

---

### Fase 0 — Preparação (sem branch)

- [x] Criar repositório `celkecursos/plataforma-cursos` no GitHub
- [x] Primeiro commit na `main` (README, `.gitignore`, `docs/`)
- [x] Criar `homolog` a partir da `main`
- [x] Orientações para agentes de IA em `.claude/`: rules por caminho (segurança, fronts, testes, prompts),
  commits em Conventional Commits sem coautoria de IA (`/gc`, `/pc`), `/nova-branch`, `/abrir-pr`,
  `/revisar-dod` e o pipeline SDD com portões humanos
- [ ] Regras de proteção em `main` e `homolog`: só PR, checks obrigatórios (ativar quando o pipeline existir)
- [ ] Environments `homolog` e `production` com seus secrets
- [ ] Confirmar os subdomínios livres em `celke.uno`
- [ ] Obter host, porta e credenciais SMTP no painel da Iagente; configurar SPF/DKIM

---

## Bloco A — Backend

### Fase 1 — `feature/base-projeto`

Objetivo: API rodando local e na VPS (homologação e produção), com auth completa, CI, e2e de API e
deploy automático. Depois desta fase, toda funcionalidade nova segue o fluxo normal.

**1.1 Esqueleto do backend e padrão de testes** — capítulo 3

- [x] `CLAUDE.md` da raiz (regras gerais) e `backend/CLAUDE.md` (código e testes do projeto Java)
- [x] Skills `criar-teste-unitario`, `criar-teste-integracao` e `criar-teste-e2e` em `backend/.claude/skills/`, cada uma com o padrão do nível (`padrao.md`)
- [x] Projeto gerado no Spring Initializr: Spring Boot 4.1.1, Java 25, `br.com.celke.cursos`; dependências: webmvc, security, data-jpa, validation, flyway, actuator, postgresql, springdoc, testcontainers. As demais (AWS SDK, mail, Thymeleaf) entram na etapa que as usa
- [x] `application.yaml` com credenciais do banco sem valor padrão, `open-in-view` desligado, `ddl-auto: validate`, threads virtuais, shutdown gracioso, cabeçalhos do proxy, sem stack trace nas respostas; `application-worker.yaml` sem servidor HTTP, sem Flyway (as migrations são da API) e com `spring.main.keep-alive`
- [x] Sem usuário embutido do Spring Security (`UserDetailsServiceAutoConfiguration` excluída)
- [x] `SecurityConfig` (só na aplicação web, com `@ConditionalOnWebApplication`): stateless, sem CSRF/sessão/formulário/HTTP Basic, 401 para não autenticado; públicos só `GET` de health/probes e da documentação
- [x] Actuator: só `health`, com probes de liveness/readiness e sem detalhes
- [x] Swagger ligado por `DOCS_ENABLED=true` (dev e homologação), desligado por padrão
- [x] Surefire para `*Test` (com `-Dskip.unit=true` para pular), Failsafe para `*IT`, JaCoCo somando os dois com mínimo de 80% de linhas, Spotless (palantir-java-format) verificado no `verify`
- [x] `support/Containers` (PostgreSQL 18 como bean com `@ServiceConnection`) e `support/IntegrationTest` (`MockMvcTester`, `stamp()`)
- [x] `SegurancaPadraoIT`: health e probes públicos com `UP` e sem detalhes; demais rotas e métodos exigem autenticação; documentação desligada
- [x] Dockerfile multi-stage: build com JDK 25, jar em camadas, execução com JRE 25, `curl` para healthcheck e usuário sem root; targets `api` e `worker` (com ffmpeg)

**1.2 Ambiente local** — capítulo 4

- [x] `infra/compose.yml` (projeto `plataforma-cursos`): PostgreSQL 18.6, Garage v2.4.1, Mailpit v1.31.6, API e worker, com versões fixas
- [x] Variáveis obrigatórias com `${VARIAVEL:?mensagem}`; conexão do banco num bloco compartilhado (`x-banco`) pela API e pelo worker
- [x] `infra/.env.example` e `infra/scripts/gerar-env.sh`, que cria o `infra/.env` com segredos aleatórios (permissão 600) e não sobrescreve um existente
- [x] Garage em `--single-node --default-bucket`: `garage.toml` sem segredos (vêm de `GARAGE_RPC_SECRET` e `GARAGE_ADMIN_TOKEN`), volume de metadados e dados; bucket `midia` e chave da aplicação criados na primeira subida
- [x] PostgreSQL com o volume em `/var/lib/postgresql` (layout da versão 18)
- [x] Healthchecks (`pg_isready`, `garage status`, `mailpit readyz`, readiness da API com `curl`) e `depends_on: condition: service_healthy`
- [x] Worker sobe depois da API, que aplica as migrations
- [x] Portas ligadas apenas em `127.0.0.1` e configuráveis no `.env` (`API_PORTA`, `POSTGRES_PORTA`, `S3_PORTA`, `MAILPIT_PORTA`)
- [x] Swagger ligado no dev (`DOCS_ENABLED=true`)

**1.3 Padrões compartilhados** — capítulo 5

- [x] `ProblemDetail` (RFC 9457) com handler global: validação com a lista `erros` (`campo`, `mensagem`) em português, regra de negócio (`RegraNegocioException`, `NaoEncontradoException`, `ConflitoException`), erros do Spring MVC traduzidos em `messages.properties`, erro inesperado só com o id da requisição; `instance` e `requestId` em todo erro
- [x] 401/403 da segurança também em `ProblemDetail` (`RespostasSeguranca`)
- [x] Id de correlação `X-Request-Id` (aceito só se seguro, senão gerado) no MDC e na resposta; logs em JSON (ECS) no compose
- [x] `Pagina<T>` e paginação padrão (20, máximo 100); `Clock` único em UTC
- [x] `StorageService` (AWS SDK v2, path-style): presign GET/PUT, multipart, consulta, exclusão; presigner com o endereço público
- [x] Configuração do bucket aplicada pela API ao subir, de forma idempotente: CORS para os hosts do front (expondo `ETag`) e lifecycle que expira `uploads/` e uploads em partes incompletos
- [x] Fila: tabela `jobs` + `FilaService` (enfileirar, reservar com `SKIP LOCKED`, lease, concluir, falhar com backoff, falha definitiva), `ProcessadorFila` por tipo e consumo agendado só no worker
- [x] `EmailService` (valida destinatário e template) e envio no worker com Thymeleaf e SMTP; health check do e-mail desligado
- [x] `@ConfigurationProperties` validadas (`app.armazenamento`, `app.email`, `app.fila`)
- [x] `support/Containers` com Garage e Mailpit; `support/Mailpit`; controller de erros só de teste
- [x] Testes: handler de erros, id de correlação, `StorageService` contra o Garage real (inclusive preflight do CORS), fila (backoff, tentativas esgotadas, lease vencido, concorrência sem duplicidade), e-mail entregue no Mailpit com HTML escapado

**1.4 Usuários e autenticação** — capítulos 6 e 7

- [x] Migrations `usuarios` (e-mail único e em minúsculas), `usuario_papeis` e `refresh_tokens`
- [x] Hash de senha Argon2; BCrypt `$2y$` do Laravel aceito e regravado em Argon2 no login; política de senha por tamanho (admin: 12 a 128)
- [x] Comando `docker compose run --rm api criar-admin <email> "<nome>"`: sem servidor HTTP, senha digitada duas vezes no terminal
- [x] Access token JWT (Nimbus, HS256, 10 min, `sub` e `papeis`) com o Resource Server do Spring Security: só HS256, emissor e validade conferidos, papéis em `ROLE_*`; `JWT_SEGREDO` validado ao subir
- [x] Refresh opaco com hash SHA-256, rotação, família revogada na reutilização (gravada mesmo com a sessão recusada) e `SELECT ... FOR UPDATE`
- [x] Cookie `refresh_token`: `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 14 dias, **sem `Domain`**
- [x] Rotas `login`, `refresh`, `logout` (públicas), `sair-de-todos` e `me` (privadas); mesma resposta e mesmo tempo para e-mail inexistente e senha errada
- [x] Seed do profile `test` (admin, aluno); `support/UsuariosSeed`; `login()`, `bearer()` e `criarUsuario()` no `IntegrationTest`
- [x] Testes: token adulterado, expirado, de outra chave, com outro algoritmo, `alg: none` e de outro emissor recusados; rotação, reutilização, logout e sair de todos; matriz de papéis das rotas de autenticação

**1.5 Autorização e proteções** — capítulos 8 e 9

- [x] `SecurityFilterChain` nega tudo por padrão; rotas públicas declaradas com método HTTP; papéis do token + `@PreAuthorize` (matriz de papéis testada)
- [x] Limite por IP com Bucket4j nas rotas de autenticação (`LimitePorIpFilter`): conta toda requisição, roda antes da segurança, 429 em ProblemDetail com `Retry-After`
- [x] Bloqueio temporário por falhas seguidas: 5 por e-mail + IP e 20 por e-mail (qualquer IP), 15 min, contadores zerados por login correto ou após 1 h sem falhas; verificado antes de calcular o hash
- [x] Refresh e logout: verificação de `Origin` e `Sec-Fetch-Site` (CSRF), porque outras aplicações em `*.celke.uno` contam como o mesmo site; origens em `AUTH_ORIGENS`
- [x] Auditoria de login (`auditoria_login`): resultado, IP e user-agent de toda tentativa, em transação própria
- [x] `server.forward-headers-strategy: native`: `X-Forwarded-For` aceito só de proxy interno e lido da direita para a esquerda
- [x] Erros que nascem em filtros escritos por `ProblemDetailJson` (mesmo formato do handler global)

**1.6 Cadastro e recuperação de senha** — capítulo 10

- [x] Migration: `usuarios.email_confirmado_em` (contas existentes confirmadas), `tokens_uso_unico` e novo resultado `EMAIL_NAO_CONFIRMADO` na auditoria
- [x] Cadastro de aluno (`POST /api/auth/cadastro`) com conta pendente e link de confirmação (48 h); resposta igual para e-mail novo e já cadastrado — conta ativa recebe um aviso (no máximo um por hora), cadastro pendente recebe o link de novo
- [x] Confirmação (`confirmar-email`) e reenvio (`reenviar-confirmacao`); login com e-mail pendente recebe 403 próprio só quando a senha está certa
- [x] Recuperação (`recuperar-senha`, link de 1 h, resposta igual exista ou não a conta) e redefinição (`redefinir-senha`): troca a senha, confirma o e-mail e encerra todas as sessões
- [x] Tokens de uso único com hash SHA-256 (`TokenSeguro`, compartilhado com o refresh): um novo invalida os anteriores do mesmo tipo; no máximo 3 e-mails por hora por conta e tipo
- [x] Senha de aluno com 10 a 128 caracteres; contas do comando e dos seeds nascem confirmadas
- [x] Templates de e-mail (`confirmacao-email`, `recuperacao-senha`, `cadastro-existente`) com links para `FRONT_ALUNO_URL`; envio pelo worker (Mailpit no ambiente local)
- [x] Rotas da conta públicas (só POST) e no `LimitePorIpFilter`
- [x] Healthcheck do worker por arquivo de batimento (`BatimentoWorker`), separado do consumo da fila: o `up --wait` e o deploy passam a esperar o worker
- [x] Testes: jornadas de cadastro e recuperação com os e-mails lidos no Mailpit, link de uso único, limite de e-mails, mesma resposta para e-mail inexistente

**1.7 e2e de API e pipeline** — capítulo 11

- [x] `infra/compose.prod.yml`: stack dos servidores com Caddy, imagens do GHCR pela `TAG`, `restart: unless-stopped`, healthchecks e nenhuma porta publicada além das do Caddy
- [x] `infra/caddy/Caddyfile`: hosts pelo ambiente (`HOST_ALUNO`, `HOST_ADM`, `HOST_MEDIA`), só `/api/*` e `/actuator/health` chegam à API, 404 para o resto, headers de segurança, `-Server`, `lb_try_duration`, `X-Forwarded-For` substituído
- [x] `infra/compose.e2e.yml` + `infra/e2e.env` (valores só de teste, no git): HTTP em `*.localhost` na porta 8088, Mailpit, imagens locais, profile `e2e`, projeto Compose separado
- [x] Seed do profile `e2e` (`SeedE2e`: admin e aluno, senha de `SEED_E2E_SENHA`)
- [x] Perfil Maven `e2e` (`src/e2e/java`, REST Assured, Awaitility) com `support/` conforme o padrão da skill `criar-teste-e2e`; Spotless também nas fontes e2e
- [x] Jornadas `CadastroE2E` (cadastro → e-mail entregue pelo worker → confirmação → login), `SessaoE2E` (login → refresh → logout pelo Caddy, cookie sem `Domain`, origem recusada) e `ProxyE2E` (headers, rotas fora da API bloqueadas, `X-Forwarded-For` forjado sem efeito)
- [x] `.github/workflows/pipeline.yml`: testes → imagens (cache `type=gha`) → e2e → relatórios e `docker compose logs` em caso de falha; roda em PR e push para `homolog` e `main`
- [ ] Regras de proteção de `homolog` e `main` com os checks `Testes (unitários e integração)` e `e2e (stack completa)` obrigatórios (configuração no GitHub)

**1.8 VPS** — capítulos 12 e 13 · roteiro em [vps.md](vps.md)

- [x] `infra/scripts/preparar-vps.sh` (Ubuntu 24.04): Docker oficial com rotação de logs, administrador com sudo, `deploy` sem sudo no grupo docker, SSH só por chave e sem root (`01-plataforma.conf`, antes do `50-cloud-init.conf`), `ufw` (22/80/443), fail2ban, atualizações automáticas, swap de 2 GB, `/opt/plataforma-cursos/{caddy,producao,homolog}`
- [x] `compose.prod.yml` por ambiente (`AMBIENTE`): rede `plataforma-cursos-<ambiente>`, apelidos `<ambiente>-api` e `<ambiente>-garage`, nenhuma porta publicada, limites de recursos e `-Xmx` ajustado
- [x] `compose.homolog.yml`: limites menores, worker ligado com meia CPU (envia os e-mails da homologação), Swagger ligado
- [x] Caddy único (`infra/caddy/compose.yml`) nas portas 80/443, ligado às duas redes; `Caddyfile` com trechos comuns e `sites/producao.caddy` e `sites/homolog.caddy`
  - homologação restrita por IP (`HOMOLOG_IPS`) e com `X-Robots-Tag: noindex` — senha do Caddy não serve, porque ocuparia o cabeçalho `Authorization` do JWT
  - `adm.` de produção restrito por IP (`ADM_IPS`)
  - HSTS sem `includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, sem `Server`, `lb_try_duration`; `X-Forwarded-For` substituído pelo IP real
- [x] e2e usando o mesmo `Caddyfile` com `caddy/e2e.caddy`
- [x] `infra/scripts/gerar-env-servidor.sh`: `.env` de cada ambiente com segredos aleatórios e diferentes, hosts de `celke.uno`, SMTP para preencher
- [x] Roteiro `docs/vps.md`: painel, DNS, chaves, preparação, cópia dos arquivos, `.env`, GHCR, primeira subida, verificação
- [ ] Executar o roteiro na VPS (painel, DNS dos seis subdomínios, preparação, `.env`, login no GHCR)
- [ ] Primeira subida dos dois ambientes e do Caddy, com as imagens publicadas pelo deploy automático (1.9)

Limites de recursos:

| Serviço | Produção | Homologação |
|---|---|---|
| API | 1 GB, `-Xmx512m` | 768 MB, `-Xmx384m`, `cpus: 0.5` |
| Worker + ffmpeg | 2 GB, `-Xmx768m`, `cpus: 1.5` | 768 MB, `-Xmx256m`, `cpus: 0.5` (sempre ligado: envia os e-mails) |
| PostgreSQL | 1 GB | 512 MB |
| Garage | 768 MB | 512 MB |
| Caddy (compartilhado) | 128 MB | — |

**1.9 Deploy automático** — capítulo 14

- [x] Publicação no GHCR pelo job de e2e, depois das jornadas e só em push: as imagens testadas recebem a tag do SHA (sem rebuild); label `org.opencontainers.image.source` liga o pacote ao repositório
- [x] `infra/scripts/deploy.sh` (na VPS em `/usr/local/bin/plataforma-cursos-deploy`, dono root): valida `<producao|homolog> <sha>` (também de `SSH_ORIGINAL_COMMAND`), extrai só os arquivos de compose esperados, backup do banco, troca a tag, `up --pull missing --wait`, rollback automático para a versão anterior (arquivos e tag) se algum container não ficar saudável; `flock`, `trap '' HUP`, guarda 5 versões e 10 backups, `deploy.log`
- [x] Chave do deploy presa ao script no `authorized_keys` (`command=...,no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty`), instalada pelo `preparar-vps.sh`
- [x] Workflow reutilizável `deploy.yml` (environment `homolog` ou `production`, um deploy por ambiente, nunca cancelado); no pipeline: push em `homolog` → homologação; push em `main` → produção, só com a variável `DEPLOY_ATIVO=true` (sem ela, os deploys são pulados); só PRs cancelam execuções antigas
- [x] Testado localmente: deploy de uma versão boa, deploy de uma versão quebrada com rollback automático, backup restaurável, validação de argumentos (inclusive injeção pelo SSH) e deploy simultâneo barrado; chave presa ao script testada com `sshd` real (sem shell, terminal ou túnel)
- [x] Roteiro `docs/vps.md`: secrets do repositório, primeira subida pelo pipeline, Caddy e administradores, operação (histórico, voltar versão, atualizar Caddy e script)
- [ ] Secrets `VPS_HOST`, `VPS_SSH_KEY` e `VPS_KNOWN_HOSTS` e variável `DEPLOY_ATIVO=true` no repositório (conta dona) e, se o plano permitir, aprovação obrigatória no environment `production`
- [ ] Primeiro deploy nos dois ambientes pelo pipeline; Caddy no ar; administrador criado em cada um

**Pronto quando:** cadastro, login e logout funcionando pela API em `homolog-aluno.celke.uno/api` e
`aluno.celke.uno/api`, e-mail chegando pela Iagente, pipeline completo verde e um rollback testado de propósito.

---

### Fase 2 — `feature/cursos`

Objetivo: API de gestão de cursos, turmas, módulos e aulas. Capítulos 15 e 16.

- [x] Migration `V6`: `cursos` (slug único com `CHECK` do formato), `turmas`, `modulos` e `aulas`; status como texto com `CHECK` (curso `RASCUNHO`/`PUBLICADO`/`ARQUIVADO`, turma `PREPARACAO`/`ATIVA`/`ENCERRADA`, módulo e aula `ATIVO`/`INATIVO`); aula gratuita; `ON DELETE RESTRICT` em toda a hierarquia
- [x] Ordem de módulos e aulas única por pai com restrição `DEFERRABLE INITIALLY DEFERRED` (a reordenação troca todas as posições numa transação); item novo entra no fim; exclusão refaz a sequência; o pai fica travado (`PESSIMISTIC_WRITE`) enquanto a ordem muda
- [x] CRUD em `/api/adm/**` com Bean Validation: cursos (lista paginada com filtro por status, slug gerado do título quando não informado, 409 para slug repetido), turmas, módulos e aulas, e `PATCH .../status`
- [x] Exclusão só de item vazio (curso sem turmas, turma sem módulos, módulo sem aulas); o resto é arquivado, encerrado ou inativado pelo status, para não perder matrículas e histórico
- [x] Reordenação `PUT /api/adm/turmas/{id}/modulos/ordem` e `PUT /api/adm/modulos/{id}/aulas/ordem` com a lista completa de ids (400 se faltar, sobrar ou repetir)
- [x] `/api/adm/**` exige ADMIN no `SecurityConfig` (antes de ler o corpo) e de novo com `@PreAuthorize` nos controllers
- [x] Capa do curso: URL pré-assinada de PUT em `uploads/capas/<curso>/<uuid>` (JPEG ou PNG, até 5 MB); a confirmação aceita só a chave gerada para o mesmo curso, confere o tamanho real, recusa lado acima de 8000 px antes de decodificar, reduz para até 1280 × 720 e regrava em JPEG sem metadados em `capas/<curso>/<uuid>.jpg`; o original e a capa anterior são apagados. `ProcessadorImagem` em `shared/imagem` (reutilizado pela foto de perfil)
- [x] Catálogo público (`GET`, declarado no `SecurityConfig`): `/api/cursos` (publicados, paginado, ordem alfabética), `/api/cursos/{slug}` (módulos e aulas ativos da turma ativa mais recente, sem dados internos) e `/api/cursos/{slug}/capa` (endereço estável que redireciona para uma URL assinada nova, com cache de 5 minutos); rascunho e arquivado respondem 404
- [x] Testes: unitários (slug, ordenação, DTOs, serviços, capa, processamento de imagem com EXIF e transparência), integração (matriz de papéis de todas as rotas, CRUD, ordem com restrição adiável e criação simultânea, catálogo, capa com o Garage real)
- [x] e2e `CatalogoE2E`: o ADM cria curso → turma → módulo → aulas, envia a capa pelo Caddy direto ao Garage e publica; o visitante sem login vê o curso, o conteúdo e a capa; o aluno recebe 403 na gestão

**Pronto quando:** um curso completo, com capa, é cadastrado pela API do ADM e aparece no catálogo
público com o conteúdo da turma ativa.

---

### Fase 3 — `feature/matricula-acesso`

Objetivo: matrícula e uma política única de acesso. Capítulos 17 e 18.

- [x] Migration `V7` `matriculas`: usuário e turma (FK `RESTRICT`), início, fim opcional (sem prazo), status `ATIVA`/`CANCELADA`, forma de pagamento (`MANUAL`, `CORTESIA`, `PIX`, `CARTAO`, `BOLETO`), transação, arquivamento; índice único parcial de uma matrícula ativa por aluno e turma e de transação por forma de pagamento (pronto para o webhook)
- [x] "Vencida" e "aguardando início" não são status gravados: `Matricula.situacao(agora)` calcula pelo `Clock` (fim inclusivo), sem job para marcar matrículas
- [x] Fronteira entre módulos: a matrícula guarda só os ids; o conteúdo vem do `ConteudoTurmaService` (exportado pelo módulo `curso`, também usado pelo catálogo) e os usuários do `UsuarioService`
- [x] Endpoints do ADM: listar as matrículas da turma, matricular por e-mail (início padrão agora; turma encerrada, usuário inexistente, matrícula ativa repetida e transação repetida recusados), alterar período, cancelar; turma com matrículas não pode ser excluída (409 pela FK)
- [x] `AcessoAulaPolicy` (classe pura com `Clock`): aula liberada com matrícula vigente na turma ou aula aberta (gratuita, turma ativa, curso publicado); turma inteira só com matrícula vigente. `AcessoAulaService.exigirAcesso` é a porta de entrada para vídeo, arquivo, fórum, progresso e certificado
- [x] Endpoints do aluno (`/api/aluno`, escopo do token): meus cursos por aba (`ATIVOS` com as futuras, `ARQUIVADOS`, `EXPIRADOS`; cancelada em nenhuma), turma com módulos e aulas ativos, aula, arquivar/desarquivar (matrícula de outro aluno responde 404)
- [x] Testes: situação e política em tabela (instante antes, exato e depois de cada limite), matriz de papéis, matriz de acesso ao conteúdo (vigente, sem, vencida, cancelada, futura, gratuita, curso em rascunho, conteúdo inativo, outra turma), IDOR
- [x] e2e `MatriculaE2E`: conta nova vê só a aula gratuita; matriculada vê a turma e a aula paga; com o período vencido, 403 e o curso nos expirados

**Pronto quando:** o ADM matricula um aluno e o acesso dele às aulas segue o período da matrícula,
decidido num ponto só.

---

### Fase 4 — `feature/upload-video`

Objetivo: o vídeo enviado vira HLS comprimido. Capítulos 19 a 21.

- [x] Migration `V8` `videos`: um vídeo por aula (FK `RESTRICT`: aula com vídeo responde 409 na exclusão), status `AGUARDANDO_ENVIO`/`PENDENTE`/`PROCESSANDO`/`PRONTO`/`ERRO`, chaves do original e do HLS, tamanho original e final, duração, resolução, variantes, tempo de processamento e erro. O HLS publicado só muda quando a conversão do envio novo termina
- [x] Envio em partes (`POST /api/adm/aulas/{id}/video/envio`): MP4, MOV, MKV ou WebM até 5 GB, validados antes de assinar; partes de 16 MiB (maiores acima de 10.000 partes), uma URL pré-assinada por parte em `uploads/videos/<aula>/<uuid>`; envio anterior ainda aberto é abortado
- [x] Conclusão (`.../envio/conclusao` com os ETags): junta as partes, confere o tamanho real igual ao informado e a assinatura do início do arquivo (`ftyp` ou EBML); recusado é apagado e o vídeo fica com erro; aceito vai para a fila na mesma transação
- [x] Worker (`TranscodificacaoManipulador`): `ffprobe` → ffmpeg via `ProcessBuilder` (lista de argumentos, `nice -n 10`), numa passada só
  - H.264 CRF 24 (`veryfast`), `maxrate` 2800k/5000k, AAC 128k, segmentos de 6 s, 720p + 1080p sem aumentar a resolução de origem (abaixo de 720p, uma variante na altura original)
  - thumbnail; envio para `videos/<aula>/<job>/`; apaga o original e o HLS anterior só depois de publicar
- [x] Lease renovado a cada minuto durante a conversão; worker que cai deixa o lease vencer e o job volta para a fila (refaz o prefixo do job do zero); `stop_grace_period: 30s`. Arquivo inválido é erro permanente; desistência da fila (`ManipuladorJob.desistir`) deixa o vídeo com erro
- [x] Endpoints do ADM: status com tamanho antes/depois, redução percentual, variantes, segundos de processamento por minuto de vídeo e thumbnail; reprocessar (só com erro e com o original ainda no bucket); remover o vídeo e os arquivos (fora do processamento)
- [ ] Medir tempo de processamento por minuto de vídeo na VPS e registrar no tutorial (o ADM já mostra a medida: `segundosProcessamentoPorMinuto`)
- [x] Testes: unitários (formatos e assinaturas, divisão em partes, variantes, argumentos do ffmpeg, saída do ffprobe, entidade, serviços, fila), integração com o Garage e o ffmpeg reais (transcodificação do vídeo de 3 s, troca, extensão falsa, vídeo corrompido e reprocessamento, matriz de papéis); o pipeline instala o ffmpeg
- [x] e2e `VideoE2E`: upload em partes pelo Caddy direto ao Garage → worker → status "pronto" com a thumbnail no host de mídia

**Pronto quando:** o ADM envia o vídeo de uma aula direto ao armazenamento e, sem passar o arquivo pela
API, ele vira HLS em 720p/1080p com o tamanho antes e depois registrado.

---

### Fase 5 — `feature/entrega-video`

Objetivo: vídeo protegido e progresso salvo. Capítulos 22 a 24.

- [x] `GET /api/aulas/{id}/video.m3u8` (master) e `GET /api/aulas/{id}/video/{variante}.m3u8`: passam pela `AcessoAulaService` (matrícula vigente ou aula gratuita); a master aponta para as variantes servidas pela API e cada variante traz os segmentos por URLs pré-assinadas válidas pela duração do vídeo + `app.video.margem-playlist` (2 h). Só nomes no formato gerado pelo worker são aceitos na reescrita; resposta com `Cache-Control: no-store`
- [ ] Opcional (cap. 23): HLS com AES-128 e chave por endpoint autenticado — não feito nesta fase
- [x] Progresso (migration `V9` `progresso_aulas`, um registro por aluno e aula): `GET` e `PATCH /api/aluno/aulas/{id}/progresso` (posição, vista, revisar; só os campos enviados mudam) e `GET /api/aluno/turmas/{id}/progresso` (percentual das aulas ativas vistas, arredondado para baixo; turma sem aulas = 0%). Sempre no escopo do token; a turma exige matrícula vigente (`AcessoAulaService.exigirAcessoTurma`)
- [x] Log de acesso (migration `V10` `acessos_video`): cada abertura da master registra usuário, aula, IP e navegador; o worker apaga todo dia o que passa de `app.video.retencao-log-acesso` (180 dias, o prazo do Marco Civil para registros de acesso a aplicações)
- [x] Testes: unitários (reescrita das playlists e playlists adulteradas, validade, percentual com as bordas, entidade e serviço do progresso, retenção do log), integração (matriz de acesso ao conteúdo na playlist e no progresso, segmento assinado baixando do Garage com `X-Amz-Expires` = duração + margem, isolamento entre alunos, retenção do log)
- [x] e2e `EntregaVideoE2E` (margem de 5 s na stack de e2e): playlist assinada → segmento baixa com 200 → após expirar, recusado (o Garage responde 400 "Date is too old"; a AWS, 403); progresso refletido na turma

**Pronto quando:** o aluno matriculado assiste ao vídeo por uma playlist assinada, sem acesso direto ao
bucket, e retoma de onde parou com o progresso refletido na turma.

---

### Fase 6 — `feature/arquivos-apoio`

Capítulo 25.

- [x] Migration `V11` `arquivos_apoio`: de uma aula **ou** de um módulo (`CHECK` com `num_nonnulls`), título, nome de download, chave, tipo e tamanho; aula ou módulo com arquivos responde 409 na exclusão
- [x] Envio pelo ADM em duas etapas, como a capa: `POST /api/adm/{aulas|modulos}/{id}/arquivos/envio` (ZIP, PDF ou TXT até 200 MB) → PUT direto no Garage → `POST /api/adm/{aulas|modulos}/{id}/arquivos` confere a chave gerada para o mesmo dono, o tamanho real e o conteúdo (assinatura do ZIP e do PDF; texto sem byte nulo) e move de `uploads/` (que expira) para `arquivos/`; recusado é apagado. Nome de download limpo (sem caminho, acento ou aspas) e com a extensão do formato real. `DELETE /api/adm/arquivos/{id}` remove do bucket
- [x] Aluno (`/api/aluno`): lista da aula (mesmo acesso da aula, gratuita inclusive) e do módulo (matrícula vigente na turma; módulo inativo responde 404) e `GET /arquivos/{id}/download` com URL pré-assinada de 5 minutos que baixa como anexo (`Content-Disposition` com o nome do arquivo); a chave do bucket nunca sai na resposta
- [x] Testes: unitários (formatos, nomes, serviços), integração (matriz de papéis do ADM, matriz de acesso ao conteúdo na aula e no módulo, conteúdo falso, chave de outra aula, exclusões bloqueadas) e e2e `ArquivoE2E` (envio pelo Caddy direto ao Garage; download bloqueado sem matrícula e liberado com matrícula)

**Pronto quando:** o aluno matriculado baixa o arquivo da aula ou do módulo por uma URL curta, e sem
matrícula recebe 403.

---

### Fase 7 — `feature/forum`

Capítulo 26.

- [x] Migration `V12`: `forum_topicos` (da turma ou de uma aula da turma; aula excluída vira tópico da turma), `forum_respostas` (marca de instrutor) e `forum_anexos` (de um tópico **ou** de uma resposta); Markdown guardado junto com o HTML já filtrado
- [x] Aluno (`/api/aluno`, só com matrícula vigente na turma): listar tópicos visíveis (fixados primeiro, filtro por aula, contagem de respostas), perguntar, ver o tópico com as respostas e responder; tópico ou resposta oculta não aparece (404)
- [x] Markdown → HTML em duas camadas: commonmark com `escapeHtml` e `sanitizeUrls` e sanitizador OWASP com lista fechada de elementos (sem eventos, estilo, imagens ou iframes); links com `rel="nofollow noopener noreferrer"`. Só o HTML filtrado sai na resposta
- [x] Anexos de imagem: URL pré-assinada em `uploads/forum/<usuario>/<uuid>`; ao publicar, a API confere que a chave é do próprio usuário (contra IDOR), regrava em JPEG sem metadados (até 1600 × 1600) em `forum/anexos/` e devolve URLs temporárias; até 3 por mensagem
- [x] Moderação no ADM (`/api/adm`): todos os tópicos e respostas (ocultos inclusive), ocultar/mostrar e fixar tópico, ocultar resposta, responder como instrutor (o tópico fica marcado como respondido)
- [x] Limite de mensagens por aluno contado no banco (`app.forum`: 20 perguntas e respostas por hora): 429 com `Retry-After` até a mais antiga sair da janela
- [x] Testes: unitários (XSS em tabela, entidade, limite, anexos e IDOR, serviços, DTOs), integração (pergunta e resposta do instrutor, XSS guardado, anexo e IDOR, sem matrícula, moderação, 429, matriz de papéis da moderação)
- [x] e2e `ForumE2E`: aluno pergunta (com HTML malicioso filtrado), admin responde como instrutor, aluno vê a resposta

**Pronto quando:** o aluno matriculado pergunta no fórum da aula, o instrutor responde pela moderação e
o aluno vê a resposta, sem nenhum HTML perigoso chegando ao navegador.

---

### Fase 8 — `feature/certificado`

Capítulo 27.

- [x] Regra de conclusão por turma (migration `V13`: `percentual_conclusao` de 1 a 100, padrão 100, e `carga_horaria_horas` opcional), definida em `PUT /api/adm/turmas/{id}/certificado`; turma sem aulas nunca é concluída
- [x] Situação (`GET /api/aluno/turmas/{id}/certificado`: percentual visto, exigido, concluiu, certificado se já emitido) e emissão (`POST`, com o nome completo confirmado pelo aluno), ambas com matrícula vigente; emitir de novo devolve o mesmo certificado (dados impressos congelados na emissão: nome, curso, turma, carga horária)
- [x] PDF com openhtmltopdf a partir de um template Thymeleaf, gerado uma vez e guardado em `certificados/<turma>/<codigo>.pdf`; download do próprio aluno por URL de 5 minutos (de outro aluno, 404); lista em `GET /api/aluno/certificados`
- [x] Código de validação de 12 caracteres sem letras ambíguas (60 bits aleatórios) e `GET /api/certificados/{codigo}` público, sem e-mail nem ids internos
- [x] Testes: unitários (regra com as bordas, código, nome, serviço, turma), integração (ciclo completo com PDF no Garage, regra do ADM, sem matrícula, download de outro aluno, validações) e e2e `CertificadoE2E` (aluno conclui → gera certificado → baixa o PDF → código valida sem login)

**Pronto quando:** o aluno que concluiu a turma baixa o certificado em PDF e qualquer pessoa confere o
código impresso nele.

---

### Fase 9 — `feature/perfil-termos`

Capítulo 28.

- [x] Perfil (`/api/perfil`, para aluno e admin, sempre pelo token): dados sem senha nem hash, troca de nome, troca de senha (exige a atual, política do papel — aluno 10, admin 12 —, diferente da atual; encerra todas as sessões e limpa o cookie de refresh) e foto (migration `V14`: `foto_chave`; envio direto ao bucket, chave do próprio usuário contra IDOR, regravada em JPEG sem metadados até 400 × 400, anterior apagada)
- [x] Termos versionados (migration `V14`: `termos` com tipo `TERMOS_USO`/`POLITICA_PRIVACIDADE` e versão crescente; `aceites_termos` com IP e navegador): a vigente de cada tipo é a maior versão; `GET /api/termos/vigentes` público, `GET /api/termos/pendentes` e `POST /api/termos/{id}/aceite` (só a vigente; antiga 409)
- [x] Bloqueio: `TermoPendenteInterceptor` em `/api/aluno/**` e `/api/aulas/**` responde 403 com `termosPendentes` enquanto houver vigente sem aceite (primeiro acesso e cada versão nova); perfil, sessão e termos continuam liberados. `RegraNegocioException.propriedades()` leva campos extras ao ProblemDetail
- [x] ADM: `POST /api/adm/termos` publica a próxima versão (Markdown → HTML filtrado pelo `MarkdownSeguro`, agora em `shared/texto`) e `GET /api/adm/termos?tipo=` lista as versões
- [x] Testes: unitários (termos, interceptor, perfil), integração (perfil com usuário próprio, troca de senha encerrando a sessão, foto e IDOR, bloqueio e aceite — apagando os termos ao fim, porque o banco é compartilhado entre as classes) e e2e `PerfilTermosE2E`. No e2e, `Api.login` aceita os termos pendentes como o front fará no primeiro acesso; `Api.loginSemAceite` serve à jornada do bloqueio
- [x] e2e de API: novo termo publicado → rotas do aluno bloqueadas até o aceite; perfil atualizado e troca de senha invalidando o refresh antigo

**Pronto quando:** o aluno cuida do próprio perfil e só usa a área do aluno depois de aceitar os termos
vigentes, a cada versão nova.

---

### Fase 10 — `feature/operacao`

Capítulos 29 a 31. Fica antes do front porque precisa estar pronta antes de entrarem dados reais em produção.

- [x] Backup com restic para fora da VPS (`infra/scripts/backup.sh`): dump do PostgreSQL por stdin e arquivos do Garage com o snapshot consistente dos metadados; retenção 7/4/6. Restauração (`restaurar.sh`) testada na stack de e2e — banco e os 11 objetos do bucket de volta — e documentada em [operação](operacao.md)
- [ ] Agendar o backup no cron da VPS e repetir o teste de restauração na homologação (depende da VPS no ar)
- [x] Alertas do worker por e-mail (`ALERTA_EMAIL`): jobs que foram para ERRO e disco abaixo de 15% livre, a cada 15 minutos
- [ ] Monitor de uptime externo (UptimeRobot ou Better Stack) no health check e no site (depende da VPS no ar)
- [x] Rotação de logs do Docker (`json-file`, 5 × 10 MB) em todos os serviços; uploads órfãos já expiram pelo bucket (2 dias); roteiro para limpar a homologação
- [x] Conta das horas de vídeo que cabem no disco e plano de saída para o R2/B2 (troca de endpoint + `rclone sync`) em [operação](operacao.md)
- [x] Revisão OWASP ASVS nível 1 documentada; Trivy no pipeline (críticos com correção quebram o build — já corrigiu Tomcat e Jackson); OWASP ZAP baseline na stack local (0 falhas; `Cross-Origin-Resource-Policy` acrescentado ao Caddy)
- [ ] OWASP ZAP baseline na homologação a cada versão (depende da VPS no ar)

**Pronto quando:** existe backup diário fora da VPS com a restauração testada, os alertas chegam por
e-mail e o pipeline barra imagens com vulnerabilidade crítica conhecida.

---

## Bloco B — Frontend

**Biblioteca de layout:** `@celke/kole-react`, instalada do GitHub (`github:celkecursos/kole-react`)
com o commit fixo no `package.json`. Ela traz a moldura (`Estrutura`, `TelaLogin`), formulários,
tabelas e feedback. Os componentes próprios da área do aluno (`PlayerVideo` com hls.js, `CardCurso`,
`ListaAulas`, fórum) ficam no app enquanto não existirem na biblioteca.

### Fase 11 — `feature/web-base`

Capítulo 33.

- [x] `web/` com `aluno` e `adm` (Vite + React + TypeScript, Node 24); um pacote só, sem workspaces; `site` entra na Fase 14
- [x] `@celke/kole-react` do GitHub com commit fixo
- [x] Cliente HTTP: token em memória, refresh silencioso, fila de requisições durante o refresh
- [x] Rotas protegidas por papel; tratamento de termo pendente
- [x] Aluno: login, cadastro, confirmação de e-mail, recuperar senha
- [x] ADM: login, layout `Estrutura`
- [x] Proxy do Vite `/api → localhost:8080` (mesma origem também no dev)
- [x] Imagem `web`: build dos fronts + Caddy servindo `/srv/aluno` (:8081) e `/srv/adm` (:8082); o Caddy da VPS manda `/api` para a API e o resto para ela
- [x] Caddy: CSP (mídia em `MIDIA_ORIGEM`), `X-Frame-Options`, cache longo nos assets com hash e sem cache no `index.html` (o Swagger da homologação continua: a API ainda é testada por ele)
- [x] Vitest + Testing Library; Playwright no navegador entra no `pipeline.yml`
- [x] e2e (Playwright, caminho feliz e infeliz): login aluno e admin, senha errada, cadastro com validação e confirmação pelo Mailpit, recuperação de senha, links inválidos, aluno barrado no ADM, sessão por refresh, refresh reutilizado, logout

### Fase 12 — `feature/web-adm`

Capítulo 34.

- [x] Cursos, turmas, módulos e aulas (`Tabela`, `Form`, `Paginacao`, `Abas`); reordenar com os botões subir/descer (acessível por teclado, uma troca por clique)
- [x] Upload de vídeo multipart direto ao Garage com progresso e cancelamento, status do processamento (consulta a cada 3 s), reprocessar, remover
- [x] Capa do curso, arquivos de apoio (aula e módulo), matrículas (período, cancelamento), moderação do fórum (responder, fixar, ocultar), certificado da turma, termos
- [x] e2e (Playwright, caminho feliz e infeliz): admin cria curso completo e envia vídeo curto até "pronto"; validações na tela, conflitos da API (slug repetido, exclusão com filhos), formatos recusados, matrícula sem conta e em turma encerrada, moderação do fórum

### Fase 13 — `feature/web-aluno`

Capítulo 35.

- [x] Meus cursos em abas (ativos, arquivados, expirados), arquivar/desarquivar
- [x] Turma com módulos, aulas, arquivos do módulo e progresso (vistas e revisar)
- [x] Aula: player HLS (hls.js; token só nas playlists da API) com retomada e envio da posição, anterior/próxima, vista/revisar, arquivos, fórum da aula (pergunta e resposta; as imagens nas mensagens entram na Fase 18)
- [x] Certificado (situação, emissão com o nome completo, PDF), lista de certificados e perfil (nome, foto, senha)
- [x] e2e (Playwright, caminho feliz e infeliz): login → turma → aula → vídeo toca até o fim → vista → progresso da turma; retomada, revisar, navegação, download do arquivo, fórum, certificado (bloqueado → emitido → validado), perfil; sem matrícula, matrícula futura, arquivar

### Fase 14 — `feature/site`

Capítulo 36.

- [x] Layout novo com os tokens da `@celke/kole-react` (HTML estático, sem JavaScript de layout)
- [x] Pré-renderização estática (uma página HTML por rota) em `cursos.celke.uno`: o container `site` gera ao subir e a cada `SITE_INTERVALO` (padrão 5 min) a partir da API do próprio ambiente, com troca atômica da versão; a mesma imagem serve homologação e produção
- [x] Home, catálogo, página do curso, validar certificado, termos, privacidade, 404
- [x] SEO: meta tags, canônico, Open Graph, dados estruturados (Course), sitemap, `robots.txt` (homologação fora dos buscadores)
- [x] Meta de Lighthouse no CI (desktop): desempenho ≥ 90, acessibilidade ≥ 95, boas práticas ≥ 95, SEO ≥ 90 em todas as páginas

---

### Fase 15 (opcional) — `feature/migracao-legado`

Capítulo 37.

- [ ] ETL do MySQL do CLMS: usuários, cursos, turmas, módulos, aulas, matrículas, progresso
- [ ] Mapear status numéricos do legado (`3, 4, 13, 14, 15`...) para os enums novos
- [ ] Senhas `$2y$` do Laravel aceitas no primeiro login e re-hash
- [ ] Reprocessar vídeos existentes no S3 para HLS


### Fase 16 — `feature/pagamentos`

Prompt: [`docs/prompts/f16-01-pagamentos-hotmart.md`](prompts/f16-01-pagamentos-hotmart.md). Operação e
configuração da Hotmart: [`docs/pagamentos.md`](pagamentos.md).

- [x] Módulo `pagamento` com a porta `GatewayPagamento` (um bean por meio de pagamento) e o status de pedido único para todos
- [x] Meio de pagamento configurado pelo painel (formulário a partir dos campos do gateway, segredos com AES-GCM, um ativo por vez)
- [x] Hotmart com o **webhook 2.0.0** (`X-HOTMART-HOTTOK`, JSON com `id` por evento); a versão 1.0.0 não é mais recomendada
- [x] Checkout por redirecionamento com o código do pedido no `xcod`; página de retorno que acompanha o status
- [x] Notificações idempotentes, processadas pela fila, tolerantes a fora de ordem; matrícula criada, mantida ou cancelada conforme o status
- [x] Conta do comprador sem senha, com link de criar senha (nunca senha por e-mail)
- [x] Reconciliação pela API de vendas (preparada, ligada por configuração)
- [x] Painel: meios de pagamento, ofertas por turma, pedidos e eventos recebidos (reprocessar)
- [x] Testes unitários, de integração, e2e e Playwright com caminho feliz e infeliz
- [x] `PAGAMENTOS_CHAVE` nos `.env` dos ambientes; homologação (restrita por IP) aceita só o caminho do webhook de fora
- [ ] Na homologação: "Enviar teste" da Hotmart de cada evento, fixtures trocadas pelos corpos reais


### Fase 17 — `feature/auditoria`

Prompt: [`docs/prompts/f17-01-auditoria-e-logs.md`](prompts/f17-01-auditoria-e-logs.md). Operação:
[`docs/operacao.md`](operacao.md), seções 4 e 4.1.

- [x] Trilha de auditoria (`auditoria`) gravada por um ouvinte dos eventos do Hibernate, na transação da mudança: usuário, operação, entidade e id, antes e depois (só o que mudou), origem (rota ou job), IP, navegador, id da requisição e data
- [x] Dados sensíveis (`@DadoSensivel`) como `***`; entidades de log e de sessão fora da trilha (`@SemAuditoria`)
- [x] Só inclusão garantida pelo banco (gatilho); retenção de 5 anos pelo worker
- [x] Tela de consulta no ADM (Segurança → Auditoria) com filtros e o antes/depois lado a lado
- [x] Logs da API e do worker também em arquivo, no volume `logs`: um por dia, 180 dias ou 2 GB por serviço
- [x] Testes unitários, de integração e Playwright

### Fase 18 — `feature/forum-anexos`

Prompt: [`docs/prompts/f18-01-anexos-no-forum.md`](prompts/f18-01-anexos-no-forum.md).

- [x] Imagens na pergunta e na resposta do aluno: escolher, arrastar ou colar um print (no texto também), com prévia, remover e progresso do envio
- [x] Tipo, tamanho e quantidade conferidos em todos os caminhos (o `accept` do seletor não filtra arrastar e colar): JPEG ou PNG, até 5 MB, até 3 por mensagem
- [x] Envio direto ao armazenamento só ao publicar (a API apaga o envio ao processá-lo; uma nova tentativa envia de novo)
- [x] Imagens publicadas na pergunta e nas respostas, para o aluno e na moderação do ADM, com visualizador em tela cheia
- [x] Testes unitários e Playwright (caminho feliz e infeliz)

### Fase 19 — `feature/seguranca`

Prompt: [`docs/prompts/f19-01-seguranca.md`](prompts/f19-01-seguranca.md). Operação:
[`docs/operacao.md`](operacao.md), seção 7.

- [x] Segundo fator (TOTP, RFC 6238) obrigatório para administradores: desafio depois da senha, cadastro por QR code, códigos de recuperação de uso único, segredo cifrado (`TOTP_CHAVE`), código sem reuso, comando `redefinir-2fa`
- [x] Usuário do banco só de dados para a API e o worker; migrations com o dono do schema no serviço `migracao` (comando `migrar`), que cria e libera o usuário da aplicação
- [x] Política de senha: lista de senhas comuns e vazadas, nome e e-mail, sequências e repetições; medidor de força nas telas
- [x] Limite por usuário nas gravações e no checkout
- [x] Dependabot (Maven, npm, Actions, Docker) e `npm audit` no pipeline
- [x] ADM → Segurança → Usuários: busca, bloquear e desbloquear, encerrar as sessões (o access token já emitido deixa de valer na hora), tudo na auditoria
- [x] Checkout só em HTTPS nos domínios da Hotmart; a compra só abre o pedido com a confirmação do aluno
- [x] Token dos links de e-mail sai da barra de endereço depois de lido
- [x] Teste que varre todas as rotas da API: só as públicas declaradas respondem sem login
- [x] Testes unitários, de integração e Playwright

### Fase 20 — `feature/lgpd`

Prompt: [`docs/prompts/f20-01-lgpd.md`](prompts/f20-01-lgpd.md). Operação:
[`docs/operacao.md`](operacao.md), seção 4.2. Modelo da política:
[`docs/politica-privacidade-modelo.md`](politica-privacidade-modelo.md).

- [x] Baixar meus dados: JSON com a conta e uma seção por módulo (`DadosPessoais` em cada módulo)
- [x] Excluir minha conta: senha e confirmação, aviso por e-mail, conta anonimizada, progresso, certificados, foto, sessões e segundo fator apagados, pedidos sem e-mail e nome; o que tem prazo legal fica
- [x] Retenção da auditoria de login (180 dias)
- [x] Modelo da política de privacidade com dados, finalidades, bases legais e os prazos aplicados pelo sistema
- [x] Perfil do aluno → Privacidade; testes de integração e Playwright

---

## 5. Mapa fase → capítulos do tutorial

O conteúdo de cada capítulo está em [capitulos.md](capitulos.md).

| Fase | Branch | Capítulos |
|---|---|---|
| 0 | — | 1, 2 |
| 1 | `feature/base-projeto` | 3–14 |
| 2 | `feature/cursos` | 15, 16 |
| 3 | `feature/matricula-acesso` | 17, 18 |
| 4 | `feature/upload-video` | 19–21 |
| 5 | `feature/entrega-video` | 22–24 |
| 6 | `feature/arquivos-apoio` | 25 |
| 7 | `feature/forum` | 26 |
| 8 | `feature/certificado` | 27 |
| 9 | `feature/perfil-termos` | 28 |
| 10 | `feature/operacao` | 29–31 |
| — | repositório da `@celke/kole-react` | 32 |
| 11 | `feature/web-base` | 33 |
| 12 | `feature/web-adm` | 34 |
| 13 | `feature/web-aluno` | 35 |
| 14 | `feature/site` | 36 |
| 15 | `feature/migracao-legado` | 37 |
| 16 | `feature/pagamentos` | 38–40 |
| 17 | `feature/auditoria` | 41 |
| 18 | `feature/forum-anexos` | 26 |
| 19 | `feature/seguranca` | 42, 43 |
| 20 | `feature/lgpd` | 44 |
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

Fase 19 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/lgpd
```

## Orientações para o usuário

Esta etapa tem passos que só o usuário faz, fora do código (no navegador, no painel da Hostinger, no DNS, no GitHub ou na VPS). Depois de gravar os arquivos e passar na **Verificação**, **mostre no terminal**, para o usuário, cada bloco abaixo **exatamente como está**, um de cada vez, na ordem. Depois de cada bloco, pare e espere o usuário confirmar que concluiu antes de mostrar o próximo. Quando ele informar um valor (o IP da VPS, por exemplo), use-o nos blocos seguintes no lugar de `<ip-da-vps>`.

### Política de privacidade

```text
==> LGPD — PUBLICAR A POLÍTICA DE PRIVACIDADE

1. Abra docs/politica-privacidade-modelo.md, troque o que está entre [colchetes] pelos dados da
   empresa (razão social, CNPJ, endereço, encarregado e e-mail) e revise com quem cuida do jurídico.
2. No ADM → Termos, publique o texto como nova versão da Política de privacidade: os usuários aceitam
   no próximo acesso.
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

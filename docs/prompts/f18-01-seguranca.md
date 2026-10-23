# Segurança: segundo fator, menos privilégio e senhas fortes

**Fase:** 18
**Branch:** `feature/seguranca`

## Prompt

Vamos reforçar a segurança da plataforma para que uma senha vazada, uma conta roubada ou uma falha na
aplicação causem o menor estrago possível. Siga o `backend/CLAUDE.md`, as rules de `.claude/rules/` e os
padrões do `web/`. Não faça commit.

Entregas:

1. Segundo fator (TOTP) obrigatório para os administradores.
2. Usuário do banco só de dados para a API e o worker; migrations com o dono do schema.
3. Política de senha que recusa senhas comuns, vazadas e previsíveis, com medidor de força nas telas.
4. Limite por usuário nas gravações e no checkout.
5. Dependabot e `npm audit` no pipeline.
6. ADM → Segurança → Usuários: bloquear, desbloquear e encerrar as sessões.
7. Checkout só em HTTPS nos domínios do meio de pagamento.
8. Token dos links de e-mail fora da barra de endereço.
9. Compra só com a confirmação do aluno.
10. Um teste que varre todas as rotas e falha se alguma nascer aberta.

### 1. Segundo fator dos administradores (módulo `auth`)

**TOTP sem biblioteca** — `auth/domain/Totp`: RFC 6238 com HMAC-SHA1, passos de 30 s e 6 dígitos;
segredo de 20 bytes em Base32 (`gerarSegredo`, `base32`, `decodificarBase32`); `uri(emissor, conta,
segredo)` no formato `otpauth://totp/<emissor>:<conta>?secret=...&issuer=...&algorithm=SHA1&digits=6&period=30`
(espaço como `%20`); `verificar(segredo, codigo, agora, ultimoPassoUsado)` aceita um passo antes e um
depois do atual, compara em tempo constante, ignora espaços e só aceita passo **maior** que o último
usado (o mesmo código não vale duas vezes). Testes com os vetores SHA-1 da RFC (59 s → `287082`,
1111111109 → `081804`, 1111111111 → `050471`, 1234567890 → `005924`, 2000000000 → `279037`).

**Banco** (`V17__seguranca.sql`):

- `segundo_fator` (`usuario_id` PK e FK com `ON DELETE CASCADE`, `segredo BYTEA`, `ativado_em`,
  `ultimo_passo`, `criado_em`): `ativado_em` nulo = cadastro iniciado e não confirmado.
- `codigos_recuperacao` (`id`, `usuario_id`, `hash VARCHAR(64)`, `usado_em`, `criado_em`).
- `ck_tokens_uso_unico_tipo` passa a aceitar `DESAFIO_SEGUNDO_FATOR`; `ck_auditoria_login_resultado`
  passa a aceitar `SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`, `SEGUNDO_FATOR_CADASTRADO` e
  `CODIGO_RECUPERACAO`.
- `usuarios.sessoes_encerradas_em TIMESTAMPTZ` (entrega 6).

As entidades `SegundoFator` e `CodigoRecuperacao` são `@SemAuditoria` (o último passo muda a cada login;
os eventos ficam na auditoria de login).

**Configuração** — `SegundoFatorProperties` em `app.auth.segundo-fator`: `chave` (`${TOTP_CHAVE:}`,
`@NotBlank`, 32 bytes em Base64 conferidos por `@AssertTrue`), `emissor` (`Plataforma de Cursos`) e
`rejeitar-reuso` (`true`; `false` só nos testes, que entram várias vezes no mesmo intervalo). Mova a
`CifraSegredos` dos pagamentos para `shared/cripto` com o nome da variável no construtor
(`new CifraSegredos(chave, "TOTP_CHAVE")`), usada nas mensagens de erro.

**`SegundoFatorService`**: `exigido(usuario)` (papel `ADMIN`), `ativo(id)`, `iniciarCadastro(usuario)`
(gera ou troca o segredo pendente, cifrado com o dado associado `segundo-fator:<id>`; 409 se já ativo),
`verificar(usuario, codigo, codigoRecuperacao)` (trava a linha; o primeiro código certo ativa e gera 10
códigos de recuperação `xxxxx-xxxxx` de um alfabeto sem `0/o/1/l/i`, guardados só como SHA-256; código
de recuperação vale uma vez, com ou sem hífen e em qualquer caixa), `redefinir(id)` e
`ativarComSegredo(id, segredo)` (só para o seed do e2e).

**Login em duas etapas** — `AuthService.entrar` devolve `sealed interface Entrada permits Sessao,
Desafio`. Para o administrador com a senha certa: audita `SEGUNDO_FATOR_PENDENTE`, **não** zera o
bloqueio, emite um token de uso único `DESAFIO_SEGUNDO_FATOR` (5 minutos; fora do limite de e-mails por
hora: `TipoToken.porEmail()`) e devolve `Desafio(token, cadastro, expiraEm)`, sem sessão nem cookie.
`cadastrarSegundoFator(desafio)` devolve segredo e URI. `concluirSegundoFator(desafio, codigo,
codigoRecuperacao, origem)` confere o desafio (`DesafioInvalidoException`, 401 "O tempo para informar o
código acabou. Entre de novo."), o bloqueio, o código; código errado audita `SEGUNDO_FATOR_INVALIDO`,
conta falha no bloqueio e lança `CodigoInvalidoException` (400, campo `codigo`, sem desfazer a transação);
certo consome o desafio, audita `SUCESSO`, `SEGUNDO_FATOR_CADASTRADO` ou `CODIGO_RECUPERACAO`, zera o
bloqueio e abre a sessão.

**Rotas** (públicas, no `SecurityConfig` com comentário, no `LimitePorIpFilter` e na lista do teste de
rotas):

- `POST /api/auth/login` → `SessaoResponse` ou `DesafioResponse { segundoFator: CODIGO | CADASTRO,
  desafio, expiraEm }` (OpenAPI com `oneOf`).
- `POST /api/auth/segundo-fator/cadastro { desafio }` → `{ segredo, uri }`.
- `POST /api/auth/segundo-fator { desafio, codigo | codigoRecuperacao }` → `SessaoResponse` com o cookie
  de refresh e, só na ativação, `codigosRecuperacao` (`@JsonInclude(NON_EMPTY)`).

**Comando** `redefinir-2fa <email>` (`ComandoRedefinirSegundoFator`, perfil `comando`): apaga o segundo
fator, revoga as sessões e avisa no terminal. O `ComandoCriarAdmin.foiPedido` reconhece os dois
comandos e cada um ignora os argumentos do outro.

**Seed do e2e** — `SeedSegundoFatorE2e` (perfil `e2e`, `@Order(2)`, depois do `SeedE2e`, `@Order(1)`):
ativa o segundo fator do admin do seed com o segredo Base32 de `SEED_E2E_TOTP`.

**Front** (`compartilhado/src/sessao`):

- `ProvedorSessao.entrar` devolve `{ tipo: 'sessao', usuario } | { tipo: 'desafio', desafio }`.
- `SegundoFator.tsx`: no cadastro, chama a rota de cadastro e desenha o QR code com o pacote `qrcode`
  (`toDataURL`; `npm install qrcode` e `npm install -D @types/qrcode`), mostra a chave em grupos de 4;
  campo "Código do aplicativo" (`autoComplete="one-time-code"`), alternativa "Sem o celular? Use um
  código de recuperação", "Voltar"; na ativação, a tela "Guarde os códigos de recuperação" e só no
  "Guardei os códigos, continuar" a sessão é entregue ao cliente da API.
- `segundoFator.ts` (funções puras com teste): `limparCodigo`, `erroCodigo`, `erroCodigoRecuperacao`,
  `segredoEmGrupos`.
- As páginas de entrada do ADM e do aluno mostram o `SegundoFator` quando o login devolve o desafio.

### 2. Usuário do banco só de dados

- `PlataformaCursosApplication`: com o argumento `migrar`, sobe sem servidor HTTP com o perfil `migrar`
  (`application-migrar.yaml`: Flyway ligado, fila desligada), aplica as migrations e termina.
- `db/migration/afterMigrate.sql` (callback do Flyway) com os placeholders `usuario_app` e `senha_app`
  (`spring.flyway.placeholders` de `DB_APP_USERNAME`/`DB_APP_PASSWORD`; em branco não faz nada): cria ou
  atualiza o papel com `LOGIN PASSWORD`, `GRANT CONNECT`, `USAGE` no schema, `REVOKE CREATE`, `SELECT,
  INSERT, UPDATE, DELETE` em todas as tabelas, `USAGE, SELECT` nas sequências e `REVOKE ALL` no
  `flyway_schema_history`.
- `compose.prod.yml`: serviço `migracao` (imagem da API, `command: ["migrar"]`, `restart: "no"`, dono do
  schema e `DB_APP_*`), a API com `depends_on: migracao: service_completed_successfully`; o `x-app` passa a
  usar `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD` com `SPRING_FLYWAY_ENABLED: "false"`. No
  `compose.e2e.yml`, o `migracao` usa a imagem `plataforma-cursos-api:e2e`.
- `gerar-env-servidor.sh` gera `POSTGRES_APP_USER=plataforma_cursos_app` e a senha; o `deploy.sh`
  ganha `completar_env`, que acrescenta ao `.env` `POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD` e
  `TOTP_CHAVE` que ainda não existirem. O ambiente local (`compose.yml`) segue com um usuário só.
- Teste (`UsuarioAplicacaoBancoIT`, placeholder `app_teste` no perfil de teste): o usuário da aplicação
  faz `SELECT`/`INSERT`/`UPDATE`/`DELETE` e recebe `42501` em `CREATE TABLE`, `DROP`, `ALTER`,
  `TRUNCATE` e na leitura do histórico do Flyway.

### 3. Política de senha (`usuario/domain/PoliticaSenha`)

- `validar(senha, minimo, String... pessoais)` devolve a primeira mensagem ou `null`: obrigatória;
  `MINIMO_ALUNO = 10`, `MINIMO_ADMIN = 12`, `MAXIMO = 128`; **comum** ("Esta senha é muito comum ou já
  apareceu em vazamentos. Escolha outra.") pela lista em `src/main/resources/seguranca/senhas-comuns.txt`,
  carregada uma vez (holder preguiçoso) e comparada em minúsculas; **pessoal** ("A senha não pode conter
  o seu nome ou o seu e-mail.") com trechos de 4 ou mais caracteres do valor inteiro, da parte local do
  e-mail e das palavras; **previsível** ("A senha é fácil de adivinhar (sequência ou repetição). Escolha
  outra.") com no máximo 2 caracteres distintos, bloco repetido (`(.{1,4})\1+`) ou trecho de uma
  sequência (dígitos, alfabeto, linhas e diagonais do teclado, nos dois sentidos).
- Gere a lista (senhas de 10 a 128 caracteres de uma lista pública de 1 milhão de senhas vazadas, em
  minúsculas, sem repetição; o `grep -v` final tira 5 entradas que não fazem sentido no projeto):

  ```bash
  mkdir -p backend/src/main/resources/seguranca
  curl -fsSL https://raw.githubusercontent.com/danielmiessler/SecLists/4c0fd59432ab8508edc23b3c880127edb12e34d5/Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt \
    | awk 'length($0)>=10 && length($0)<=128' | tr 'A-Z' 'a-z' | LC_ALL=C sort -u | grep -viE 'p[j]c' \
    > backend/src/main/resources/seguranca/senhas-comuns.txt
  wc -l backend/src/main/resources/seguranca/senhas-comuns.txt   # 117567
  ```

- Aplique no cadastro (antes de qualquer outra regra, com nome e e-mail), na redefinição (confere o
  dono do link sem consumi-lo: `TokenUsoUnicoService.dono`; mínimo pelo papel), no perfil (campo
  `novaSenha`), no `criar-admin` e no seed. Recusa no formato da validação:
  `shared/erro/CampoInvalidoException(campo, mensagem)` (400 com `erros[]`).
- Front: `CampoSenha` com `forca` e `politica={{ minimo: SENHA_MINIMA_ALUNO }}` no cadastro, na
  redefinição e no perfil; dica "Pelo menos 10 caracteres, sem o seu nome ou e-mail e fora das senhas
  mais usadas."; o erro do campo `senha` vindo da API aparece no campo.

### 4. Limite por usuário

`shared/security/LimitePorUsuarioFilter` (Bucket4j + Caffeine, relógio da aplicação), depois da
segurança: só `POST/PUT/PATCH/DELETE` em `/api/` com `JwtAuthenticationToken`; balde por usuário de
`app.limites-usuario.escritas-por-minuto` (120) e, em `POST /api/pagamentos/checkout`, outro de
`checkouts-por-hora` (10); estourou: 429 em ProblemDetail com `Retry-After`. Nos testes de integração os
limites ficam altos (o admin do seed grava a suíte toda) e o filtro tem teste unitário próprio; na stack
de e2e, `APP_LIMITES_USUARIO_ESCRITAS_POR_MINUTO=3000` e `..._CHECKOUTS_POR_HORA=300`.

### 5. Dependências

- `.github/dependabot.yml`: Maven (`/backend`), npm (`/web`), GitHub Actions, Docker (`/backend`,
  `/web`) e docker-compose (`/infra`, `/infra/caddy`), semanal (segunda, 6h de Brasília), PRs para a
  `homolog`, patch e minor agrupados, mensagens `chore(deps)`/`ci(deps)`.
- Pipeline, job do front: `npm audit --omit=dev --audit-level=high` depois do `npm ci`.

### 6. Usuários no ADM

- `Usuario`: `sessoesEncerradasEm`, `bloquear(agora)` (inativa e encerra as sessões),
  `desbloquear(agora)`, `encerrarSessoes(agora)`; `UsuarioRepository.buscar(busca, papel, ativo,
  pageable)` (nome ou e-mail, sem diferenciar maiúsculas); `UsuarioService.listar/bloquear/desbloquear/
  encerrarSessoes`.
- `auth/application/UsuariosAdmService` (o admin não bloqueia a si mesmo: 409) revoga os refresh tokens
  e esquece a conta no `SessaoValidaValidator`: um `OAuth2TokenValidator<Jwt>` no `JwtDecoder` que recusa
  conta inativa, inexistente ou token com `iat` anterior ao encerramento (truncado em segundos), com a
  situação da conta em Caffeine por 30 s.
- `UsuariosAdmController` em `/api/adm/usuarios` (`@PreAuthorize`): `GET` com `busca`, `papel`, `ativo`
  e paginação; `PATCH /{id}/situacao { ativo }`; `POST /{id}/encerrar-sessoes` (204). Resposta
  `UsuarioAdm` com papéis, situação, e-mail confirmado, segundo fator ativo, encerramento e criação.
- Tela `adm/src/paginas/usuarios/UsuariosPagina.tsx` no menu Segurança (antes da Auditoria): filtros,
  tabela, ações "Encerrar as sessões de ..." e "Bloquear/Desbloquear ..." com confirmação, sem ações na
  linha do próprio admin.

### 7. Checkout seguro

- `CampoConfiguracao` ganha `hostsPermitidos`, as fábricas `selecao(...)` e `url(...)` e
  `erroUrl(valor, hosts)`: só `https`, com host e sem credencial na URL; com hosts, só eles. A Hotmart
  declara `HOSTS_CHECKOUT = pay.hotmart.com, sandbox.hotmart.com` e recusa no checkout (409) um endereço
  gravado fora da regra.
- Front: `checkoutSeguro(url)` (só `https:`) antes de sair da plataforma.

### 8. Token fora da barra de endereço

`compartilhado/src/ui/useTokenDaUrl.ts`: lê o `token` uma vez e troca a URL (`replace`) pelos outros
parâmetros (`semToken`, com teste). Usado em `ConfirmarEmailPagina` e `RedefinirSenhaPagina`.

### 9. Compra com confirmação

- `GET /api/pagamentos/ofertas/{id}` (logado): `CheckoutService.resumo` com as mesmas regras do checkout
  (oferta ativa, meio ativo, curso publicado e turma ativa) → `ResumoOferta { id, titulo,
  precoCentavos, moeda, curso, turma, meioPagamento }`.
- `ComprarPagina`: mostra curso, turma, oferta e valor ("Confirmar a compra") e só chama o checkout no
  botão "Ir para o pagamento".

### 10. Varredura das rotas

`RotasProtegidasIT` lista todas as rotas `/api/**` do `RequestMappingHandlerMapping`: sem login, toda
rota fora da lista de públicas responde 401 "Autenticação necessária."; o aluno recebe 403 em
`/api/adm/**`; do Actuator só o health responde; a documentação fica fora.

### 11. Testes de apoio

- `IntegrationTest.entrar`: se o login devolver o desafio, cadastra o aplicativo (guardando o segredo por
  e-mail) e conclui com o código calculado; `codigoTotp(email)` e `concluirSegundoFator(...)`.
- e2e Java: `support/Totp.java` e `E2eConfig.TOTP_SEED`; `Api.loginSemAceite` conclui a segunda etapa.
- Playwright: `support/totp.ts` (`node:crypto`), `TOTP_SEED` no `config.ts`, segunda etapa em
  `entrarPelaApi`, `clienteApi` e `confirmarSegundoFator` (`telas.ts`); `acesso.spec.ts` com o código
  certo e o errado; `usuarios.spec.ts`; `compra.spec.ts` com o "Ir para o pagamento".
- Variáveis: `TOTP_CHAVE` em `application-test.yaml`, `infra/.env.example`, `gerar-env.sh`,
  `gerar-env-servidor.sh`, compose e `infra/e2e.env` (com `POSTGRES_APP_*` e `SEED_E2E_TOTP`).

### 12. Documentação

`docs/operacao.md` (ASVS, segundo fator, `redefinir-2fa`, usuário do banco, restauração), `docs/vps.md`
(variáveis novas e o primeiro login do admin), `README.md` (segredo do admin de exemplo no aplicativo,
`redefinir-2fa`), `web/e2e/README.md`, plano (Fase 18) e capítulos (Parte 12).

## Antes de executar

Fase 17 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/seguranca
```

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: o admin entra com o código do aplicativo (chave `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP`);
  em Segurança → Usuários, bloquear um aluno o desconecta na hora; a compra pede "Ir para o pagamento".
- `docker compose ... exec postgres psql -U plataforma_cursos_app plataforma_cursos -c 'DROP TABLE cursos'`
  é recusado.

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md
git commit -m "feat(auth): exigir segundo fator dos administradores e reduzir privilégios"
git push -u origin feature/seguranca
```

Abra o Pull Request de `feature/seguranca` para `homolog`.

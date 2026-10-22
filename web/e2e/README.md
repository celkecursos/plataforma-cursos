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

| Variável          | Padrão                         | Uso                                       |
| :---------------- | :----------------------------- | :---------------------------------------- |
| `E2E_ALUNO_URL`   | `http://aluno.localhost:8088`  | área do aluno                             |
| `E2E_ADM_URL`     | `http://adm.localhost:8088`    | ADM                                       |
| `E2E_SITE_URL`    | `http://cursos.localhost:8088` | site público                              |
| `E2E_MAILPIT_URL` | `http://localhost:8026`        | e-mails (links de confirmação e de senha) |
| `E2E_SENHA_SEED`  | `123456A#b123`                 | senha das contas do seed                  |
| `E2E_SLOWMO`      | `0`                            | câmera lenta, em ms entre ações           |

## Contas e sessão

- **Seed da stack** (criado pela API no perfil `e2e`): `admin@celke.com.br` e
  `aluno@celke.com.br`, senha `123456A#b123`. São **só leitura**: nenhum teste troca a senha,
  sai ou mexe no perfil delas.
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

- **`acesso.spec.ts`** — entrada do admin, volta para onde ia, senha errada; aluno barrado na tela e na
  API do ADM.
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
- **`compra.spec.ts`** — compra pelo botão do site com a Hotmart interceptada: aprovada (o curso
  aparece), reembolso (o curso some), boleto aguardando, e o "Comprar" sem login que volta para a compra
  depois de entrar.

**Site** (`e2e/site/`)

- **`site.spec.ts`** — home e catálogo com o curso publicado (rascunho não aparece), página do curso com
  meta tags, capa, 404, rotas da API fora das públicas barradas, sitemap e robots, validação de
  certificado e termos publicados. O site é regenerado a cada 3 s na stack de e2e; os testes recarregam
  até a mudança aparecer.

**Apoio** (`e2e/support/`): `config.ts` (endereços e trava), `api.ts` (montar dados pela API),
`sessoes.ts` (fixtures de sessão), `contas.ts` e `mailpit.ts` (contas confirmadas pelo e-mail),
`telas.ts` (login pela tela, termos, escolher opção do `CampoSelect`), `amostras.ts` (arquivos de
amostra), `pagamentos.ts` (Hotmart e notificações).

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

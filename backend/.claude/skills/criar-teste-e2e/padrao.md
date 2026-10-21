# Testes e2e — o padrão do projeto

Os testes e2e tratam o sistema como **caixa-preta**: a stack completa sobe com
`infra/compose.prod.yml` + `infra/compose.e2e.yml` (Caddy, API, worker, PostgreSQL, Garage,
Mailpit) usando **as mesmas imagens que vão para produção**, e os testes chamam o sistema só
por HTTP, como um cliente faria. Não há acesso ao banco, ao código da aplicação nem a mocks.

Ficam no próprio projeto Java, num source set separado (`src/e2e/java`), e só rodam com o
perfil Maven `e2e`.

O e2e é **pouco e caro**: cobre só o que nenhum teste de integração alcança. Regra, papel e
acesso caso a caso já estão nos níveis abaixo.

## O que é jornada e2e

Entra no e2e o que depende de **vários containers juntos** ou da infraestrutura:

- roteamento do Caddy por host (`aluno.localhost`, `adm.localhost`, `media.localhost`) e
  proxy de `/api`;
- cookie de refresh atravessando o proxy (login → refresh → logout);
- e-mail real saindo da API e chegando ao Mailpit (cadastro → confirmação; recuperação de senha);
- URL pré-assinada usada **de fora**, direto no Garage (upload multipart, download);
- worker processando a fila com ffmpeg (upload de vídeo → status "pronto");
- playlist HLS com segmentos assinados (segmento baixa; depois de expirar, não baixa);
- headers de segurança e de cache entregues pelo Caddy.

**Não** entra: matriz de papéis completa, validação de DTO, erros de negócio um a um — isso
é integração ou unitário.

## Onde cada coisa vive

```
src/e2e/java/br/com/celke/cursos/e2e/
  support/                 # configuração e utilitários — nenhum teste aqui
    E2eConfig.java         # URLs base por host, vindas de propriedades do sistema
    Api.java               # RequestSpecification por host, login(), bearer()
    Mailpit.java           # buscar e-mail, extrair link
    Dados.java             # stamp() com prefixo e2e-
    Arquivos.java          # leitura das amostras
  <jornada>/
    <Jornada>E2E.java      # CadastroE2E, SessaoE2E, VideoE2E, ProxyE2E...
src/e2e/resources/
  amostras/video-3s.mp4    # vídeo de amostra de 3 segundos
```

- Nome da classe termina em `E2E`. Um arquivo por jornada.
- Bibliotecas: JUnit 5, REST Assured, Awaitility, AssertJ (escopo de teste, usadas só pelo
  perfil `e2e`).
- Os usuários iniciais vêm do seed do profile `e2e` da API; as credenciais ficam em
  `support/Api.java`.

## Cada classe é independente

- Cada classe cria os próprios dados pela API e faz o próprio login.
- Nenhuma classe depende de outra ou da ordem de execução. Jornada é uma sequência real:
  fica num teste só, com passos comentados.
- Os usuários do seed são **só leitura**: logout, troca de senha e sessões são feitos com um
  usuário criado pela própria classe.
- Identificador único com prefixo `e2e-`: `e2e-<stamp>@teste.celke.uno`.

## Espera assíncrona

E-mail, fila e transcodificação são assíncronos. Use **Awaitility** com limite explícito e
mensagem (`await("vídeo processado").atMost(Duration.ofMinutes(2)).pollInterval(...)`),
nunca `Thread.sleep`. O vídeo de amostra é curto (3 s) para caber no runner do CI.

## O que conferir

- Status **e** corpo, como na integração; erros no formato `ProblemDetail`.
- O que o Caddy acrescenta: `Strict-Transport-Security` (quando HTTPS), `X-Content-Type-Options`,
  `Referrer-Policy`, `Cache-Control` dos assets e do `index.html`.
- Cookie de refresh com `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, sem `Domain`.
- URLs devolvidas pela API apontam para o host de mídia esperado, nunca para o endereço
  interno do container.

## Falha no CI

Quando um e2e falha, o pipeline anexa os relatórios do Failsafe e o `docker compose logs`
de todos os serviços. Leia os logs antes de mexer no teste: a causa costuma estar no
container (variável faltando, healthcheck, rede).

## Verificação

Na raiz do repositório:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml up -d --build --wait
```

Dentro de `backend/`:

```bash
./mvnw verify -Pe2e                          # todas as jornadas
./mvnw verify -Pe2e -Dit.test=VideoE2E       # uma jornada
```

Depois, na raiz: `docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml down -v`.

Se uma jornada falhar por **bug real**, não ajuste o teste para passar — avise com a
jornada, o passo, o esperado e o obtido.

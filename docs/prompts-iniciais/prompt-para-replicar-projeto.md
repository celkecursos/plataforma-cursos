# Prompt para replicar o projeto

Cria a Plataforma de Cursos completa numa execução só: a sessão do Claude Code coordena e cada um dos
30 prompts desta pasta é executado por um subagente com contexto limpo, na ordem da tabela do
[README](README.md).

## Antes de executar

- Uma pasta para o projeto com a pasta `prompts-iniciais` dentro dela. A pasta do projeto pode estar
  em qualquer lugar e pode ser um repositório já clonado e vazio ou uma pasta comum: se não for
  repositório, o prompt cria um.
- Docker em execução, Java 25 e Node instalados: as verificações rodam os testes.
- Um domínio seu: o prompt pergunta o domínio e os subdomínios (site, área do aluno, ADM e mídia) e
  grava as respostas em `docs/configuracao.md`. Nenhum domínio, e-mail ou conta da Celke é usado.
- Contas na Iagente (envio de e-mails, cadastro em https://celke.com.br/page/iagente, com alguns
  e-mails gratuitos) e na Hostinger (VPS KVM 4, em https://celke.com.br/page/hostinger, cupom
  **CELKE**): os prompts mostram quando usar cada uma.
- Opcional: repositório vazio no GitHub configurado como `origin` e GitHub CLI autenticado
  (`gh auth status`). Com eles, cada fase é enviada por push e entra na `homolog` por Pull Request.
  Sem eles, tudo é feito no git local e o envio para o GitHub fica para depois.

Abra o Claude Code na pasta do projeto e digite:

```text
Execute o prompt prompts-iniciais\prompt-para-replicar-projeto.md
```

## Prompt

Você vai construir a Plataforma de Cursos completa executando, **na ordem**, os 30 prompts da pasta
`prompts-iniciais`, sem parar entre eles. Execute tudo automaticamente: só pare nos casos listados em
**Quando parar**.

Nomes usados abaixo:

- **raiz**: o diretório atual, onde o projeto é criado.
- **pasta de prompts**: a pasta que contém este arquivo (normalmente `prompts-iniciais`, na raiz).
  Use sempre caminhos relativos à raiz, nunca um caminho fixo de disco.

A ordem, o arquivo e a branch de cada prompt estão na tabela do `README.md` da pasta de prompts. Leia
o README antes de começar.

Você é o **coordenador**: não execute os prompts no seu próprio contexto, porque o conteúdo somado não
cabe numa sessão.

### Preparação (automática)

1. **Progresso**: leia `progresso.md` na pasta de prompts. Se existir, retome a partir do primeiro
   prompt que não está concluído e pule os passos 2, 4 e 6.
2. **Repositório**: se a raiz não for um repositório git, execute `git init -b main`. Se já for um
   repositório **com commits** e não houver `progresso.md`, pare: é um projeto existente, e os prompts
   gravariam por cima dele.
3. **Pasta de prompts fora do git**: se a pasta de prompts estiver dentro da raiz, acrescente
   `/<pasta de prompts>/` em `.git/info/exclude` (vale só nesta máquina, não é commitado). O primeiro
   prompt copia os prompts para `docs/prompts-iniciais/`, que é a cópia versionada.
4. **Licença**: o primeiro prompt pede o titular e o ano da licença. Use `git config user.name` como
   titular e o ano atual. Só pergunte se `user.name` estiver vazio.
5. **Modo de entrega**:
   - **GitHub**: existe o remoto `origin` e o `gh auth status` está autenticado.
   - **Local**: qualquer outro caso. Os commits e as branches são os mesmos; só o envio muda (ver
     abaixo).

   Se não houver `origin` mas o `gh` estiver autenticado, pegue o usuário com
   `gh api user --jq .login` e pergunte, numa única pergunta, qual repositório recebe o projeto. As
   opções são:
   - criar `<usuário>/plataforma-cursos` privado (`gh repo create <usuário>/plataforma-cursos
     --private`) e configurá-lo como `origin`;
   - informar a URL de outro repositório vazio;
   - seguir no modo local.

   Na pergunta, mostre o nome real do usuário no lugar de `<usuário>`. Se esse repositório já existir
   (`gh repo view <usuário>/plataforma-cursos`), não o use nem o ofereça: sugira
   `<usuário>/plataforma-cursos-2` (ou o próximo número livre). Nunca use um repositório que já tenha
   commits.
6. **Configuração do projeto**: o primeiro prompt pede o domínio, os subdomínios e o dono do
   repositório no GitHub, e grava as respostas em `docs/configuracao.md`. Os prompts usam marcadores
   (`<dominio>`, `<sub-site>`, `<sub-aluno>`, `<sub-adm>`, `<sub-media>`, `<dono-github>`) no lugar
   desses valores. Nunca use os domínios, e-mails ou contas da Celke. Pergunte numa única mensagem:
   - o domínio do usuário (ex.: `meudominio.com.br`);
   - os subdomínios do site, da área do aluno, do ADM e da mídia, com os padrões `cursos`, `aluno`,
     `adm` e `media` (a homologação usa os mesmos com o prefixo `homolog-`).

   O dono do repositório vem do `origin` ou do passo 5; só pergunte se não for possível descobrir.
7. **Ferramentas**: confira `docker info`, `java -version` (25) e `node -v`. Se faltar alguma, pare e
   diga o que instalar.

Informe numa linha o modo escolhido e comece.

### Para cada linha da tabela

1. Dispare **um** subagente (general-purpose), sempre em sequência, nunca em paralelo, com esta
   instrução (troque `<pasta de prompts>` e `<arquivo>` pelos caminhos relativos reais):

   > Leia o arquivo `<pasta de prompts>/<arquivo>.md` e execute a seção `## Prompt` exatamente como
   > está escrita, na raiz deste repositório. Crie os arquivos com **exatamente** o conteúdo indicado,
   > trocando apenas os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`, `<sub-adm>`, `<sub-media>`
   > e `<dono-github>` pelos valores de `docs/configuracao.md`. "Esta pasta de prompts" é `<pasta de prompts>` (ao copiá-la, deixe de fora o `progresso.md`). Se
   > a seção `## Antes de executar` tiver comandos de git (troca de branch), execute-os antes; no modo
   > local, ignore `git pull`. Depois rode todos os comandos da seção `## Verificação` que são
   > executados neste computador e corrija o que falhar até tudo passar. Passos na VPS ou nas
   > configurações do GitHub não são seus: liste-os. Não faça commit. No fim, responda com: arquivos
   > criados ou alterados, resultado de cada verificação, passos manuais pendentes e qualquer desvio
   > do prompt.

   No primeiro prompt, inclua na instrução o titular e o ano da licença, o domínio, os subdomínios e o
   dono do repositório: o subagente não consegue perguntar ao usuário.

2. Leia o relatório. Se alguma verificação falhou ou o subagente desviou do prompt, mande o mesmo
   subagente corrigir (SendMessage) antes de continuar.
3. Execute você mesmo a seção `## Depois de executar` do arquivo (git add e commit), conforme o modo:
   - **GitHub**: faça os pushes indicados. Quando ela pedir o Pull Request para `homolog`, use o `gh`:
     crie o PR, espere os checks de testes e e2e (`gh pr checks --watch`) e faça o merge
     (`gh pr merge --merge`). Se um check falhar, corrija na mesma branch e repita antes de seguir.
   - **Local**: pule os comandos `git push`. No lugar do Pull Request, faça o merge local na
     `homolog` (`git switch homolog` e `git merge --no-ff <branch da fase>`); esse merge substitui o PR
     e é a única gravação permitida na `homolog`.
   - No prompt `f01-09-deploy-automatico.md`, faça também a entrega de `feature/base-projeto` para a
     `homolog` (PR ou merge local): o prompt seguinte cria a branch a partir da `homolog`.
   - No modo GitHub, o job de deploy roda depois do merge e falha enquanto a VPS e os secrets `VPS_*`
     não estão configurados. Isso não impede o próximo prompt: registre como pendência.
4. Atualize `progresso.md` na pasta de prompts com uma linha por prompt: ordem, arquivo, branch,
   commit, status (`concluído` ou `falhou`) e pendências manuais.

### Quando parar

- O passo 2 ou o passo 7 da preparação pediram, ou falta a resposta das perguntas dos passos 5 e 6.
- Uma verificação continua falhando depois de três tentativas de correção.
- Algo exige uma credencial que você não tem (secrets do GitHub, SMTP, acesso à VPS). Não invente
  valores para segredos.

Se o limite de uso interromper a execução, basta abrir uma sessão nova na mesma raiz e executar este
prompt de novo: o `progresso.md` diz de onde continuar.

### Regras

- Não pule prompts, não junte dois prompts num subagente e não "melhore" o conteúdo dos arquivos: o
  tutorial depende do conteúdo exato.
- Nunca faça commit direto em `homolog` ou `main`, exceto os comandos do primeiro prompt e o merge
  local do modo local.
- Ao terminar os 30 prompts, mostre o resumo do `progresso.md`, as pendências manuais e o estado final
  das branches. No modo local, inclua como enviar tudo ao GitHub depois: criar o repositório vazio,
  `git remote add origin <url>` e `git push -u origin main homolog`. Em ambos os modos, liste a
  preparação da VPS, os secrets e as regras de branch do GitHub, conforme `docs/vps.md`.

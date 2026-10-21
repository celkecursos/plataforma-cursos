# Estrutura inicial do repositório

**Fase:** 0 — Preparação
**Branch:** `main`

## Prompt

Estamos começando o projeto **Plataforma de Cursos**: uma plataforma de cursos online com área do
aluno, painel administrativo e site. O backend será em Java 25 com Spring Boot 4.1 e PostgreSQL 18,
os vídeos ficarão num armazenamento S3 (Garage) convertidos para HLS com ffmpeg, o frontend será em
React usando a biblioteca `@celke/ui`, e a infraestrutura usará Docker, Caddy, GitHub Actions e deploy
numa VPS.

Nesta etapa, crie apenas os arquivos iniciais na raiz do repositório. Não crie código, pastas de
backend/frontend nem faça commit.

### 1. `README.md`

Crie com exatamente este conteúdo:

````markdown
# Plataforma de Cursos

Plataforma de cursos online com área do aluno, painel administrativo e site, desenvolvida passo a passo
como tutorial.

- **Backend:** Java 25, Spring Boot 4.1, PostgreSQL 18
- **Vídeos:** armazenamento S3 (Garage) com conversão para HLS via ffmpeg
- **Frontend:** React com [`@celke/ui`](https://github.com/celkecursos/layout-proprio-biblioteca)
- **Infraestrutura:** Docker, Caddy, GitHub Actions, deploy em VPS

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
- [Roteiro do tutorial (capítulos)](docs/capitulos.md)
- [Prompts para recriar o projeto passo a passo](docs/prompts/README.md)
````

### 2. `.gitignore`

Crie com exatamente este conteúdo:

```gitignore
# Segredos
.env
.env.*
!.env.example

# Java / Maven
target/
*.class
*.log

# Node
node_modules/
dist/
coverage/
playwright-report/
test-results/

# IDE / sistema
.idea/
.vscode/
*.iml
.DS_Store
```

## Antes de executar

O repositório já existe no GitHub e está clonado (ou iniciado com `git init -b main` e o `origin`
configurado). Os arquivos `docs/plano-implementacao.md`, `docs/capitulos.md` e a pasta `docs/prompts/`
são copiados do projeto de referência do tutorial.

## Verificação

- `README.md` e `.gitignore` existem na raiz com o conteúdo acima.
- `git status` mostra os arquivos como não rastreados (nenhum commit foi feito).

## Depois de executar

```bash
git add README.md .gitignore docs/
git commit -m "Estrutura inicial e plano de implementação"
git push -u origin main

git switch -c homolog
git push -u origin homolog

git switch -c feature/base-projeto
```

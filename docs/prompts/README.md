# Prompts

Prompts para recriar a Plataforma de Cursos do zero com o Claude Code. Execute-os **na ordem da
tabela**, cada um numa sessão do Claude Code aberta na raiz do repositório. Cada prompt parte do
estado deixado pelos anteriores.

Ao final de cada prompt, confira a seção **Verificação** dele antes de seguir para o próximo.

| Ordem | Prompt | Fase do plano | Branch |
|---|---|---|---|
| 1 | [Estrutura inicial do repositório](f00-01-estrutura-inicial.md) | 0 | `main` |
| 2 | [Esqueleto do backend e padrão de testes](f01-01-esqueleto-backend-e-testes.md) | 1.1 | `feature/base-projeto` |
| 3 | [Ambiente local com Docker](f01-02-ambiente-local.md) | 1.2 | `feature/base-projeto` |
| 4 | [Padrões compartilhados](f01-03-padroes-compartilhados.md) | 1.3 | `feature/base-projeto` |
| 5 | [Usuários e login com JWT e refresh em cookie](f01-04-usuarios-e-login.md) | 1.4 | `feature/base-projeto` |
| 6 | [Autorização e proteções](f01-05-protecoes.md) | 1.5 | `feature/base-projeto` |
| 7 | [Cadastro, confirmação de e-mail e recuperação de senha](f01-06-cadastro-e-recuperacao.md) | 1.6 | `feature/base-projeto` |
| 8 | [Testes e2e e pipeline no GitHub Actions](f01-07-e2e-e-pipeline.md) | 1.7 | `feature/base-projeto` |
| 9 | [Preparar a VPS: produção e homologação](f01-08-vps.md) | 1.8 | `feature/base-projeto` |
| 10 | [Deploy automático com rollback](f01-09-deploy-automatico.md) | 1.9 | `feature/base-projeto` |
| 11 | [Cursos, turmas, módulos, aulas e catálogo](f02-01-cursos-e-catalogo.md) | 2 | `feature/cursos` |
| 12 | [Matrícula e política única de acesso](f03-01-matricula-e-acesso.md) | 3 | `feature/matricula-acesso` |
| 13 | [Upload de vídeo e conversão para HLS](f04-01-upload-de-video.md) | 4 | `feature/upload-video` |
| 14 | [Playlist assinada, progresso e log de acesso](f05-01-playlist-e-progresso.md) | 5 | `feature/entrega-video` |
| 15 | [Arquivos de apoio](f06-01-arquivos-de-apoio.md) | 6 | `feature/arquivos-apoio` |

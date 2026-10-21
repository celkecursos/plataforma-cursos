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

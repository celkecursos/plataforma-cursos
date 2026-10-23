# Prompts

Prompts para recriar a Plataforma de Cursos do zero com o Claude Code. Execute-os **na ordem da
tabela**, cada um numa sessão do Claude Code aberta na raiz do repositório. Cada prompt parte do
estado deixado pelos anteriores.

Ao final de cada prompt, confira a seção **Verificação** dele antes de seguir para o próximo.

| Ordem | Prompt | Fase do plano | Branch |
|---|---|---|---|
| 1 | [Estrutura inicial do repositório](f00-01-estrutura-inicial.md) | 0 | `main` |
| 2 | [Orientações para agentes de IA](f00-02-orientacoes-para-ia.md) | 0 | `feature/base-projeto` |
| 3 | [Esqueleto do backend e padrão de testes](f01-01-esqueleto-backend-e-testes.md) | 1.1 | `feature/base-projeto` |
| 4 | [Ambiente local com Docker](f01-02-ambiente-local.md) | 1.2 | `feature/base-projeto` |
| 5 | [Padrões compartilhados](f01-03-padroes-compartilhados.md) | 1.3 | `feature/base-projeto` |
| 6 | [Usuários e login com JWT e refresh em cookie](f01-04-usuarios-e-login.md) | 1.4 | `feature/base-projeto` |
| 7 | [Autorização e proteções](f01-05-protecoes.md) | 1.5 | `feature/base-projeto` |
| 8 | [Cadastro, confirmação de e-mail e recuperação de senha](f01-06-cadastro-e-recuperacao.md) | 1.6 | `feature/base-projeto` |
| 9 | [Testes e2e e pipeline no GitHub Actions](f01-07-e2e-e-pipeline.md) | 1.7 | `feature/base-projeto` |
| 10 | [Preparar a VPS: produção e homologação](f01-08-vps.md) | 1.8 | `feature/base-projeto` |
| 11 | [Deploy automático com rollback](f01-09-deploy-automatico.md) | 1.9 | `feature/base-projeto` |
| 12 | [Cursos, turmas, módulos, aulas e catálogo](f02-01-cursos-e-catalogo.md) | 2 | `feature/cursos` |
| 13 | [Matrícula e política única de acesso](f03-01-matricula-e-acesso.md) | 3 | `feature/matricula-acesso` |
| 14 | [Upload de vídeo e conversão para HLS](f04-01-upload-de-video.md) | 4 | `feature/upload-video` |
| 15 | [Playlist assinada, progresso e log de acesso](f05-01-playlist-e-progresso.md) | 5 | `feature/entrega-video` |
| 16 | [Arquivos de apoio](f06-01-arquivos-de-apoio.md) | 6 | `feature/arquivos-apoio` |
| 17 | [Fórum por aula e por turma](f07-01-forum.md) | 7 | `feature/forum` |
| 18 | [Certificado em PDF](f08-01-certificado.md) | 8 | `feature/certificado` |
| 19 | [Perfil e termos](f09-01-perfil-e-termos.md) | 9 | `feature/perfil-termos` |
| 20 | [Operação: backup, alertas e segurança](f10-01-operacao.md) | 10 | `feature/operacao` |
| 21 | [Base dos fronts: aluno e ADM](f11-01-base-dos-fronts.md) | 11 | `feature/web-base` |
| 22 | [Telas do ADM](f12-01-telas-do-adm.md) | 12 | `feature/web-adm` |
| 23 | [Área do aluno](f13-01-area-do-aluno.md) | 13 | `feature/web-aluno` |
| 24 | [Site público](f14-01-site-publico.md) | 14 | `feature/site` |
| 25 | [Pagamentos: multi-gateway e Hotmart](f16-01-pagamentos-hotmart.md) | 16 | `feature/pagamentos` |
| 26 | [Auditoria das ações e logs persistentes](f17-01-auditoria-e-logs.md) | 17 | `feature/auditoria` |
| 27 | [Imagens no fórum de dúvidas](f18-01-anexos-no-forum.md) | 18 | `feature/forum-anexos` |
| 28 | [Segurança: segundo fator, menos privilégio e senhas fortes](f19-01-seguranca.md) | 19 | `feature/seguranca` |

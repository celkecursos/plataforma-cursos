---
paths:
  - "backend/src/main/**"
  - "infra/**"
description: Regras de segurança do backend e da infraestrutura — rotas privadas por padrão, segredos, dados sensíveis, auditoria e limites. Carrega ao tocar o código de produção do backend ou a infra.
---

# Segurança do backend

## Rotas

- **Toda rota nasce privada.** Rota pública só existe declarada em `shared/security/SecurityConfig`,
  com o método HTTP e um comentário dizendo por que é pública.
- Rota pública nova entra também na lista de rotas públicas esperadas do teste que varre as rotas;
  sem isso o teste falha — é proposital.
- `/api/adm/**` exige `ADMIN` no `SecurityConfig` **e** `@PreAuthorize` no controller.
- Conteúdo de curso (aula, vídeo, arquivo, fórum, progresso, certificado) passa sempre pela
  `AcessoAulaPolicy`. Nunca confie em id vindo do cliente sem conferir o dono.
- Rota de autenticação ou de conta que recebe POST anônimo entra no limite por IP.

## Dados

- Segredo só em variável de ambiente (`.env` fora do git, secrets do GitHub). Variável nova: no
  `application.yaml` com `${VARIAVEL}`, validada em `@ConfigurationProperties`, no `.env.example`, nos
  scripts que geram o `.env` e nos compose.
- Segredo de terceiros guardado no banco é cifrado (AES-256-GCM com dado associado); token guardado no
  banco é só o hash.
- Campo sensível (hash de senha, segredo, token) leva `@DadoSensivel`: aparece mascarado na trilha.
- Entidade nova entra na auditoria por padrão; `@SemAuditoria` só para log, dado técnico de sessão ou
  mudança frequente demais, com o motivo no Javadoc.
- Nunca registre em log senha, token, segredo, corpo de requisição com dado pessoal ou cookie.

## Respostas

- Erro sempre em `ProblemDetail`; nada de stack trace, SQL ou nome de classe na resposta.
- Login e recuperação de senha respondem igual para e-mail existente e inexistente.
- Upload confere tipo pelo conteúdo e tamanho no servidor, nunca só pela extensão.
- Redirecionamento só para destino conhecido (lista de hosts ou caminho relativo).

## Banco

- Migration compatível com a versão anterior (expandir → contrair): o rollback do deploy não desfaz
  migration.
- Consulta com parâmetro, nunca concatenando texto do usuário.

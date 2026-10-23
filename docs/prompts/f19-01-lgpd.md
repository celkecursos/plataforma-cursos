# LGPD: cópia dos dados, exclusão da conta e política de privacidade

**Fase:** 19
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
  (Fase 19), capítulos (Parte 13), `README.md` (funcionalidade e link do modelo) e `web/e2e/README.md`.

## Antes de executar

Fase 18 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/lgpd
```

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: com um aluno, Perfil → Privacidade → "Baixar meus dados" baixa o JSON; "Excluir minha
  conta" leva à entrada com o aviso, o e-mail chega no Mailpit e o login é recusado.

## Depois de executar

```bash
git add backend/ web/ docs/ README.md
git commit -m "feat(perfil): baixar os dados e excluir a conta pelo perfil"
git push -u origin feature/lgpd
```

Abra o Pull Request de `feature/lgpd` para `homolog`.

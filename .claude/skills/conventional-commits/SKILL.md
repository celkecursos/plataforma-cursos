---
name: conventional-commits
description: Padrão Conventional Commits, em português, para toda mensagem de commit e título de Pull Request. Use sempre que for escrever, revisar ou corrigir uma mensagem de commit, um título de PR ou um commit de merge/revert. Define o formato `<tipo>(<escopo>): <descrição>`, os dez tipos aceitos, a descrição com verbo no infinitivo, o `!` e o `BREAKING CHANGE:` e a proibição de rodapés que creditam ferramentas.
---

# Mensagens de commit — Conventional Commits

```
<tipo>(<escopo opcional>): <descrição>

<corpo opcional>

<rodapés opcionais>
```

## Antes de escrever

- **Descreva o que vai no commit**, não o que você pretendia fazer: leia `git diff --staged --stat`
  (ou o `git status` quando o commit leva a worktree inteira).
- **Reaproveite os escopos do histórico** (`git log --oneline -20`). Escopos deste repositório seguem
  os módulos e áreas: `auth`, `usuario`, `curso`, `matricula`, `video`, `progresso`, `arquivo`,
  `forum`, `certificado`, `perfil`, `pagamento`, `auditoria`, `shared`, `adm`, `aluno`, `site`, `e2e`,
  `infra`, `ci`, `docs`, `prompts`.
- **Em português**, com acentos. Ficam em inglês só os tokens da especificação: o tipo,
  `BREAKING CHANGE:` e os rodapés (`Refs:`, `Closes:`).

## A primeira linha

| Tipo       | Para                                                         |
| ---------- | ------------------------------------------------------------ |
| `feat`     | funcionalidade nova para o usuário ou para a API             |
| `fix`      | correção de defeito                                          |
| `docs`     | só documentação (inclui os prompts do tutorial)              |
| `style`    | formatação, sem mudar significado                            |
| `refactor` | mudança que não corrige defeito nem cria funcionalidade      |
| `perf`     | mudança feita por desempenho                                 |
| `test`     | testes que faltavam ou correção de testes                    |
| `chore`    | build, dependências, configuração, ferramentas               |
| `ci`       | pipeline (GitHub Actions) e scripts dele                     |
| `revert`   | desfazer um commit anterior (SHA no rodapé)                  |

- Nenhum outro tipo (`build` → `chore`; `wip` não chega a branch compartilhada).
- O tipo diz o **efeito** da mudança, não os arquivos tocados.
- **Descrição:** verbo no infinitivo ("este commit vai…": _adicionar_, _corrigir_, _remover_),
  minúscula, sem ponto final, até ~72 caracteres. Não repita o tipo (`fix: corrigir…` gasta a linha).

## Corpo e rodapés

- Corpo separado por linha em branco: explica **por quê** (a restrição, a causa do defeito, a
  alternativa descartada), não narra o diff. Quebre em ~72 colunas.
- Quebra de compatibilidade: `!` antes dos dois-pontos **e** rodapé `BREAKING CHANGE: <o que quem usa
  precisa fazer>` (ex.: variável de ambiente nova obrigatória).
- **Crédito é para pessoas.** Nunca `Co-Authored-By` de IA, "Generated with", "Gerado por", emoji de
  robô ou nome de modelo — nem no assunto, nem no corpo, nem em rodapé.

## Regras que mantêm o histórico útil

- **Uma mudança lógica por commit.** Se a descrição precisa de "e", são dois commits.
- **Código e teste juntos** no mesmo commit; `test:` é para teste de código que já existia.
- Commit de merge gerado pelo git (`Merge pull request…`) fica como está.
- Revert: `revert: <assunto do commit desfeito>` com `Refs: <sha>`.

## Exemplos

```text
feat(auth): exigir segundo fator no login dos administradores
```

```text
fix(pagamento): ignorar notificação de oferta não cadastrada

A Hotmart reenvia a notificação por dias; recusar com erro fazia a fila
de reenvios dela crescer sem que nada pudesse ser feito do nosso lado.

Refs: #42
```

```text
chore(infra)!: ler a chave do segundo fator de TOTP_CHAVE

BREAKING CHANGE: o .env de cada ambiente precisa de TOTP_CHAVE
(openssl rand -base64 32); sem ela a API não sobe.
```

## Antes de commitar

- [ ] Descreve o que está no commit
- [ ] Português; só tipo e rodapés em inglês
- [ ] Um dos dez tipos, fiel ao efeito
- [ ] Escopo do vocabulário do histórico, ou nenhum
- [ ] Infinitivo, minúscula, sem ponto, ~72 caracteres
- [ ] Uma mudança lógica, com os testes dela
- [ ] Nenhum rodapé que credite ferramenta

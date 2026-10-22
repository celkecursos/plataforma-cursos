---
description: Varre o código contra a definição de pronto — o que verify, lint e build não pegam (rota pública sem justificativa, segredo no código, Clock ignorado, fetch solto, teste enfraquecido).
argument-hint: "[caminho, ou vazio = diff da branch contra a homolog]"
---

Alvo: **$ARGUMENTS** — vazio: o diff da branch (`git diff --name-only origin/homolog...HEAD` mais os
arquivos não commitados).

Você é o **revisor da definição de pronto**. A verificação (`./mvnw verify`, lint, testes, build) não
é o seu trabalho; você existe porque parte das regras passa no build e mesmo assim está errada.

## Parte 1 — varredura

Rode restrito ao alvo e relate `arquivo:linha`, o trecho e a regra violada.

```bash
# A) Rotas: permitAll novo precisa de comentário e de entrar na lista do teste de rotas
git diff origin/homolog...HEAD -- '*SecurityConfig.java' | grep -n 'permitAll\|requestMatchers'

# B) Data e hora fora do Clock injetado (regra de negócio)
grep -rnE '(LocalDateTime|LocalDate|Instant|OffsetDateTime|ZonedDateTime)\.now\(\)' <alvo> --include=*.java | grep -v '/test/'

# C) Um módulo usando o repositório de outro
grep -rn 'import br.com.celke.cursos\.[a-z]*\.infra\.' <alvo> --include=*.java

# D) Segredo ou credencial no código, compose ou docs
grep -rnEi '(senha|password|secret|segredo|token|chave)\s*[:=]\s*["'"'"']?[A-Za-z0-9+/=_-]{12,}' <alvo> | grep -v -e '\.env\.example' -e '/test/' -e 'e2e.env'

# E) Log com dado sensível
grep -rnE 'LOG\.(info|warn|error|debug)\(.*(senha|token|segredo|cookie)' <alvo> --include=*.java

# F) Front: fetch fora do cliente da API e token em storage
grep -rn 'fetch(' <alvo> --include=*.ts --include=*.tsx | grep -v 'compartilhado/src/api' | grep -v '\.test\.'
grep -rnE '(localStorage|sessionStorage)\.(setItem|getItem)\(.*(token|sessao)' <alvo> --include=*.ts --include=*.tsx

# G) Teste desligado ou enfraquecido
grep -rnE '@Disabled|\.skip\(|\.only\(|waitForTimeout|Thread\.sleep' <alvo>

# H) Caminho absoluto de uma máquina (não pertence ao repositório)
git diff origin/homolog...HEAD | grep -nE '^\+.*(/home/|C:\\\\Users)'
```

### Como julgar

- `Instant.now()` em teste, em configuração de `Clock` ou em log técnico não é violação.
- Valor de exemplo em `.env.example`, em `infra/e2e.env` (só local) ou em teste não é segredo.
- `Thread.sleep` dentro de utilitário de espera com condição (polling) pode ser legítimo: relate como
  "revisar".

## Parte 2 — o que só a pessoa vê

Liste como checklist para quem revisa, sem fingir que verificou:

- [ ] Matriz de papéis da funcionalidade nova (anônimo, aluno, admin) testada.
- [ ] Telas com estados carregando, vazio e erro; mensagens em português.
- [ ] Navegação por teclado e foco visível nas telas novas.
- [ ] Variável nova documentada (`.env.example`, scripts, `docs/vps.md`).
- [ ] Prompts do tutorial (`docs/prompts` e `docs/prompts-iniciais`) atualizados se a fase mudou.

## Saída

1. **Violações** (arquivo:linha · trecho · regra · como corrigir).
2. **A revisar** (casos ambíguos, com a sua leitura).
3. **Checklist humano.**
4. Varredura limpa? Diga claramente e não invente achado.

Você **não corrige nada** — só relata.

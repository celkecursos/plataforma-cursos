# `.claude/` — orientações para agentes de IA

Regras, skills, agentes e comandos do Claude Code para este repositório. Tudo é Markdown com
frontmatter e fica versionado: quem clona o projeto recebe as mesmas orientações.

## Hierarquia (a de cima vence)

1. `CLAUDE.md` da raiz — regras gerais, branches e a lista do que existe aqui.
2. `backend/CLAUDE.md` — regras do Java (definição de pronto, estrutura, padrões, testes).
3. `.claude/rules/` — detalhes que carregam sozinhos ao tocar os caminhos do `paths` de cada uma.
4. `.claude/skills/` e `backend/.claude/skills/` — processos passo a passo, sob demanda.
5. Documentação oficial das bibliotecas — a versão do `pom.xml`/`package.json` é a que vale; não
   confie na memória para API de biblioteca.

## O que existe

```
.claude/
├── settings.json                 permissões de comandos de leitura e verificação; .env bloqueado
├── rules/
│   ├── seguranca-backend.md      rotas privadas, segredos, dados sensíveis, auditoria (backend/src/main)
│   ├── frontend.md               padrões dos fronts e do site (web/)
│   ├── testes-existentes.md      não enfraquecer teste existente para passar (testes)
│   ├── prompts-do-tutorial.md    prompts atemporais e sem citar outros projetos (docs/prompts*)
│   └── sdd-artefatos.md          contrato das specs e tasks (.sdd/)
├── skills/
│   ├── conventional-commits/     padrão das mensagens de commit (pt-BR)
│   ├── gc/                       /gc: gera a mensagem de commit da worktree
│   ├── pc/                       /pc: commita com a mensagem do /gc e faz o push
│   └── write-specs/              Fase 1 do SDD: especificação agnóstica
├── agents/
│   ├── pm-spec-architect.md      persona da Fase 1 (não se despacha)
│   ├── dev-lead.md               Fase 2: spec → tasks
│   ├── dev-pleno.md              Fase 3: implementa seguindo padrão existente
│   └── dev-senior.md             Fase 3: define padrão novo
└── commands/
    ├── sdd.md                    /sdd <ideia>
    ├── gerar-tasks.md            /gerar-tasks <spec>
    ├── implementar-tasks.md      /implementar-tasks <readme> <spec>
    ├── revisar-dod.md            /revisar-dod [caminho]
    ├── nova-branch.md            /nova-branch <funcionalidade>
    └── abrir-pr.md               /abrir-pr
```

As skills de teste do backend ficam em `backend/.claude/skills/` (`criar-teste-unitario`,
`criar-teste-integracao`, `criar-teste-e2e`).

## Pipeline SDD (spec → tasks → código)

```
/sdd <ideia>                          → .sdd/specs/<slug>.spec.md        ⛔ aprovar a spec
/gerar-tasks <spec>                   → .sdd/tasks/<slug>-v<versão>/     ⛔ revisar as tasks
/implementar-tasks <readme> <spec>    → código + testes                  ⛔ revisar o diff e a verificação
```

Os três portões são humanos. A Fase 1 roda no chat principal (é uma conversa); as Fases 2 e 3
delegam aos agentes.

## Contribuindo

- **Rule**: `rules/<nome>.md` com `paths` (opcional) e `description`.
- **Skill**: `skills/<nome>/SKILL.md` com `name` e uma `description` que diga quando usar.
- **Agente**: `agents/<nome>.md` com `name`, `description`, `model` e `tools`. `tools` é uma lista
  fechada: se o corpo manda consultar algo (documentação na web, outra skill), a ferramenta precisa
  estar na lista.
- **Comando**: `commands/<nome>.md` com `description` e `argument-hint`.

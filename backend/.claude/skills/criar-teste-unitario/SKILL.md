---
name: criar-teste-unitario
description: Cria ou amplia os testes unitários (JUnit 5 + AssertJ + Mockito) de um módulo do backend Java — teste no mesmo pacote, no molde certo (serviço com dependências, classe pura ou DTO). Use quando pedirem "teste unitário", "cobrir o serviço/DTO/policy" ou "/criar-teste-unitario <modulo>".
argument-hint: <modulo> [classe]
---

# Criar teste unitário

Argumento: `$ARGUMENTS` — o módulo (pacote em `br.com.celke.cursos`) e, opcionalmente, a
classe (ex.: `curso CriarCursoService`, `matricula AcessoAulaPolicy`). Sem classe: todos os
serviços de `application/`, as classes de `domain/` com regra e os DTOs de `api/` do módulo,
um por vez.

O **padrão** (moldes, exceções, tabela, o que mockar) está em [padrao.md](padrao.md) — leia
antes de escrever. Esta skill é o **processo**. Comandos rodam dentro de `backend/`.

## 1. Listar os ramos do código

Para cada classe alvo, leia o código e o teste existente (se houver) e liste:

- cada método público e cada **ramo** dele (`if`, `switch`, ternário, `Optional`,
  `try/catch`, retorno antecipado);
- cada `throw` — tipo da exceção, mensagem e o que **não** deve ser gravado/enviado nesse caso;
- cada regra numérica ou de data (percentual, limite, período, expiração) e suas bordas;
- no DTO: cada anotação de validação, cada normalização e os campos opcionais;
- as dependências injetadas — são elas (e só elas) que serão mockadas; `Clock` entra como
  `Clock.fixed`.

Marque o que o teste atual já cobre. Para o mapa real, rode o `./mvnw verify` e abra o
relatório do JaCoCo da classe: as linhas não cobertas apontam os ramos sem caso.

## 2. Propor a tabela de casos — antes de escrever

Apresente ao usuário, por classe, o molde escolhido e a tabela:

| Método / campo | Caso | Entrada (mínima) | Esperado | Já coberto? |
| --- | --- | --- | --- | --- |
| `criar` | slug duplicado | `existsBySlug` → `true` | `SlugDuplicadoException`, `save` não chamado | não |
| `podeAcessar` | um segundo após o fim | `Clock` no fim + 1 s | `false` | não |

Inclua todas as exceções, as bordas obrigatórias (zero, negativo, limite ±1,
arredondamento, lista vazia, instante antes/exato/depois) e o que fica **fora** (rota,
segurança, constraint do banco → integração). Espere o ok do usuário, ou ajuste, antes do
passo 3.

## 3. Escrever no molde certo

- Serviço com dependências → `MockitoExtension`, `@Mock` nas dependências, serviço real,
  `Clock.fixed`, fábricas no topo ou em `support/fixtures/`.
- Classe pura → `new Classe(...)`, regras numéricas e de data em `@ParameterizedTest`.
- DTO → `Validator` único na classe + `camposInvalidos(dto)` + `payloadValido()`.
- Teste já existe? **Amplie** a classe mantendo a organização dela; não crie uma segunda
  classe de teste para a mesma classe.
- Achou um ramo que só se testa espionando a própria classe ou mockando algo interno? Pare
  e avise: provavelmente a regra está no lugar errado — não force o teste.

## 4. Rodar

```bash
./mvnw test -Dtest=<Classe>Test
./mvnw spotless:apply
./mvnw verify
```

Confira no relatório do JaCoCo que as linhas da tabela foram exercitadas; linha descoberta
que sobrar precisa de caso ou de justificativa no resumo. Teste que falha por **bug real** do
código não se ajusta para passar — avise o usuário com o caso, o esperado e o obtido.

Por fim, resuma: classes criadas/ampliadas, casos novos e a cobertura antes/depois de cada
classe.

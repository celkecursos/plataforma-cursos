# Testes unitários — o padrão do projeto

Os unitários rodam no Surefire (`./mvnw test`), **sem Spring, sem banco e sem HTTP**. Cobrem
a **regra**: ramos dos casos de uso, cálculo, políticas de acesso, validação de DTO. O que
depende de rota, segurança, Flyway, PostgreSQL, Garage ou SMTP é teste de integração
(`../criar-teste-integracao/padrao.md`).

## Onde cada coisa vive

- A classe de teste fica no **mesmo pacote** do código, em `src/test/java`, com o mesmo nome
  + `Test`:
  `curso/application/CriarCursoService.java` → `curso/application/CriarCursoServiceTest.java`.
- Uma classe de teste por classe testada; `@Nested` por método quando o método tem mais de
  um caso.
- Nome do método de teste em português, em camelCase, dizendo a **regra**
  (`rejeitaSlugJaCadastrado`), com `@DisplayName("rejeita slug já cadastrado")`. Nunca
  descreva a implementação ("chamaFindBySlug").
- Fábricas de objetos de teste compartilhadas entre módulos vivem em
  `src/test/java/br/com/celke/cursos/support/fixtures/`. Não recrie à mão, em cada teste,
  uma entidade que já tem fábrica.

## Os três moldes — escolha um e copie a referência

| O que é testado | Molde | Referência |
| --- | --- | --- |
| Serviço com dependências (repositório, outro serviço, `StorageService`, `EmailService`) | `@ExtendWith(MockitoExtension.class)`, dependências com `@Mock`, serviço **real** com `@InjectMocks` (ou construtor no `@BeforeEach`); `Clock.fixed(...)` real, não mock | `auth/application/LoginServiceTest.java` |
| Classe pura (política, cálculo, enum com regra) | `new Classe(...)` direto, sem Mockito | `matricula/domain/AcessoAulaPolicyTest.java` |
| DTO (`record` com Bean Validation) | `Validator` de `Validation.buildDefaultValidatorFactory()` criado uma vez na classe; helper local `camposInvalidos(dto)` que devolve o `Set` de nomes dos campos com erro; um `payloadValido()` e variações | `auth/api/CadastroRequestTest.java` |

Se o arquivo de referência ainda não existir, o primeiro teste do molde segue este padrão e
passa a ser a referência.

- Fábrica no topo da classe (`cursoExistente(...)`) ou em `support/fixtures/` em vez de
  objetos montados repetidamente.
- DTO: confira **quais campos** falharam (`assertThat(camposInvalidos(dto)).containsExactlyInAnyOrder("email")`),
  não só que falhou; confira também a normalização (`trim`, minúsculas) quando o DTO a faz.

## Toda exceção é testada

Para **cada** `throw` do código (e cada exceção que ele deixa passar de propósito):

1. O **tipo** — `assertThatThrownBy(() -> ...).isInstanceOf(SlugDuplicadoException.class)` —
   e a **mensagem exata** quando ela é o contrato com o usuário (`.hasMessage("Já existe um curso com este slug.")`).
2. **Nada foi gravado nem enviado** — `verify(cursoRepository, never()).save(any())` (e
   `delete`, `storageService`, `emailService`, `filaService` — o que alteraria estado).

## Regra numérica ou de data: tabela com `@ParameterizedTest`

Cálculo, percentual, arredondamento, limites, paginação e **períodos de data** (matrícula
ativa, token expirado, URL assinada) vão numa tabela — `@CsvSource` quando os valores são
simples, `@MethodSource` quando precisam de objetos — uma linha por caso, com rótulo:

```java
@ParameterizedTest(name = "{0}")
@CsvSource({
    "um segundo antes do início,   2026-03-01T09:59:59Z, false",
    "exatamente no início,         2026-03-01T10:00:00Z, true",
    "exatamente no fim,            2026-06-01T10:00:00Z, true",
    "um segundo depois do fim,     2026-06-01T10:00:01Z, false",
})
void acessoPeloPeriodoDaMatricula(String caso, Instant agora, boolean esperado) {
    var policy = new AcessoAulaPolicy(Clock.fixed(agora, ZoneOffset.UTC));
    assertThat(policy.podeAcessar(matriculaAtiva(), aulaPaga())).isEqualTo(esperado);
}
```

Bordas **obrigatórias**: **zero**, **negativo**, o **limite exato** e um acima/abaixo dele,
**arredondamento** e lista **vazia** quando houver lista. Em data: um instante antes, o
instante exato e um depois de cada limite. Decimal com `BigDecimal` se compara com
`isEqualByComparingTo`.

## Mocke só a fronteira

- Mock **apenas** o que está fora da unidade: repositórios, serviços de outros módulos,
  `StorageService`, `EmailService`, `FilaService`, `PasswordEncoder`, geradores de token.
- O relógio **não** é mockado: use `Clock.fixed(...)`.
- **Nunca** mocke, espione (`spy`) ou substitua método da **própria** classe sob teste:
  monte o retorno das dependências para que o método real siga o ramo desejado.
- Stub devolve o **mínimo** que o ramo precisa. Sem `lenient()`: stub não usado é sinal de
  teste errado (o Mockito estrito acusa).
- `verify(...)` com argumentos só quando o efeito **é** a regra (hash gravado, e-mail
  enfileirado, família de refresh revogada) — não para reproduzir a implementação.
  Para conferir o objeto gravado, use `ArgumentCaptor`.

## Verificação

```bash
./mvnw test -Dtest=CriarCursoServiceTest
./mvnw test
./mvnw spotless:apply
```

O relatório de cobertura do JaCoCo (`target/site/jacoco/index.html`, gerado no `verify`)
aponta os ramos sem caso: linha não coberta precisa de caso ou de justificativa.

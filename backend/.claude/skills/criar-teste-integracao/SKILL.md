---
name: criar-teste-integracao
description: Cria ou amplia os testes de integração (Spring Boot + Testcontainers + MockMvcTester) de um módulo do backend Java — matriz de papéis, matriz de acesso ao conteúdo e fluxos por arquivo. Use quando pedirem "teste de integração", "testar as rotas/endpoints", "matriz de permissão" ou "/criar-teste-integracao <modulo>".
argument-hint: <modulo> [fluxo]
---

# Criar teste de integração de um módulo

Argumento: `$ARGUMENTS` — o módulo (pacote em `br.com.celke.cursos`) e, opcionalmente, o
fluxo.

O **padrão** (infraestrutura compartilhada, independência, matrizes, dado de teste) está em
[padrao.md](padrao.md) — leia antes de escrever. Esta skill é o **processo**. Comandos rodam
dentro de `backend/`.

## 1. Entender o módulo antes de escrever

Leia, nesta ordem:

1. `<modulo>/api/*Controller.java` — cada rota: método, caminho, `@PreAuthorize`, status de
   sucesso, parâmetros. Monte a lista de rotas: é a base da matriz de papéis.
2. `shared/security/SecurityConfig.java` — quais rotas do módulo são públicas e com qual
   método HTTP.
3. `<modulo>/api/` (DTOs) — campos obrigatórios, normalizações e formato de resposta.
4. `<modulo>/application/` — erros de negócio com a mensagem exata, efeitos colaterais
   (e-mail, fila, S3, revogação de tokens) e se a rota passa pela `AcessoAulaPolicy`.
5. As migrations do módulo — constraints (único, FK, check) que só o banco garante.
6. O seed do profile `test` — o que já existe para o módulo (só leitura).

## 2. Propor os arquivos

Antes de escrever, apresente ao usuário a lista, por exemplo:

- `<modulo>/<Modulo>AcessoIT.java` — matriz anônimo / ALUNO / ADMIN de **todas** as rotas.
- `<modulo>/<Modulo>ConteudoIT.java` — matriz de acesso ao conteúdo (ativa, sem matrícula,
  vencida, cancelada, aula gratuita) das rotas que usam a `AcessoAulaPolicy`.
- `<modulo>/<Modulo>CrudIT.java` — ciclo completo num teste: cria, busca pelo `stamp`, erro
  de negócio (409/404), edita, exclui.
- fluxos específicos (`AuthRefreshIT`, `VideoUploadIT`, `ForumModeracaoIT`...).

Diga também o que fica **fora** (regra caso a caso, validação campo a campo → unitário;
jornada com Caddy/worker → e2e). Espere o ok do usuário antes de escrever.

## 3. Completar `support/` quando faltar algo

Só infraestrutura, nenhum teste. O que já existe não é alterado para atender um teste; o que
falta é acrescentado:

- `Containers.java` — novo container como `@Bean`; PostgreSQL com `@ServiceConnection`,
  os demais com um bean `DynamicPropertyRegistrar`.
- `IntegrationTest.java` — `login(email, senha)` que confere 200 e devolve o access token,
  `bearer(token)` (a partir da etapa de autenticação).
- `UsuariosSeed.java` — e-mails e senha dos usuários do seed do profile `test`.
- `Mailpit.java` — `aguardarEmailPara(email)` com Awaitility e extração de links.

Esqueleto de uma classe:

```java
/**
 * Cobre: CRUD de cursos pelo ADM.
 * Não cobre: validação campo a campo (CriarCursoRequestTest).
 * Dado criado: cursos com slug it-<stamp>.
 */
class CursoCrudIT extends IntegrationTest {

    private final String stamp = stamp();
    private String tokenAdmin;

    @BeforeEach
    void entrar() {
        tokenAdmin = login(UsuariosSeed.ADMIN_EMAIL, UsuariosSeed.SENHA);
    }

    @Test
    @DisplayName("admin cria, edita e exclui um curso")
    void cicloCompleto() {
        // status E corpo em cada passo
    }
}
```

## 4. Escrever

- Escreva os arquivos propostos seguindo o padrão.
- Em toda rota com `{id}`, os casos 401/403 usam um id real criado pela classe.
- Confira status **e** corpo, `ProblemDetail` nos erros, ausência de dados sensíveis.
- Não use `@MockitoBean`, `@DirtiesContext` nem `@TestPropertySource`.

## 5. Verificar

```bash
./mvnw verify -Dskip.unit=true -Dit.test='<Modulo>*IT'   # o módulo
./mvnw spotless:apply
./mvnw verify                                           # a suíte inteira
```

O Docker precisa estar rodando. Se um teste falhar, leia a resposta antes de mexer: pode ser
**bug real** da API. Bug real não se contorna no teste — avise o usuário com a rota, o
esperado e o obtido.

Por fim, resuma: arquivos criados, o que cada um cobre e os achados.

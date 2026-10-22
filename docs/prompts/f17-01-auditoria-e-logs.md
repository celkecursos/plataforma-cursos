# Auditoria das ações e logs persistentes

**Fase:** 17
**Branch:** `feature/auditoria`

## Prompt

Vamos registrar quem fez o quê nos dados da plataforma e guardar o log técnico por meses. Siga o
`backend/CLAUDE.md` e os padrões do `web/`. Não faça commit.

Duas entregas:

1. **Trilha de auditoria**: uma tabela `auditoria`, só de inclusão, com usuário, operação, entidade, id,
   antes e depois (sem dados sensíveis), origem (rota ou job), IP, navegador, id da requisição e data,
   gravada em toda mudança feita pela aplicação nos dados (ADM, área do aluno, cadastro, worker), com
   uma tela de consulta no ADM.
2. **Log persistente**: a API e o worker gravam o log também em arquivo, num volume, com retenção.

### 0. Por que sem biblioteca de auditoria

- **Hibernate Envers** cria uma tabela `_aud` por entidade: a consulta "o que aconteceu" fica espalhada e
  não guarda ação, IP nem rota sem código extra.
- **JaVers** guarda snapshots e diffs em tabelas e modelo próprios: dependência grande e trabalho com
  relacionamentos lazy.
- **Spring Data Auditing** (`@CreatedBy`, `@LastModifiedBy`) só guarda quem alterou por último na linha:
  não é trilha.

Use o mesmo mecanismo que o Envers usa por baixo: os eventos `PostInsert`, `PostUpdate` e `PostDelete`
do Hibernate, gravando numa tabela única, na mesma transação da mudança (o que foi desfeito não aparece).
Mudanças feitas direto em SQL (fila, notificações de pagamento, atualizações em lote) não passam por
ali, e está tudo bem: são do sistema e já têm registro próprio.

### 1. Banco (`V16__criar_auditoria.sql`)

`backend/src/main/resources/db/migration/V16__criar_auditoria.sql`:

`````sql
-- Trilha de auditoria: cada inclusão, alteração e exclusão feita pela aplicação, com quem fez, de onde e
-- o antes e o depois dos campos (dados sensíveis mascarados). Sem chave estrangeira: o registro não
-- pode impedir nada nem depender de outra linha continuar existindo.
CREATE TABLE auditoria (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Nulo: ação sem usuário logado (cadastro, worker, agendamento)
    usuario_id   BIGINT,
    operacao     VARCHAR(20)  NOT NULL,
    entidade     VARCHAR(60)  NOT NULL,
    entidade_id  VARCHAR(64),
    antes        JSONB,
    depois       JSONB,
    -- Rota da requisição ("PUT /api/adm/cursos/{id}") ou o job/agendamento que fez a mudança
    origem       VARCHAR(200) NOT NULL,
    ip           VARCHAR(45),
    user_agent   VARCHAR(255),
    request_id   VARCHAR(64),
    em           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_auditoria_operacao CHECK (operacao IN ('INCLUSAO', 'ALTERACAO', 'EXCLUSAO'))
);

CREATE INDEX ix_auditoria_em ON auditoria (em DESC, id DESC);
CREATE INDEX ix_auditoria_entidade ON auditoria (entidade, entidade_id, em DESC);
CREATE INDEX ix_auditoria_usuario ON auditoria (usuario_id, em DESC);

-- Só inclusão: alterar nunca; excluir só pela retenção, que liga app.auditoria_retencao na transação
CREATE FUNCTION auditoria_somente_inclusao() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.auditoria_retencao', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'A auditoria só aceita inclusão (% recusado).', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_auditoria_somente_inclusao
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria_somente_inclusao();
`````

### 2. Captura (`shared/auditoria`)

- `@DadoSensivel` em `Usuario.senhaHash`, `Gateway.segredos` e `Gateway.webhookToken`: a trilha mostra
  que mudaram, com `***`.
- `@SemAuditoria` em `ProgressoAula` (muda a cada poucos segundos), `AcessoVideo`, `AuditoriaLogin`,
  `EventoRecebido` (já são registros), `RefreshToken` e `TokenUsoUnico` (sessão e links de e-mail).
- O `ProcessadorFila` põe o tipo do job no MDC (`ContextoAuditoria.CHAVE_JOB`) durante o processamento
  e tira no `finally`: as mudanças do worker ficam com a origem `job <tipo>` e sem usuário.

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/DadoSensivel.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Campo que nunca vai para a auditoria: a trilha registra que ele mudou, com o valor trocado por
 * {@code "***"} (hash de senha, segredos cifrados, tokens).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DadoSensivel {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/SemAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entidade fora da trilha de auditoria: registros que já são um log (acesso a vídeo, auditoria de
 * login, notificações recebidas), dados técnicos de sessão (refresh token, tokens de e-mail) ou
 * mudanças frequentes demais para valer a trilha (progresso da aula, a cada poucos segundos).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SemAuditoria {}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/OperacaoAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

public enum OperacaoAuditoria {
    INCLUSAO,
    ALTERACAO,
    EXCLUSAO
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/ContextoAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import br.com.celke.cursos.shared.web.IdRequisicao;
import br.com.celke.cursos.shared.web.OrigemRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Quem fez a mudança e de onde: o usuário do token e o job da fila em processamento ({@link #CHAVE_JOB}
 * no MDC), ou a requisição (rota, IP, user-agent, id de correlação), ou "sistema" (agendamentos).
 *
 * @param usuarioId nulo sem usuário logado
 */
public record ContextoAuditoria(Long usuarioId, String origem, String ip, String userAgent, String requestId) {

    /** Chave do MDC com o tipo do job em processamento (posta pelo processador da fila). */
    public static final String CHAVE_JOB = "job";

    static final int TAMANHO_MAXIMO_ORIGEM = 200;

    /** Contexto da thread atual. */
    public static ContextoAuditoria atual() {
        Long usuarioId = usuario(SecurityContextHolder.getContext().getAuthentication());
        String requestId = MDC.get(IdRequisicao.CHAVE_MDC);
        String job = MDC.get(CHAVE_JOB);
        if (job != null) {
            return new ContextoAuditoria(usuarioId, "job " + job, null, null, requestId);
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest request = atributos.getRequest();
            OrigemRequisicao origem = OrigemRequisicao.de(request);
            return new ContextoAuditoria(usuarioId, rota(request), origem.ip(), origem.userAgent(), requestId);
        }
        return new ContextoAuditoria(usuarioId, "sistema", null, null, requestId);
    }

    /** Método e rota com as variáveis ({@code PUT /api/adm/cursos/{id}}); sem rota mapeada, o caminho. */
    static String rota(HttpServletRequest request) {
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String caminho = padrao == null ? request.getRequestURI() : padrao.toString();
        String rota = request.getMethod() + " " + caminho;
        return rota.length() <= TAMANHO_MAXIMO_ORIGEM ? rota : rota.substring(0, TAMANHO_MAXIMO_ORIGEM);
    }

    private static Long usuario(Authentication autenticacao) {
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            try {
                return Long.valueOf(jwt.getToken().getSubject());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/ValoresAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.hibernate.proxy.HibernateProxy;

/**
 * Converte o estado de uma entidade (como o Hibernate entrega no evento) em valores simples para o JSON
 * da trilha: entidade relacionada vira o id, data vira texto ISO-8601, binário vira um marcador e
 * campo {@link DadoSensivel} vira {@code "***"}.
 */
public final class ValoresAuditoria {

    public static final String OCULTO = "***";
    static final String BINARIO = "[binário]";

    private static final Map<Class<?>, Set<String>> SENSIVEIS = new ConcurrentHashMap<>();

    private ValoresAuditoria() {}

    /**
     * Campos escolhidos do estado.
     *
     * @param indices posições a incluir; nulo, todas
     */
    public static Map<String, Object> campos(Class<?> tipo, String[] nomes, Object[] estado, int[] indices) {
        if (estado == null) {
            return null;
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        if (indices == null) {
            for (int i = 0; i < nomes.length; i++) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        } else {
            for (int i : indices) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        }
        return campos;
    }

    static Object valor(Class<?> tipo, String campo, Object valor) {
        if (valor == null) {
            return null;
        }
        if (sensiveis(tipo).contains(campo)) {
            return OCULTO;
        }
        return simples(valor);
    }

    static Object simples(Object valor) {
        if (valor == null || valor instanceof String || valor instanceof Number || valor instanceof Boolean) {
            return valor;
        }
        if (valor instanceof Enum<?> constante) {
            return constante.name();
        }
        if (valor instanceof TemporalAccessor || valor instanceof UUID) {
            return valor.toString();
        }
        if (valor instanceof byte[]) {
            return BINARIO;
        }
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream().map(ValoresAuditoria::simples).toList();
        }
        if (valor instanceof HibernateProxy proxy) {
            return simples(proxy.getHibernateLazyInitializer().getIdentifier());
        }
        if (valor.getClass().isAnnotationPresent(Entity.class)) {
            return idDe(valor);
        }
        return valor.toString();
    }

    /** Campos {@link DadoSensivel} da classe e das superclasses (lidos uma vez por classe). */
    static Set<String> sensiveis(Class<?> tipo) {
        return SENSIVEIS.computeIfAbsent(tipo, classe -> {
            java.util.stream.Stream.Builder<Field> campos = java.util.stream.Stream.builder();
            for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field campo : c.getDeclaredFields()) {
                    campos.add(campo);
                }
            }
            return campos.build()
                    .filter(campo -> campo.isAnnotationPresent(DadoSensivel.class))
                    .map(Field::getName)
                    .collect(Collectors.toUnmodifiableSet());
        });
    }

    private static Object idDe(Object entidade) {
        try {
            Method getId = entidade.getClass().getMethod("getId");
            return simples(getId.invoke(entidade));
        } catch (ReflectiveOperationException e) {
            return entidade.getClass().getSimpleName();
        }
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/TrilhaAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava uma linha da trilha de auditoria. Usa a conexão da transação em andamento: se a mudança é
 * desfeita, o registro também é.
 */
@Component
public class TrilhaAuditoria {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public TrilhaAuditoria(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void registrar(
            OperacaoAuditoria operacao,
            String entidade,
            String entidadeId,
            Map<String, Object> antes,
            Map<String, Object> depois,
            ContextoAuditoria contexto) {
        jdbc.sql("""
                        INSERT INTO auditoria (usuario_id, operacao, entidade, entidade_id, antes, depois, origem, ip,
                                               user_agent, request_id, em)
                        VALUES (:usuario, :operacao, :entidade, :entidadeId, CAST(:antes AS JSONB), CAST(:depois AS JSONB),
                                :origem, :ip, :userAgent, :requestId, :em)
                        """)
                .param("usuario", contexto.usuarioId())
                .param("operacao", operacao.name())
                .param("entidade", entidade)
                .param("entidadeId", entidadeId)
                .param("antes", antes == null ? null : jsonMapper.writeValueAsString(antes))
                .param("depois", depois == null ? null : jsonMapper.writeValueAsString(depois))
                .param("origem", contexto.origem())
                .param("ip", contexto.ip())
                .param("userAgent", contexto.userAgent())
                .param("requestId", contexto.requestId())
                .param("em", utc(clock.instant()))
                .update();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/OuvinteAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import java.util.Map;
import org.hibernate.Hibernate;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

/**
 * Ouve as inclusões, alterações e exclusões que o Hibernate grava e registra cada uma na trilha de
 * auditoria, dentro da mesma transação. Na alteração, só os campos que mudaram, com o antes e o
 * depois. Entidades {@link SemAuditoria} ficam de fora. Mudanças feitas direto em SQL (fila,
 * notificações de pagamento, atualizações em lote) não passam por aqui.
 */
@Component
public class OuvinteAuditoria implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final TrilhaAuditoria trilha;

    public OuvinteAuditoria(TrilhaAuditoria trilha) {
        this.trilha = trilha;
    }

    @Override
    public void onPostInsert(PostInsertEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.INCLUSAO,
                    tipo,
                    evento.getId(),
                    null,
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getState(), null));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        int[] alterados = evento.getDirtyProperties();
        if (!auditada(tipo) || (alterados != null && alterados.length == 0)) {
            return;
        }
        String[] nomes = nomes(evento.getPersister());
        registrar(
                OperacaoAuditoria.ALTERACAO,
                tipo,
                evento.getId(),
                ValoresAuditoria.campos(tipo, nomes, evento.getOldState(), alterados),
                ValoresAuditoria.campos(tipo, nomes, evento.getState(), alterados));
    }

    @Override
    public void onPostDelete(PostDeleteEvent evento) {
        Class<?> tipo = Hibernate.getClass(evento.getEntity());
        if (auditada(tipo)) {
            registrar(
                    OperacaoAuditoria.EXCLUSAO,
                    tipo,
                    evento.getId(),
                    ValoresAuditoria.campos(tipo, nomes(evento.getPersister()), evento.getDeletedState(), null),
                    null);
        }
    }

    /** O registro entra na transação da mudança; nada fica pendente para depois do commit. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    static boolean auditada(Class<?> tipo) {
        return !tipo.isAnnotationPresent(SemAuditoria.class);
    }

    private void registrar(
            OperacaoAuditoria operacao,
            Class<?> tipo,
            Object id,
            Map<String, Object> antes,
            Map<String, Object> depois) {
        trilha.registrar(
                operacao,
                tipo.getSimpleName(),
                id == null ? null : String.valueOf(ValoresAuditoria.simples(id)),
                antes,
                depois,
                ContextoAuditoria.atual());
    }

    private static String[] nomes(EntityPersister persister) {
        return persister.getPropertyNames();
    }
}
`````

`backend/src/main/java/br/com/celke/cursos/shared/auditoria/RegistroOuvinteAuditoria.java`:

`````java
package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.stereotype.Component;

/** Liga o {@link OuvinteAuditoria} aos eventos do Hibernate (API e worker). */
@Component
public class RegistroOuvinteAuditoria {

    public RegistroOuvinteAuditoria(EntityManagerFactory emf, OuvinteAuditoria ouvinte) {
        EventListenerRegistry registro =
                emf.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registro.appendListeners(EventType.POST_INSERT, ouvinte);
        registro.appendListeners(EventType.POST_UPDATE, ouvinte);
        registro.appendListeners(EventType.POST_DELETE, ouvinte);
    }
}
`````

### 3. Consulta (módulo `auditoria`)

- `RegistroAuditoria`: entidade `@Immutable` e `@SemAuditoria` só para leitura da tabela (`antes` e
  `depois` como JSON).
- `RegistroAuditoriaRepository` com `JpaSpecificationExecutor` e `entidades()` (`select distinct`,
  em ordem).
- `RetencaoAuditoriaJdbc.apagarAnteriores(limite)`: `@Transactional`, liga
  `set_config('app.auditoria_retencao', 'on', true)` e apaga o que tem `em` anterior ao limite.
- `AuditoriaProperties` (`app.auditoria.retencao`, padrão `P1826D`: 5 anos, prazo de prescrição das
  ações do consumidor) e `LimpezaAuditoria` (só no worker, domingo às 5h de Brasília).
- `AuditoriaService`: lista com filtros opcionais (usuário por id ou e-mail — e-mail sem conta devolve
  vazio —, entidade, id, operação, período com `de` inclusivo e `ate` exclusivo), mais recentes primeiro
  (`em`, `id`), com nome e e-mail do usuário pelo `UsuarioService.buscarPorIds`; busca por id (404
  "Registro não encontrado."); entidades; retenção.
- `AuditoriaController` (`ADMIN`): `GET /api/adm/auditoria` (página), `GET /api/adm/auditoria/entidades`,
  `GET /api/adm/auditoria/{id}`. Resposta: `id, em, operacao, entidade, entidadeId, usuarioId,
  usuarioNome, usuarioEmail, origem, ip, userAgent, requestId, antes, depois` (os dois últimos em texto
  JSON).

### 4. Log persistente

- `application.yaml`, em `logging`, a retenção do arquivo (só vale quando `LOGGING_FILE_NAME` está
  definida):

  `````yaml
  logback:
    rollingpolicy:
      file-name-pattern: "${LOG_FILE}.%d{yyyy-MM-dd,America/Sao_Paulo}.%i.gz"
      max-file-size: 50MB
      max-history: 180
      total-size-cap: 2GB
  `````

  e, em `app`, `auditoria.retencao: P1826D`.
- `backend/Dockerfile`: no estágio `runtime`, crie `/var/log/plataforma-cursos` com dono `app` (o volume
  novo herda o dono).
- `infra/compose.prod.yml`: `LOGGING_STRUCTURED_FORMAT_FILE: ecs` no bloco `x-app`;
  `LOGGING_FILE_NAME: /var/log/plataforma-cursos/api.log` na API e `.../worker.log` no worker; o volume
  `logs` montado em `/var/log/plataforma-cursos` nos dois e declarado em `volumes`. O log do Docker
  continua como estava.

### 5. Frontend (ADM)

- Menu **Segurança → Auditoria** (`/auditoria`, ícone `shield-check`).
- Filtros: entidade (do `/entidades`), operação, id e e-mail do usuário; tabela com quando, operação
  (Inclusão, Alteração, Exclusão), registro (`Curso #12`), usuário (e-mail; sem usuário, "Sistema
  (worker)" para origem `job ...` ou "Sistema ou visitante") e origem.
- Clique na linha abre o diálogo com usuário, e-mail, origem, IP, id da requisição, navegador e a tabela
  campo | antes | depois (`mudancas(antes, depois)`: campo ausente vira "—", nulo vira "vazio").

### 6. Testes

- **Unitários**: `ValoresAuditoria` (valores simples, entidade pelo id, sensíveis como `***`, campos
  por índice), `ContextoAuditoria` (rota com variáveis, truncada em 200; job pelo MDC; sistema);
  `FusoHorarioLogs` já existe. No front, `mudancas`.
- **Integração** (`AuditoriaIT`): ADM cria e altera um curso (inclusão completa; alteração só com o que
  mudou; usuário, rota `PUT /api/adm/cursos/{id}`, IP, id da requisição; filtros por usuário, e-mail,
  operação e período); aluno troca nome e senha (hash como `***`); segredo do gateway como `***`;
  operação recusada (slug repetido, 409) sem rastro; venda processada pelo worker com origem
  `job pagamento.processar-evento` e sem usuário; progresso fora; `UPDATE` e `DELETE` recusados pelo
  banco; retenção apaga só o antigo; matriz de papéis. Compare o JSON sem espaços (o `jsonb` volta com
  espaço depois dos dois-pontos).
- **Playwright**: o curso criado pela API aparece na auditoria filtrada pela entidade e pelo id, com o
  e-mail do admin, a rota e os campos no diálogo.

### 7. Documentação

- `docs/operacao.md`: seção 4 (logs persistentes: onde ficam, retenção, como ler) e 4.1 (auditoria).
- `docs/vps.md`: nada a configurar; o deploy cria o volume.
- `docs/plano-implementacao.md` (Fase 17) e `docs/capitulos.md` (Cap. 41).

## Verificação

- `./mvnw verify` e as jornadas `-Pe2e`; no `web/`, format, lint, typecheck, test, build e `npm run e2e`.
- Na stack de e2e: alterar um curso pelo ADM e ver o registro em Segurança → Auditoria; `docker compose
  ... exec api ls /var/log/plataforma-cursos` mostra o `api.log`.
- `UPDATE auditoria ...` direto no banco é recusado.

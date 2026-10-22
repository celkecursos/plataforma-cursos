package br.com.celke.cursos.shared.fila;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.celke.cursos.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Cobre: enfileirar, reservar, concluir e falhar no PostgreSQL real — backoff, tentativas
 * esgotadas, lease vencido e reserva concorrente sem duplicidade.
 * Não cobre: cálculo do backoff (BackoffTest) e despacho para manipuladores (ProcessadorFilaTest).
 * Dado criado: jobs com tipo it-<stamp> (cada teste usa um tipo próprio).
 */
class FilaServiceIT extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-10T12:00:00Z");

    @Autowired
    private FilaService fila;

    @Autowired
    private JdbcClient jdbc;

    @Test
    @DisplayName("reserva o job, entrega o payload e conclui")
    void cicloCompleto() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of("para", "ana@teste.celke.uno"), T0);

        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        assertThat(job.id()).isEqualTo(id);
        assertThat(job.tentativas()).isEqualTo(1);
        assertThat(job.maxTentativas()).isEqualTo(5);
        assertThat(job.payload()).isEqualTo("{\"para\": \"ana@teste.celke.uno\"}");
        assertThat(status(id)).isEqualTo(StatusJob.PROCESSANDO);

        fila.concluir(job, T0);

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("só reserva jobs dos tipos pedidos")
    void filtraPorTipo() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(stamp()), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(), T0)).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0)).isPresent();
    }

    @Test
    @DisplayName("não reserva job agendado para o futuro")
    void respeitaHorario() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);

        assertThat(fila.reservar(Set.of(tipo), T0.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("falha volta o job para a fila depois do backoff, com o erro registrado")
    void falhaComBackoff() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falhar(job, "SMTP fora do ar", T0);

        assertThat(status(id)).isEqualTo(StatusJob.PENDENTE);
        assertThat(ultimoErro(id)).isEqualTo("SMTP fora do ar");
        // backoff-base de 30 s: um segundo antes não reserva, no instante exato reserva
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(29))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plusSeconds(30)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("na última tentativa a falha leva o job para ERRO")
    void tentativasEsgotadas() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Instant agora = T0;
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            Job job = fila.reservar(Set.of(tipo), agora).orElseThrow();
            assertThat(job.tentativas()).isEqualTo(tentativa);
            fila.falhar(job, "falha " + tentativa, agora);
            agora = agora.plus(Duration.ofDays(1));
        }

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(ultimoErro(id)).isEqualTo("falha 5");
        assertThat(fila.reservar(Set.of(tipo), agora)).isEmpty();
    }

    @Test
    @DisplayName("falha definitiva leva o job direto para ERRO")
    void falhaDefinitiva() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        fila.falharDefinitivo(job, "template inexistente", T0);

        assertThat(status(id)).isEqualTo(StatusJob.ERRO);
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("job com lease vencido volta a ser reservado (o worker anterior caiu)")
    void leaseVencido() {
        String tipo = stamp();
        fila.enfileirar(tipo, Map.of(), T0);
        fila.reservar(Set.of(tipo), T0).orElseThrow();

        // lease de 10 min: ainda com o worker anterior no instante exato, livre um segundo depois
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)))).isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.tentativas()).isEqualTo(2));
    }

    @Test
    @DisplayName("renovar o lease mantém o job com o worker; sem renovação ele volta para a fila")
    void renovacaoDoLease() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();

        // Renovado aos 9 min: o lease vai até os 19 min
        fila.renovar(job, T0.plus(Duration.ofMinutes(9)));
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(10)).plusSeconds(1)))
                .isEmpty();
        assertThat(fila.reservar(Set.of(tipo), T0.plus(Duration.ofMinutes(19)).plusSeconds(1)))
                .hasValueSatisfying(j -> assertThat(j.id()).isEqualTo(id));
    }

    @Test
    @DisplayName("renovar um job já concluído não o devolve para processamento")
    void renovacaoDeJobConcluido() {
        String tipo = stamp();
        long id = fila.enfileirar(tipo, Map.of(), T0);
        Job job = fila.reservar(Set.of(tipo), T0).orElseThrow();
        fila.concluir(job, T0);

        fila.renovar(job, T0.plusSeconds(30));

        assertThat(status(id)).isEqualTo(StatusJob.CONCLUIDO);
    }

    @Test
    @DisplayName("falhas desde um instante: só os jobs em ERRO depois dele e fora do tipo excluído")
    void falhasDesde() {
        String tipo = stamp();
        String excluido = stamp();
        Job antigo = reservar(tipo, T0);
        fila.falharDefinitivo(antigo, "antes", T0);
        Job novo = reservar(tipo, T0);
        fila.falharDefinitivo(novo, "depois", T0.plusSeconds(60));
        fila.falharDefinitivo(reservar(excluido, T0), "e-mail", T0.plusSeconds(60));
        fila.concluir(reservar(tipo, T0), T0.plusSeconds(60));

        assertThat(fila.falhasDesde(T0.plusSeconds(30), excluido))
                .filteredOn(f -> f.tipo().equals(tipo) || f.tipo().equals(excluido))
                .extracting(FilaService.FalhaJob::id, FilaService.FalhaJob::erro)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(novo.id(), "depois"));
    }

    private Job reservar(String tipo, Instant agora) {
        fila.enfileirar(tipo, Map.of(), agora);
        return fila.reservar(Set.of(tipo), agora).orElseThrow();
    }

    @Test
    @DisplayName("consumidores concorrentes nunca reservam o mesmo job")
    void reservaConcorrente() throws Exception {
        String tipo = stamp();
        int total = 40;
        for (int i = 0; i < total; i++) {
            fila.enfileirar(tipo, Map.of("i", i), T0);
        }
        List<Long> reservados = Collections.synchronizedList(new ArrayList<>());
        Callable<Void> consumidor = () -> {
            while (true) {
                var job = fila.reservar(Set.of(tipo), T0);
                if (job.isEmpty()) {
                    return null;
                }
                reservados.add(job.get().id());
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (var futuro : executor.invokeAll(List.of(consumidor, consumidor, consumidor, consumidor))) {
                futuro.get();
            }
        }

        assertThat(reservados).hasSize(total).doesNotHaveDuplicates();
    }

    private StatusJob status(long id) {
        return StatusJob.valueOf(jdbc.sql("SELECT status FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single());
    }

    private String ultimoErro(long id) {
        return jdbc.sql("SELECT ultimo_erro FROM jobs WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}

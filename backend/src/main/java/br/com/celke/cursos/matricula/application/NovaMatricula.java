package br.com.celke.cursos.matricula.application;

import br.com.celke.cursos.matricula.domain.FormaPagamento;
import java.time.Instant;

/** Dados da matrícula lançada pelo ADM; sem início, começa agora; sem fim, não vence. */
public record NovaMatricula(
        String email, Instant inicio, Instant fim, FormaPagamento formaPagamento, String transacao) {}

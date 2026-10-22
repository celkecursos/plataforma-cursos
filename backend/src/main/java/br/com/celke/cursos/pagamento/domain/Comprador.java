package br.com.celke.cursos.pagamento.domain;

/** Quem está comprando, para preencher o checkout. */
public record Comprador(String email, String nome) {}

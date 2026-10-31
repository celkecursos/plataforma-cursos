package br.com.celke.cursos.shared.erro;

/** Um campo inválido na lista {@code erros} do ProblemDetail de validação. */
public record ErroCampo(String campo, String mensagem) {}

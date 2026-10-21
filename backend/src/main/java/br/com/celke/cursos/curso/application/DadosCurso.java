package br.com.celke.cursos.curso.application;

/** Dados editáveis do curso; sem slug, ele é gerado a partir do título. */
public record DadosCurso(String titulo, String slug, String resumo, String descricao) {}

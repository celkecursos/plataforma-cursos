package br.com.celke.cursos.curso.domain;

/** Item com posição dentro do pai (módulo na turma, aula no módulo), começando em 1. */
public interface Ordenavel {

    Long getId();

    int getOrdem();

    void definirOrdem(int ordem);
}

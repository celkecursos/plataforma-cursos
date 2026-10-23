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

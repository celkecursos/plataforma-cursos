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

package com.devboard.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita a taxa de chamadas ao método anotado. {@code key} é uma expressão SpEL avaliada
 * sobre os parâmetros do método (ex.: "#request.email"), combinada ao nome do método para
 * formar a chave real do contador.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    String key();

    int limit();

    /** Janela em segundos. */
    long window();
}

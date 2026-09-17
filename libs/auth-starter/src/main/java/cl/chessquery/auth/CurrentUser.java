package cl.chessquery.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta el {@link UserPrincipal} del request en un handler:
 * {@code public Foo create(@CurrentUser UserPrincipal user, @RequestBody ...)}.
 * La identidad nunca se toma del body ni de query params.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}

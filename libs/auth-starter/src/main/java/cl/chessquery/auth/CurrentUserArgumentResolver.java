package cl.chessquery.auth;

import cl.chessquery.common.api.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Construye el {@link UserPrincipal} a partir del JWT validado y del resolver de identidad. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final PlayerIdentityResolver identityResolver;
    private final String rolesClaim;

    public CurrentUserArgumentResolver(PlayerIdentityResolver identityResolver, String rolesClaim) {
        this.identityResolver = identityResolver;
        this.rolesClaim = rolesClaim;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Se requiere un token válido");
        }
        return fromJwt(token.getToken());
    }

    UserPrincipal fromJwt(Jwt jwt) {
        String subject = jwt.getSubject();
        PlayerIdentityResolver.ResolvedIdentity id = identityResolver.resolve(subject, jwt.getClaims());
        List<String> claimRoles = jwt.getClaimAsStringList(rolesClaim);
        Set<String> roles = claimRoles == null ? Set.of()
                : claimRoles.stream().map(String::toUpperCase).collect(Collectors.toUnmodifiableSet());
        return new UserPrincipal(id.playerId(), subject, jwt.getClaimAsString("email"), id.organizationId(), roles);
    }
}

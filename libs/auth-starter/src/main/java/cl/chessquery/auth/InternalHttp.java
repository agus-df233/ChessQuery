package cl.chessquery.auth;

import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP servicio→servicio hacia {@code users}: agrega {@code X-Internal-Token} y, si está configurado,
 * {@code X-Origin-Verify}. En el Learner Lab no hay Cloud Map, así que tournament y game llaman a users a través
 * del ALB, que solo atiende requests con esa cabecera de origen (ADR-0002).
 */
public final class InternalHttp {

    public static final String ORIGIN_HEADER = "X-Origin-Verify";

    private InternalHttp() {}

    public static RestClient usersClient(AuthProperties props) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.usersUrl())
                .defaultHeader(InternalTokenFilter.HEADER, props.internalToken());
        if (!props.originSecret().isBlank()) builder.defaultHeader(ORIGIN_HEADER, props.originSecret());
        return builder.build();
    }
}

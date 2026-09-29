package cl.chessquery.users.identity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityClaimsTest {

    @Test
    void emailVerificadoPorIssuerConfiableOClaimExplicito() {
        TrustedEmailIssuers trusted = new TrustedEmailIssuers("https://t.ciamlogin.com/t/v2.0/, http://otro");
        assertThat(trusted.emailIsVerified(Map.of("iss", "https://t.ciamlogin.com/t/v2.0"))).isTrue();
        assertThat(trusted.emailIsVerified(Map.of("iss", "https://malo"))).isFalse();
        assertThat(trusted.emailIsVerified(Map.of("iss", "https://malo", "email_verified", true))).isTrue();
        assertThat(trusted.emailIsVerified(Map.of("email_verified", "true"))).isTrue();
        assertThat(trusted.emailIsVerified(Map.of())).isFalse();
        assertThat(new TrustedEmailIssuers("").emailIsVerified(Map.of("iss", "x"))).isFalse();
    }

    @Test
    void nombreDesdeClaimsConRespaldo() {
        assertThat(IdentityService.Names.from(Map.of("given_name", "Ana", "family_name", "Soto")))
                .isEqualTo(new IdentityService.Names("Ana", "Soto"));
        assertThat(IdentityService.Names.from(Map.of("name", "María José Pérez Soto")))
                .isEqualTo(new IdentityService.Names("María", "José Pérez Soto"));
        assertThat(IdentityService.Names.from(Map.of("given_name", "Ana"))).isEqualTo(new IdentityService.Names("Ana", ""));
        assertThat(IdentityService.Names.from(Map.of("name", "Cher"))).isEqualTo(new IdentityService.Names("Cher", ""));
        assertThat(IdentityService.Names.from(Map.of())).isEqualTo(new IdentityService.Names("Jugador", ""));
    }
}

package cl.chessquery.users.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato con el ETL (Python): el hash del RUT debe dar exactamente lo mismo en ambos lados, o el match por
 * RUT deja de funcionar sin que nadie lo note. Mismo vector que etl/tests/test_federation_privacy.py.
 */
class IdentifierHasherContractTest {

    private static final String PEPPER = "test-pepper-0123456789";
    private static final String RUT_HASH_11111111_1 = "7d7edbece292f36b2b80583906aca532834c790b51922835aec58cca59ad4e96";

    @Test
    void rutHashCoincideConElEtl() {
        IdentifierHasher hasher = new IdentifierHasher(PEPPER);
        assertThat(hasher.rut("11.111.111-1")).isEqualTo(RUT_HASH_11111111_1);
        assertThat(hasher.rut("11111111-1")).isEqualTo(RUT_HASH_11111111_1);
    }
}

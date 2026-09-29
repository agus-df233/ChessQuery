import pytest

from chessquery_etl.federation.privacy import Pepper, normalize_rut, rut_is_valid
from tests.federation_fakes import PEPPER

# Mismo vector que IdentifierHasherContractTest.java: si cambia uno, cambia el otro (Python y Java deben coincidir).
RUT_HASH_11111111_1 = "7d7edbece292f36b2b80583906aca532834c790b51922835aec58cca59ad4e96"


def test_hash_coincide_con_java():
    assert Pepper(PEPPER).rut_hash("11.111.111-1") == RUT_HASH_11111111_1
    assert Pepper(PEPPER).rut_hash("11111111-1") == RUT_HASH_11111111_1  # formato no importa


@pytest.mark.parametrize("rut,ok", [("12.345.678-5", True), ("11.111.111-1", True), ("1-9", True),
                                    ("12.345.678-k", False), ("20.000.000-K", False), ("abc", False), ("", False)])
def test_digito_verificador(rut, ok):
    assert rut_is_valid(rut) is ok


def test_normaliza_y_dv_k_y_cero():
    assert normalize_rut(" 12.345.678-k ") == "12345678K"
    assert rut_is_valid("10.000.013-K")  # DV K
    assert rut_is_valid("11.000.003-0")  # DV 0


def test_pepper_desde_env_o_ssm():
    assert Pepper.from_env({"PRIVACY_PEPPER": PEPPER}).rut_hash("1-9")

    class FakeSsm:
        def get_parameter(self, Name, WithDecryption):  # noqa: N803
            assert Name == "/chessquery-academy/privacy-pepper" and WithDecryption
            return {"Parameter": {"Value": PEPPER}}

    env = {"PRIVACY_PEPPER_PARAM": "/chessquery-academy/privacy-pepper"}
    assert Pepper.from_env(env, ssm_client=FakeSsm()).rut_hash("11.111.111-1") == RUT_HASH_11111111_1
    with pytest.raises(ValueError, match="PRIVACY_PEPPER"):
        Pepper.from_env({})
    with pytest.raises(ValueError, match="16"):
        Pepper("corto")

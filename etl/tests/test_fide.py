from pathlib import Path

import pytest

from chessquery_etl import fide

SAMPLE = Path(__file__).parent / "fixtures" / "players_list_sample.txt"


def sample_lines():
    return SAMPLE.read_text(encoding="latin-1").splitlines()


def test_parses_only_chile_with_minimal_fields():
    players = {p.fide_id: p for p in fide.parse(sample_lines())}
    assert set(players) == {"9000001", "9000002", "9000003", "9000004", "9000005"}  # sin ARG

    ana = players["9000001"]
    assert (ana.first_name, ana.last_name) == ("Ana Maria", "Ficticia Soto")
    assert (ana.sex, ana.title, ana.birth_year) == ("F", "WFM", 1998)
    assert (ana.standard, ana.rapid, ana.blitz) == (2105, 2050, 2011)
    assert not ana.inactive and ana.has_rating()

    assert players["9000003"].inactive
    assert not players["9000004"].has_rating()
    assert players["9000005"].birth_year is None


def test_name_without_comma_goes_to_last_name():
    assert fide.split_name("Unnombre") == ("-", "Unnombre")
    assert fide.split_name("Perez, ") == ("-", "Perez")


def test_other_federation_and_blank_lines():
    lines = sample_lines() + ["   "]
    assert [p.fide_id for p in fide.parse(lines, federation="ARG")] == ["9000006"]


def test_rejects_unexpected_header():
    with pytest.raises(ValueError, match="faltan columnas"):
        list(fide.parse(["ID Number  Name  Fed"]))

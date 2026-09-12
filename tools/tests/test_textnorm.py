from mushroom_packs.textnorm import normalize


def test_umlauts_and_case():
    assert normalize("Grüner Knollenblätterpilz") == "gruener knollenblaetterpilz"
    assert normalize("Fliegenpilz (Amanita)") == "fliegenpilz amanita"
    assert normalize("  Boletus   edulis ") == "boletus edulis"
    assert normalize("Straße") == "strasse"
    assert normalize("Réaumur") == "reaumur"

from mushroom_packs.edibility import infobox_edibility, normalise, worst


def test_infobox_values():
    wt = "{{Mycomorphbox | name = Amanita muscaria | howEdible = poisonous | howEdible2 = psychoactive | hymeniumType = gills | stipeCharacter = ring and volva }} text"
    assert infobox_edibility(wt) == ("POISONOUS", ["poisonous", "psychoactive"])
    assert infobox_edibility("{{mycomorphbox|edibility=choice}}")[0] == "CHOICE"
    assert infobox_edibility("{{mycomorphbox|edibility=deadly}}")[0] == "DEADLY"
    assert infobox_edibility("no box here") == (None, [])
    from mushroom_packs.edibility import infobox_params
    assert infobox_params(wt)["stipeCharacter"] == ["ring and volva"]
    assert infobox_params("{{mycomorphbox|edibility=choice}}")["howEdible"] == ["choice"]


def test_normalise_and_worst():
    assert normalise("Edibility: inedible") == "INEDIBLE"
    assert normalise("edible but not recommended") == "CAUTION"
    assert worst(["EDIBLE", "DEADLY"]) == "DEADLY"
    assert worst([None]) is None

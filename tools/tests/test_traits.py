from mushroom_packs.traits import extract_feature_text, season_text, value_key, wiki_traits

MORPH_EN = (
    "The fly agaric is a large white-gilled, white-spotted, usually red mushroom. The cap is 8 to 20 cm in diameter, "
    "bright red with white warts. The gills are free and white. The stem is white, 5 to 20 cm high, with a ring. "
    "The base of the stem is bulbous and bears remnants of the volva as rings of warts."
)
MORPH_DE = (
    "Der Hut ist leuchtend rot und mit weißen Flocken besetzt. Die Lamellen sind weiß und frei. "
    "Der Stiel trägt einen häutigen Ring. Die Stielbasis ist knollig verdickt."
)


def test_english_feature_extraction():
    assert "cap is 8 to 20 cm" in extract_feature_text(MORPH_EN, "CAP", "en")
    assert value_key("CAP", extract_feature_text(MORPH_EN, "CAP", "en")) == "red"
    assert value_key("GILLS_PORES", extract_feature_text(MORPH_EN, "GILLS_PORES", "en")) == "gills"
    assert value_key("RING", extract_feature_text(MORPH_EN, "RING", "en")) == "present"
    assert value_key("BASE_VOLVA", extract_feature_text(MORPH_EN, "BASE_VOLVA", "en")) == "volva"


def test_german_feature_extraction():
    rows = wiki_traits("amanita-muscaria", "de", {"morphology": MORPH_DE}, 1)
    by = {r["feature"]: r for r in rows}
    assert by["CAP"]["value_key"] == "red"
    assert by["GILLS_PORES"]["value_key"] == "gills"
    assert by["RING"]["value_key"] == "present"
    assert by["BASE_VOLVA"]["value_key"] == "bulbous"
    assert by["CAP"]["source"] == "wiki:de:1"


def test_ring_absent_beats_present():
    assert value_key("RING", "Der Stiel ist ohne Ring.") == "absent"
    assert value_key("RING", "The stem lacks a ring.") == "absent"


def test_season_text_wraps_and_thresholds():
    hist = [0, 0, 0, 0, 0, 2, 5, 30, 60, 40, 8, 1]
    assert season_text(hist, "de").startswith("Beobachtungen vor allem Aug–Okt")
    assert season_text([0] * 12, "en") is None
    winter = [30, 10, 0, 0, 0, 0, 0, 0, 0, 0, 10, 40]
    assert "Nov–Feb" in season_text(winter, "en")

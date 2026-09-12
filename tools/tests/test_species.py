from mushroom_packs.species import slugify


def test_slugify():
    assert slugify("Amanita muscaria") == "amanita-muscaria"
    assert slugify("Boletus edulis var. edulis") == "boletus-edulis-var-edulis"

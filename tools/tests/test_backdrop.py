import random

from mushroom_packs.backdrop import LAYERS, SUPERSAMPLE, hill_profile, render_layer


def test_hill_profile_is_periodic():
    profile = hill_profile(random.Random(3), 960.0, 300.0, 40.0, (1, 2, 3))
    assert abs(profile(0.0) - profile(960.0)) < 1e-6


def test_render_is_deterministic_and_sized():
    a = render_layer("light", "mid", width=240, height=60, seed=1)
    b = render_layer("light", "mid", width=240, height=60, seed=1)
    assert a.size == (240, 60) and a.mode == "RGBA"
    assert a.tobytes() == b.tobytes()


def test_light_and_dark_share_geometry():
    light = render_layer("light", "near", width=240, height=60, seed=1).getchannel("A")
    dark = render_layer("dark", "near", width=240, height=60, seed=1).getchannel("A")
    assert light.tobytes() == dark.tobytes()


def test_far_fades_out_at_top_and_near_is_solid_at_bottom():
    far = render_layer("light", "far", width=240, height=60, seed=1)
    assert all(far.getpixel((x, 0))[3] == 0 for x in range(0, 240, 20))
    near = render_layer("dark", "near", width=240, height=60, seed=1)
    assert all(near.getpixel((x, 59))[3] == 255 for x in range(0, 240, 20))


def test_all_layers_render():
    for layer in LAYERS:
        assert render_layer("dark", layer, width=120, height=30, seed=2).size == (120, 30)
    assert SUPERSAMPLE >= 2

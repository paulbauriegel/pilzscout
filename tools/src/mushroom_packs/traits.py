"""Step 5: structured trait rows per species x feature x language.

Sources, in priority order:
  1. Wikipedia morphology/habitat sections (basis REFERENCE): sentences that mention the feature.
  2. FungiTastic aggregates (basis FUNGITASTIC): habitat/substrate and season templates.
A normalised value_key per feature is derived by keyword voting so the comparison engine can
decide SHARED vs differing without natural-language understanding.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import pandas as pd

from .config import Config

FEATURES = ["CAP", "GILLS_PORES", "STEM", "RING", "BASE_VOLVA", "SURFACE_TEXTURE", "HABITAT_SUBSTRATE", "REGION_SEASON"]

# Sentence must match to be attributed to a feature.
KEYWORDS = {
    "CAP": {"de": r"\bH[uü]t", "en": r"\bcap\b|\bpileus\b"},
    "GILLS_PORES": {"de": r"\bLamellen|\bR[öo]hren|\bPoren|\bStacheln|\bLeisten", "en": r"\bgills?\b|\blamellae|\bpores?\b|\btubes?\b|\bspines|\bteeth\b|\bridges"},
    "STEM": {"de": r"\bStiel", "en": r"\bstems?\b|\bstipe|\bstalk"},
    "RING": {"de": r"\bRing\b|\bManschette|\bbering", "en": r"\bring\b|\bannulus|\bpartial veil|\bskirt"},
    "BASE_VOLVA": {"de": r"\bVolva|\bKnolle|\bStielbasis|\bBasis\b|\bwurzelnd", "en": r"\bvolva|\bbulb|\bbase\b|\brooting"},
    "SURFACE_TEXTURE": {"de": r"\bschleimig|\bklebrig|\bschmierig|\btrocken|\bschuppig|\bfaserig|\bsamtig|\bglatt|\bfilzig|\bOberfläche", "en": r"\bslimy|\bviscid|\bsticky|\bglutinous|\bdry\b|\bscaly|\bscales|\bfibrous|\bfibrillose|\bvelvety|\bsmooth|\bsurface"},
    "HABITAT_SUBSTRATE": {"de": r"\bWald|\bwächst|\bBoden|\bHolz|\bLaub|\bNadel|\bWiese|\bMykorrhiza|\bSubstrat|\bStubben|\bStamm", "en": r"\bwood(land)?s?\b|\bforest|\bgrows?\b|\bsoil\b|\bgrass|\bmycorrhiz|\bsubstrate|\bsaprob|\bparasit|\bdead|\bstumps?\b|\blogs?\b|\btrunks?"},
    "REGION_SEASON": {"de": r"\bSommer|\bHerbst|\bFrühling|\bFrühjahr|\bWinter|\bMonat|\bJuli|\bAugust|\bSeptember|\bOktober|\bNovember|\bEuropa|\bDeutschland|\bverbreitet|\bVerbreitung", "en": r"\bsummer|\bautumn|\bfall\b|\bspring|\bwinter|\bJuly|\bAugust|\bSeptember|\bOctober|\bNovember|\bEurope|\bGermany|\bdistribut|\bwidespread|\bcommon in"},
}

# value_key voting: first matching group wins (order = priority)
VALUE_KEYS = {
    "GILLS_PORES": [("pores", r"\bPoren|\bR[öo]hren|\bpores?\b|\btubes?\b"), ("teeth", r"\bStacheln|\bspines|\bteeth"), ("ridges", r"\bLeisten|\bridges|\bveins"), ("gills", r"\bLamellen|\bgills?\b|\blamellae")],
    "RING": [("absent", r"\bohne Ring|\bkein(en)? Ring|\bringlos|\bno ring|\bwithout (a )?ring|\blacks? (a )?ring|\bno annulus"), ("present", r"\bRing\b|\bManschette|\bbering|\bring\b|\bannulus")],
    "BASE_VOLVA": [("volva", r"\bVolva|\bScheide|\bvolva"), ("rooting", r"\bwurzelnd|\brooting|\bpseudorhiza"), ("bulbous", r"\bKnolle|\bknollig|\bbulb|\bbulbous|\bswollen base"), ("plain", r"\bStielbasis|\bbase\b")],
    "SURFACE_TEXTURE": [("slimy", r"\bschleimig|\bklebrig|\bschmierig|\bslimy|\bviscid|\bsticky|\bglutinous"), ("scaly", r"\bschuppig|\bSchuppen|\bscaly|\bscales"), ("fibrous", r"\bfaserig|\bfibrous|\bfibrillose"), ("velvety", r"\bsamtig|\bfilzig|\bvelvety|\btomentose"), ("dry", r"\btrocken|\bdry\b"), ("smooth", r"\bglatt|\bsmooth")],
    "CAP": [("red", r"\brot(e|er|es|en)?\b|\bscharlach|\bred\b|\bscarlet|\borange-red"), ("yellow", r"\bgelb|\byellow|\bochre|\bocker"), ("brown", r"\bbraun|\bbrown|\btan\b"), ("white", r"\bweiß|\bwhite|\bcream"), ("grey", r"\bgrau|\bgr[ae]y"), ("green", r"\bgrün|\bolive|\bgreen"), ("purple", r"\bviolett|\bpurpur|\blila|\bpurple|\bviolet|\blilac")],
    "STEM": [("hollow", r"\bhohl|\bhollow"), ("bulbous", r"\bknollig|\bbulbous"), ("slender", r"\bschlank|\bdünn|\bslender|\bthin"), ("stout", r"\bkräftig|\bdick|\bstout|\bthick|\brobust")],
}

SENT_SPLIT = re.compile(r"(?<=[.!?])\s+(?=[A-ZÄÖÜ])")
MAX_TEXT = 240
MAX_SENTENCES = 2


def extract_feature_text(text: str, feature: str, lang: str) -> str | None:
    kw = re.compile(KEYWORDS[feature][lang], re.I)
    sentences = [s.strip() for s in SENT_SPLIT.split(text) if 20 <= len(s.strip()) <= 400]
    hits = [s for s in sentences if kw.search(s)][:MAX_SENTENCES]
    if not hits:
        return None
    out = " ".join(hits)
    if len(out) > MAX_TEXT:
        out = out[: MAX_TEXT - 1].rsplit(" ", 1)[0] + "…"
    return out


def value_key(feature: str, text: str) -> str | None:
    for key, pattern in VALUE_KEYS.get(feature, []):
        if re.search(pattern, text, re.I):
            return key
    return None


def wiki_traits(species_id: str, lang: str, sections: dict, revision: int) -> list[dict]:
    morph = sections.get("morphology") or ""
    habitat = sections.get("habitat") or ""
    rows = []
    for feature in FEATURES:
        source_text = habitat if feature in ("HABITAT_SUBSTRATE", "REGION_SEASON") else morph
        if feature in ("HABITAT_SUBSTRATE", "REGION_SEASON") and not source_text:
            source_text = morph
        if not source_text:
            continue
        text = extract_feature_text(source_text, feature, lang)
        if not text:
            continue
        rows.append(
            {
                "species_id": species_id, "feature": feature, "lang": lang, "text": text,
                "value_key": value_key(feature, text), "basis": "WIKIPEDIA", "source": f"wiki:{lang}:{revision}",
            }
        )
    return rows


MONTHS = {"de": ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"], "en": ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]}


def season_text(hist: list[int], lang: str) -> str | None:
    total = sum(hist)
    if total < 5:
        return None
    # months holding >= 8 % of observations, reported as the longest contiguous run (wrapping December -> January)
    active = [i for i, v in enumerate(hist) if v / total >= 0.08]
    if not active:
        return None
    best, cur = [], []
    seq = active + [a + 12 for a in active]
    for m in seq:
        if cur and m != cur[-1] + 1:
            best = max(best, cur, key=len)
            cur = []
        cur.append(m)
    best = max(best, cur, key=len)
    best = [m % 12 for m in best[:12]]
    names = MONTHS[lang]
    span = names[best[0]] if len(best) == 1 else f"{names[best[0]]}–{names[best[-1]]}"
    if lang == "de":
        return f"Beobachtungen vor allem {span} ({total} FungiTastic-Beobachtungen)."
    return f"Observed mostly {span} ({total} FungiTastic observations)."


def habitat_text(habitats: list, substrates: list, n_obs: int, lang: str) -> str | None:
    if n_obs < 5 or not habitats:
        return None
    h_name, h_count = habitats[0]
    parts = []
    if lang == "de":
        parts.append(f"Meist in: {h_name} ({round(100 * h_count / n_obs)} % der Beobachtungen)")
        if substrates:
            s_name, s_count = substrates[0]
            parts.append(f"auf {s_name} ({round(100 * s_count / n_obs)} %)")
        return "; ".join(parts) + "."
    parts.append(f"Mostly in: {h_name} ({round(100 * h_count / n_obs)} % of observations)")
    if substrates:
        s_name, s_count = substrates[0]
        parts.append(f"on {s_name} ({round(100 * s_count / n_obs)} %)")
    return "; ".join(parts) + "."


def fungitastic_traits(stats: pd.DataFrame) -> list[dict]:
    rows = []
    for r in stats.itertuples(index=False):
        habitats = json.loads(r.habitats_json)
        substrates = json.loads(r.substrates_json)
        hist = json.loads(r.month_hist_json)
        for lang in ("de", "en"):
            h = habitat_text(habitats, substrates, int(r.n_obs), lang)
            if h:
                rows.append({"species_id": r.species_id, "feature": "HABITAT_SUBSTRATE", "lang": lang, "text": h, "value_key": None, "basis": "FUNGITASTIC", "source": "fungitastic:stats"})
            s = season_text(hist, lang)
            if s:
                rows.append({"species_id": r.species_id, "feature": "REGION_SEASON", "lang": lang, "text": s, "value_key": None, "basis": "FUNGITASTIC", "source": "fungitastic:stats"})
    return rows


def run(cfg: Config) -> Path:
    rows: list[dict] = []
    wiki_path = cfg.cache_dir / "wiki_articles.parquet"
    if wiki_path.exists():
        wiki = pd.read_parquet(wiki_path)
        for a in wiki.itertuples(index=False):
            rows += wiki_traits(a.species_id, a.lang, json.loads(a.sections_json), int(a.revision_id))
    else:
        print("WARNING: no Wikipedia data, only FungiTastic traits will be produced")
    stats_path = cfg.cache_dir / "species_stats.parquet"
    if stats_path.exists():
        rows += fungitastic_traits(pd.read_parquet(stats_path))
    else:
        print("WARNING: no FungiTastic stats, no habitat/season traits")
    df = pd.DataFrame(rows, columns=["species_id", "feature", "lang", "text", "value_key", "basis", "source"])
    out = cfg.cache_dir / "traits.parquet"
    df.to_parquet(out, index=False)
    print(f"{len(df)} trait rows for {df.species_id.nunique() if len(df) else 0} species")
    return out

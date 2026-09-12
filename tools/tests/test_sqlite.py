import json
import sqlite3

from mushroom_packs.config import CFG
from mushroom_packs.sqlite import create_tables, load_room_schema


def test_room_schema_tables_match_pragma(tmp_path):
    database = load_room_schema(CFG)
    conn = sqlite3.connect(tmp_path / "t.db")
    create_tables(conn, database)
    for entity in database["entities"]:
        cols = {row[1]: row for row in conn.execute(f"PRAGMA table_info(`{entity['tableName']}`)")}
        expected = {f["columnName"] for f in entity["fields"]}
        assert set(cols) == expected, entity["tableName"]
        for f in entity["fields"]:
            assert bool(cols[f["columnName"]][3]) == bool(f.get("notNull", False)), (entity["tableName"], f["columnName"])
    (hash_,) = conn.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
    assert hash_ == database["identityHash"]

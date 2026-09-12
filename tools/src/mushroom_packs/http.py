"""Small polite HTTP helper: rate limiting, retries, on-disk JSON cache."""
from __future__ import annotations

import json
import threading
import time
from pathlib import Path
from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


class Client:
    def __init__(self, user_agent: str, rps: float) -> None:
        self.session = requests.Session()
        self.session.headers["User-Agent"] = user_agent
        retry = Retry(total=5, backoff_factor=1.0, status_forcelist=(429, 500, 502, 503, 504), allowed_methods=("GET",))
        self.session.mount("https://", HTTPAdapter(max_retries=retry))
        self.min_interval = 1.0 / rps if rps > 0 else 0.0
        self._last = 0.0
        self._lock = threading.Lock()

    def _throttle(self) -> None:
        with self._lock:
            now = time.monotonic()
            wait = self.min_interval - (now - self._last)
            if wait > 0:
                time.sleep(wait)
            self._last = time.monotonic()

    def get_json(self, url: str, params: dict[str, Any] | None = None, timeout: float = 30) -> Any:
        self._throttle()
        r = self.session.get(url, params=params, timeout=timeout)
        r.raise_for_status()
        return r.json()

    def get_bytes(self, url: str, timeout: float = 60) -> bytes:
        self._throttle()
        r = self.session.get(url, timeout=timeout)
        r.raise_for_status()
        return r.content


def cached_json(path: Path, producer) -> Any:
    """Return JSON from `path` if it exists, otherwise call producer(), store and return it."""
    if path.exists():
        return json.loads(path.read_text())
    value = producer()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False))
    return value

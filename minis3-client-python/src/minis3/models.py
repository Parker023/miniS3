"""Value types returned by the client."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from typing import Any, Optional


@dataclass(frozen=True)
class ObjectInfo:
    """Metadata of a stored object."""

    key: str
    size: int
    content_type: Optional[str]
    etag: Optional[str]  # hex MD5, without quotes; None for files uploaded through the v1 API
    last_modified: Optional[datetime]  # timezone-aware (UTC)

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "ObjectInfo":
        return cls(
            key=data["key"],
            size=int(data.get("size") or 0),
            content_type=data.get("contentType"),
            etag=data.get("etag"),
            last_modified=parse_instant(data.get("lastModified")),
        )


@dataclass(frozen=True)
class ObjectPage:
    """One page of a listing. Pass next_cursor to the next list_objects call while truncated."""

    bucket: str
    prefix: str
    objects: list[ObjectInfo]
    next_cursor: Optional[str]
    truncated: bool

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "ObjectPage":
        return cls(
            bucket=data["bucket"],
            prefix=data.get("prefix") or "",
            objects=[ObjectInfo.from_json(o) for o in data.get("objects") or []],
            next_cursor=data.get("nextCursor"),
            truncated=bool(data.get("truncated")),
        )


@dataclass(frozen=True)
class PresignedUrl:
    """A time-limited link for one object and one method that needs no Authorization header."""

    url: str
    method: str
    expires_at: Optional[datetime]

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> "PresignedUrl":
        return cls(url=data["url"], method=data["method"], expires_at=parse_instant(data.get("expiresAt")))


_FRACTION = re.compile(r"\.(\d+)")


def parse_instant(value: Optional[str]) -> Optional[datetime]:
    """Parse an ISO-8601 instant as the server sends it ('2026-10-19T10:00:00.123456789Z')."""
    if not value:
        return None
    text = value.strip().replace("Z", "+00:00")
    # Python < 3.11 accepts at most 6 fractional digits; the server may send 9
    text = _FRACTION.sub(lambda m: "." + m.group(1)[:6].ljust(6, "0"), text, count=1)
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def parse_http_date(value: Optional[str]) -> Optional[datetime]:
    """Parse a Last-Modified header ('Mon, 19 Oct 2026 10:00:00 GMT')."""
    if not value:
        return None
    try:
        return parsedate_to_datetime(value)
    except (TypeError, ValueError):
        return None

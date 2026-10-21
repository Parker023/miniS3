"""URL encoding matching the server's expectations."""

from urllib.parse import quote


def encode_segment(value: str) -> str:
    """Percent-encode one path segment: everything except RFC 3986 unreserved characters (space -> %20)."""
    return quote(value, safe="")


def encode_key(key: str) -> str:
    """Encode each '/'-separated segment of an object key, keeping the slashes."""
    return "/".join(encode_segment(segment) for segment in key.split("/"))

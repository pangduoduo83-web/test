"""Minimal S-expression reader/writer tuned for KiCad files.

Parsed documents are plain nested ``list`` objects. Atoms are:

* :class:`Sym`  — bare symbol (``kicad_pcb``, ``yes``, ``F.Cu`` when unquoted)
* :class:`Str`  — quoted string (rendered back with quotes)
* ``int`` / ``float`` — numeric literals

The writer re-indents using tabs, matching KiCad's own layout closely enough
that KiCad reloads the file without complaint.
"""

from __future__ import annotations

from collections.abc import Iterator
from typing import Any

SExpr = list  # type alias for readability


class Sym(str):
    """Unquoted symbol."""

    __slots__ = ()


class Str(str):
    """Quoted string literal."""

    __slots__ = ()


class ParseError(ValueError):
    pass


# ---------------------------------------------------------------------------
# Reader
# ---------------------------------------------------------------------------
def _tokenize(text: str) -> Iterator[tuple[str, str]]:
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch in " \t\r\n":
            i += 1
            continue
        if ch == "(":
            yield ("(", "(")
            i += 1
        elif ch == ")":
            yield (")", ")")
            i += 1
        elif ch == '"':
            j = i + 1
            buf = []
            while j < n:
                c = text[j]
                if c == "\\" and j + 1 < n:
                    nxt = text[j + 1]
                    buf.append({"n": "\n", "t": "\t", "r": "\r"}.get(nxt, nxt))
                    j += 2
                    continue
                if c == '"':
                    break
                buf.append(c)
                j += 1
            else:
                raise ParseError("unterminated string literal")
            yield ("str", "".join(buf))
            i = j + 1
        else:
            j = i
            while j < n and text[j] not in " \t\r\n()":
                j += 1
            yield ("atom", text[i:j])
            i = j


def _atom(raw: str) -> Any:
    try:
        if raw.lstrip("-+").isdigit():
            return int(raw)
        return float(raw)
    except ValueError:
        return Sym(raw)


def loads(text: str) -> SExpr:
    """Parse a document and return the root list."""
    stack: list[list] = []
    root: list | None = None
    for kind, value in _tokenize(text):
        if kind == "(":
            node: list = []
            if stack:
                stack[-1].append(node)
            stack.append(node)
        elif kind == ")":
            if not stack:
                raise ParseError("unbalanced ')'")
            node = stack.pop()
            if not stack:
                root = node
        elif kind == "str":
            if not stack:
                raise ParseError("string outside of list")
            stack[-1].append(Str(value))
        else:
            if not stack:
                raise ParseError("atom outside of list")
            stack[-1].append(_atom(value))
    if stack or root is None:
        raise ParseError("unbalanced '('")
    return root


def load(path) -> SExpr:
    with open(path, encoding="utf-8") as fh:
        return loads(fh.read())


# ---------------------------------------------------------------------------
# Writer
# ---------------------------------------------------------------------------
def fmt_num(v: float | int) -> str:
    if isinstance(v, bool):
        return "yes" if v else "no"
    if isinstance(v, int):
        return str(v)
    s = f"{v:.6f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s


def _dump_atom(a: Any) -> str:
    if isinstance(a, Str):
        escaped = a.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
        return f'"{escaped}"'
    if isinstance(a, Sym):
        return str(a)
    if isinstance(a, (int, float)):
        return fmt_num(a)
    if isinstance(a, str):  # plain str → quote for safety
        return _dump_atom(Str(a))
    raise TypeError(f"cannot serialise {type(a)!r}")


def dumps(node: Any, indent: int = 0) -> str:
    if not isinstance(node, list):
        return _dump_atom(node)
    if not node:
        return "()"
    if not any(isinstance(c, list) for c in node):
        return "(" + " ".join(_dump_atom(c) for c in node) + ")"

    head = "(" + _dump_atom(node[0])
    i = 1
    while i < len(node) and not isinstance(node[i], list):
        head += " " + _dump_atom(node[i])
        i += 1
    lines = [head]
    pad = "\t" * (indent + 1)
    for child in node[i:]:
        lines.append(pad + dumps(child, indent + 1))
    lines.append("\t" * indent + ")")
    return "\n".join(lines)


def dump(node: SExpr, path) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(dumps(node))
        fh.write("\n")


# ---------------------------------------------------------------------------
# Query helpers
# ---------------------------------------------------------------------------
def children(node: SExpr, name: str) -> list[SExpr]:
    """All direct child lists whose head symbol equals *name*."""
    return [c for c in node if isinstance(c, list) and c and c[0] == name]


def child(node: SExpr, name: str) -> SExpr | None:
    for c in node:
        if isinstance(c, list) and c and c[0] == name:
            return c
    return None


def value(node: SExpr | None, name: str, default: Any = None, index: int = 1) -> Any:
    """Return ``node``'s ``(name v)`` payload (``index``-th atom) or *default*."""
    if node is None:
        return default
    c = child(node, name)
    if c is None or len(c) <= index:
        return default
    return c[index]


def values(node: SExpr | None, name: str) -> list[Any]:
    c = child(node, name) if node is not None else None
    return list(c[1:]) if c else []


def set_child(node: SExpr, name: str, payload: list[Any]) -> SExpr:
    """Replace (or append) child ``(name ...payload)``."""
    new = [Sym(name), *payload]
    for i, c in enumerate(node):
        if isinstance(c, list) and c and c[0] == name:
            node[i] = new
            return new
    node.append(new)
    return new


def walk(node: SExpr) -> Iterator[SExpr]:
    """Depth-first traversal over all sub-lists."""
    yield node
    for c in node:
        if isinstance(c, list):
            yield from walk(c)

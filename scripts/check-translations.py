#!/usr/bin/env python3
"""Checks that every module's Baianês strings.xml mirrors its default strings.xml.

Rules (translatable="false" entries are ignored):
  - same keys (string / plurals) in the same order;
  - same format placeholders (%1$s, %d, %%) per string and per plural item;
  - every plurals has one and other, in both files;
  - both files are valid XML.
Exits 1 listing every violation, 0 when everything matches.
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
SKIP_DIRS = {"build", ".git", ".gradle", "node_modules"}


def load(path, errors):
    """Returns a list of (kind, name, {item: text}) in file order, or None if unparseable."""
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        errors.append(f"{path}: invalid XML: {e}")
        return None
    entries = []
    for el in root:
        if el.tag not in ("string", "plurals"):
            continue
        if el.get("translatable") == "false":
            continue
        name = el.get("name")
        if el.tag == "string":
            entries.append(("string", name, {"": "".join(el.itertext())}))
        else:
            items = {}
            for item in el.findall("item"):
                items[item.get("quantity")] = "".join(item.itertext())
            entries.append(("plurals", name, items))
    return entries


def placeholders(text):
    return Counter(PLACEHOLDER.findall(text))


def check_module(default_path, baianes_path, errors):
    d = load(default_path, errors)
    b = load(baianes_path, errors)
    if d is None or b is None:
        return
    for label, entries, path in (("default", d, default_path), ("baianes", b, baianes_path)):
        seen = set()
        for kind, name, items in entries:
            if (kind, name) in seen:
                errors.append(f"{path}: duplicate {kind} '{name}'")
            seen.add((kind, name))
            if kind == "plurals":
                for q in ("one", "other"):
                    if q not in items:
                        errors.append(f"{path}: plurals '{name}' is missing the '{q}' item")
    dk = [(k, n) for k, n, _ in d]
    bk = [(k, n) for k, n, _ in b]
    for k, n in dk:
        if (k, n) not in bk:
            errors.append(f"{baianes_path}: missing {k} '{n}' (present in default)")
    for k, n in bk:
        if (k, n) not in dk:
            errors.append(f"{baianes_path}: extra {k} '{n}' (not in default)")
    common_d = [x for x in dk if x in bk]
    common_b = [x for x in bk if x in dk]
    if common_d != common_b:
        for i, (x, y) in enumerate(zip(common_d, common_b)):
            if x != y:
                errors.append(
                    f"{baianes_path}: order differs from default at position {i + 1}: "
                    f"expected '{x[1]}', found '{y[1]}'"
                )
                break
    bmap = {(k, n): items for k, n, items in b}
    for k, n, ditems in d:
        bitems = bmap.get((k, n))
        if bitems is None:
            continue
        for q, text in ditems.items():
            if q not in bitems:
                continue
            if placeholders(text) != placeholders(bitems[q]):
                where = f"'{n}'" + (f" item '{q}'" if q else "")
                errors.append(
                    f"{baianes_path}: placeholders differ in {where}: "
                    f"default {sorted(placeholders(text).elements())} vs "
                    f"baianes {sorted(placeholders(bitems[q]).elements())}"
                )


def main():
    errors = []
    modules = 0
    for default in sorted(ROOT.rglob("src/main/res/values/strings.xml")):
        if SKIP_DIRS & set(default.relative_to(ROOT).parts):
            continue
        baianes = default.parent.parent / "values-b+pt+BR+baianes" / "strings.xml"
        modules += 1
        if not baianes.is_file():
            errors.append(f"{baianes.relative_to(ROOT)}: missing (default exists at {default.relative_to(ROOT)})")
            continue
        check_module(default, baianes, errors)
    if errors:
        print("Translation check FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {str(e).replace(str(ROOT) + '/', '')}", file=sys.stderr)
        sys.exit(1)
    print(f"Translation check OK: {modules} modules, Baianês strings match the default.")


if __name__ == "__main__":
    main()

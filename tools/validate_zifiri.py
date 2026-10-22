#!/usr/bin/env python3
"""Static pre-build checks for Zifiri. No Android SDK required."""

import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
JAVA = ROOT / "app" / "src" / "main" / "java"
TEST = ROOT / "app" / "src" / "test" / "java"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle.kts"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"

failures = []
passes = []


def ok(msg):
    passes.append(msg)
    print("PASS  " + msg)


def fail(msg):
    failures.append(msg)
    print("FAIL  " + msg)


def file_kinds():
    kinds = {
        "drawable", "layout", "menu", "raw", "xml", "mipmap", "anim",
        "color", "font", "transition", "navigation",
    }
    defs = defaultdict(set)
    for p in RES.rglob("*"):
        if not p.is_file():
            continue
        rel = p.relative_to(RES)
        if len(rel.parts) < 2:
            continue
        kind = rel.parts[0].split("-")[0]
        if kind in kinds:
            defs[kind].add(p.stem)
    return defs


def collect_values(defs, xml_errors):
    value_files = sorted(RES.glob("values*/**/*.xml"))
    string_names = defaultdict(list)
    for path in value_files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            xml_errors.append(f"{path.relative_to(ROOT)}: {exc}")
            continue
        for el in root:
            tag = el.tag
            name = el.get("name")
            if name is None:
                continue
            if tag == "string":
                defs["string"].add(name)
                string_names[path].append(name)
            elif tag in ("color", "dimen", "bool", "integer", "plurals", "string-array", "array"):
                kind = "array" if tag in ("string-array", "array") else tag
                defs[kind].add(name)
            elif tag == "style":
                defs["style"].add(name)
            elif tag == "item":
                typ = el.get("type")
                if typ:
                    defs[typ].add(name)
            elif tag == "attr":
                defs["attr"].add(name)
    return string_names


def parse_all_xml(xml_errors):
    for p in RES.rglob("*.xml"):
        if "raw" in p.relative_to(RES).parts:
            continue
        try:
            ET.parse(p)
        except ET.ParseError as exc:
            xml_errors.append(f"{p.relative_to(ROOT)}: {exc}")


def check_refs(defs):
    ref_re = re.compile(r"@(\w+)/([\w.]+)")
    at_id_re = re.compile(r"@\+id/(\w+)")
    bad = []
    for src in list(RES.rglob("*.xml")) + [MANIFEST]:
        if not src.is_file():
            continue
        try:
            text = src.read_text(encoding="utf-8")
        except OSError as exc:
            bad.append(f"{src.relative_to(ROOT)}: {exc}")
            continue
        for name in at_id_re.findall(text):
            defs["id"].add(name)
    for src in list(RES.rglob("*.xml")) + [MANIFEST]:
        if not src.is_file():
            continue
        text = src.read_text(encoding="utf-8")
        for kind, name in ref_re.findall(text):
            if kind in ("android", "null"):
                continue
            if name not in defs.get(kind, set()):
                bad.append(f"{src.relative_to(ROOT)} -> @{kind}/{name}")
    return sorted(set(bad))


def check_kotlin_refs(defs):
    r_re = re.compile(r"\bR\.(\w+)\.(\w+)")
    bad = []
    for src in JAVA.rglob("*.kt"):
        text = src.read_text(encoding="utf-8")
        for kind, name in r_re.findall(text):
            if kind == "styleable":
                continue
            candidates = {name}
            if kind == "style":
                candidates.add(name.replace("_", "."))
            if not candidates & defs.get(kind, set()):
                bad.append(f"{src.relative_to(ROOT)} -> R.{kind}.{name}")
    return sorted(set(bad))


def main():
    required = [
        GRADLE, MANIFEST, WORKFLOW,
        JAVA / "com/wendigo/zifiri/Game.kt",
        JAVA / "com/wendigo/zifiri/Model.kt",
        JAVA / "com/wendigo/zifiri/Dungeon.kt",
        JAVA / "com/wendigo/zifiri/GameView.kt",
        JAVA / "com/wendigo/zifiri/GameActivity.kt",
        TEST / "com/wendigo/zifiri/BalanceTest.kt",
        TEST / "com/wendigo/zifiri/DungeonTest.kt",
        TEST / "com/wendigo/zifiri/ShopTest.kt",
        TEST / "com/wendigo/zifiri/AutosaveTest.kt",
    ]
    missing = [str(p.relative_to(ROOT)) for p in required if not p.is_file()]
    if missing:
        fail("required files missing: " + ", ".join(missing))
    else:
        ok("required files present")

    defs = file_kinds()
    xml_errors = []
    parse_all_xml(xml_errors)
    if xml_errors:
        fail("invalid XML: " + "; ".join(xml_errors[:5]))
    else:
        ok("all resource XML well-formed")

    string_names = collect_values(defs, xml_errors)
    duplicates = []
    for path, names in string_names.items():
        seen = set()
        for n in names:
            if n in seen:
                duplicates.append(f"{path.relative_to(ROOT)}:{n}")
            seen.add(n)
    if duplicates:
        fail("duplicate string names: " + ", ".join(sorted(set(duplicates))))
    else:
        ok("no duplicate string names")

    bad_refs = check_refs(defs)
    if bad_refs:
        fail("unresolved resource refs: " + "; ".join(bad_refs[:10]))
    else:
        ok("resource references resolve")

    bad_kotlin = check_kotlin_refs(defs)
    if bad_kotlin:
        fail("unresolved R refs: " + "; ".join(bad_kotlin[:10]))
    else:
        ok("Kotlin R references resolve")

    manifest = MANIFEST.read_text(encoding="utf-8")
    manifest_problems = []
    for activity in ("MenuActivity", "GameActivity"):
        if activity not in manifest:
            manifest_problems.append(f"missing {activity}")
    if 'android.permission.INTERNET' in manifest:
        manifest_problems.append("unexpected INTERNET permission")
    if manifest_problems:
        fail("manifest: " + ", ".join(manifest_problems))
    else:
        ok("manifest activities and offline-only permissions")

    gradle = GRADLE.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not code_match or int(code_match.group(1)) < 2:
        fail("versionCode must be >= 2")
    if not name_match or name_match.group(1) != "1.1":
        fail("versionName must be 1.1")
    if code_match and name_match and int(code_match.group(1)) >= 2 and name_match.group(1) == "1.1":
        ok("version is 1.1 / code >= 2")

    workflow = WORKFLOW.read_text(encoding="utf-8")
    for token in ("testDebugUnitTest", "validate_zifiri.py", "v1.1-build"):
        if token not in workflow:
            fail(f"workflow missing {token}")
    if all(t in workflow for t in ("testDebugUnitTest", "validate_zifiri.py", "v1.1-build")):
        ok("CI runs validator, unit tests and v1.1 release tag")

    game = (JAVA / "com/wendigo/zifiri/Game.kt").read_text(encoding="utf-8")
    model = (JAVA / "com/wendigo/zifiri/Model.kt").read_text(encoding="utf-8")
    markers = [
        ("onProgress", "Game.kt"),
        ("fun hint(", "Game.kt"),
        ("object Balance", "Model.kt"),
    ]
    missing_markers = []
    if "onProgress" not in game:
        missing_markers.append("onProgress")
    if "fun hint(" not in game:
        missing_markers.append("fun hint(")
    if "object Balance" not in model:
        missing_markers.append("object Balance")
    if missing_markers:
        fail("core markers missing: " + ", ".join(missing_markers))
    else:
        ok("autosave, hints and balance markers present")

    if failures:
        print(f"\nFAILED ({len(failures)} problem(s))")
        return 1
    print(f"\nALL CHECKS PASSED ({len(passes)} categories)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Inventory source-defined UI chrome; imported media and runtime text are excluded."""
import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
JAVA_TOKEN = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
UI_FILES = [
    "app/src/main/java/de/danoeh/antennapod/zipimport/SeriesLibraryFragment.java",
    "app/src/main/java/de/danoeh/antennapod/zipimport/SeriesTranscriptFragment.java",
]


def normalized(text):
    return " ".join(FORMAT.sub("", text.replace(r"\n", " ")).split())


def summarize(entries):
    values = [normalized(entry["text"]) for entry in entries]
    return {
        "entries": len(entries),
        "han_characters": sum(len(HAN.findall(value)) for value in values),
        "characters_without_placeholders": sum(len(value) for value in values),
        "entries_over_40_characters": sum(len(value) > 40 for value in values),
        "entries_over_40_raw_characters": sum(len(" ".join(entry["text"].split())) > 40 for entry in entries),
        "entries_with_at_least_2_han": sum(len(HAN.findall(value)) >= 2 for value in values),
        "long_entries": [
            {"file": item["file"], "key": item["key"], "length": len(value), "text": item["text"]}
            for item, value in zip(entries, values) if len(value) > 40
        ],
    }


def xml_entries(root):
    result = []
    paths = sorted(path for path in root.rglob("*.xml")
                   if "/src/main/res/values/" in path.as_posix()
                   and "build" not in path.relative_to(root).parts
                   and ".git" not in path.relative_to(root).parts)
    for path in paths:
        resource = ET.parse(path).getroot()
        for element in resource:
            name = element.attrib.get("name", "")
            if element.tag == "string":
                rows = [(name, element)]
            elif element.tag in ("string-array", "plurals"):
                rows = [(f"{name}[{child.attrib.get('quantity', index)}]", child)
                        for index, child in enumerate(element) if child.tag == "item"]
            else:
                continue
            for key, item in rows:
                result.append({"file": str(path.relative_to(root)), "key": key,
                               "resource_name": name, "kind": element.tag,
                               "translatable": element.attrib.get("translatable", "true"),
                               "text": "".join(item.itertext())})
    return result


def java_inventory(root):
    rows = []
    for filename in UI_FILES:
        text = (root / filename).read_text()
        comments_removed = JAVA_TOKEN.sub(lambda match: "" if match.group().startswith(("//", "/*"))
                                         else match.group(), text)
        dialogs = [{"line": comments_removed.count("\n", 0, match.start()) + 1,
                    "constructor": match.group()}
                   for match in re.finditer(r"new\s+(?:MaterialAlertDialogBuilder|AlertDialog\.Builder)\s*\(",
                                            comments_removed)]
        literals = []
        for match in re.finditer(r'"(?:\\.|[^"\\])*"', comments_removed):
            value = match.group()[1:-1]
            if len(HAN.findall(value)) >= 2:
                literals.append({"line": comments_removed.count("\n", 0, match.start()) + 1, "text": value})
        rows.append({"file": filename,
                     "resource_references": sorted(set(re.findall(r"\bR\.(?:string|array)\.([A-Za-z0-9_]+)",
                                                                  comments_removed))),
                     "dialog_sites": dialogs, "hardcoded_han_literals": literals})
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    inventory = xml_entries(root)
    custom = [item for item in inventory if item["resource_name"].startswith(("zip_", "kitchen_brand_"))]
    zip_scalar = [item for item in custom if item["resource_name"].startswith("zip_") and item["kind"] == "string"]
    java = java_inventory(root)
    names = {name for item in java for name in item["resource_references"]}
    referenced = [item for item in custom if item["resource_name"] in names]
    app_names = re.findall(r'resValue\s+"string",\s*"app_name",\s*"([^"\n]+)"',
                           (root / "common.gradle").read_text())
    report = {
        "schema_version": 1,
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
        "scope": {
            "default_xml": "Tracked source paths **/src/main/res/values/*.xml, string/string-array/plurals; values-* translations excluded",
            "custom_xml": "resource name starts zip_ or kitchen_brand_; array items count individually",
            "java_ui": UI_FILES,
            "excluded": ["Imported podcast titles, summaries, episode text and transcript bodies",
                         "logs, file paths, worker state keys, model/importer source strings",
                         "translated values-* resources", "generated Gradle app_name from XML totals",
                         "runtime screen rendering, offscreen visibility and popup frequency"],
            "long_threshold": "Unicode characters >40 after removing printf placeholders and normalizing whitespace; not English word count",
        },
        "all_default_xml": summarize(inventory),
        "custom_xml": summarize(custom),
        "custom_zip_scalar": summarize(zip_scalar),
        "custom_referenced_by_java_ui": summarize(referenced),
        "default_xml_by_file": dict(Counter(item["file"] for item in inventory)),
        "custom_xml_by_kind": dict(Counter(item["kind"] for item in custom)),
        "java_ui": java,
        "custom_dialog_constructor_sites": sum(len(item["dialog_sites"]) for item in java),
        "gradle_display_names_separate": app_names,
        "custom_resource_inventory": custom,
        "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output)
    else:
        print(output, end="")


if __name__ == "__main__":
    main()

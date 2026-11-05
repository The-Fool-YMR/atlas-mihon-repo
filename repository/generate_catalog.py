#!/usr/bin/env python3
"""Publish built extensions using the protobuf schema consumed by Mihon."""

import argparse
import gzip
import hashlib
import html
import json
import re
import shutil
import subprocess
from pathlib import Path

from google.protobuf import json_format

import index_pb2


def command(tool, *arguments):
    return subprocess.check_output([str(tool), *map(str, arguments)], text=True, encoding="utf-8")


def generate(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    info = json.loads(args.metadata.read_text(encoding="utf-8"))
    apk_name = args.apk.name
    badging = command(args.aapt2, "dump", "badging", args.apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package.groups() != (info["packageName"], str(info["versionCode"]), info["versionName"]):
        raise ValueError("APK package/version does not match the generated build metadata")
    certificates = command(args.apksigner, "verify", "--print-certs", args.apk)
    certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})", certificates)
    if not certificate:
        raise ValueError("Could not verify the APK signing certificate")
    fingerprint = certificate.group(1).lower()
    base_url = args.public_base_url.rstrip("/")
    if not base_url.startswith("https://"):
        raise ValueError("The public base URL must use HTTPS")

    def copy_asset(file, directory, name=None):
        destination = output / directory / (name or file.name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, destination)
        return f"{base_url}/{directory}/{destination.name}"

    resources = index_pb2.Resources(
        apkUrl=copy_asset(args.apk, "apk"),
        iconUrl=copy_asset(args.icon, "icon", f"{info['packageName']}.png"),
    )
    if args.jar:
        resources.jarUrl = copy_asset(args.jar, "jar")
    extension = index_pb2.Extension(
        name=info["name"],
        packageName=info["packageName"],
        resources=resources,
        extensionLib=info["extensionLib"],
        versionCode=info["versionCode"],
        versionName=info["versionName"],
        contentWarning=info["contentWarning"],
        sources=[
            index_pb2.Source(
                id=int(source["id"]),
                name=source["name"],
                language=source["lang"],
                homeUrl=args.website,
                mirrorUrls=source.get("mirrorUrls", []),
            )
            for source in info["sources"]
        ],
    )
    index = index_pb2.Index(
        name=args.name,
        badgeLabel=args.badge_label,
        signingKey=fingerprint,
        contact=index_pb2.Contact(website=args.contact),
        extensionList=index_pb2.ExtensionList(extensions=[extension]),
    )
    serialized = index.SerializeToString(deterministic=True)
    compressed = gzip.compress(serialized, mtime=0)
    (output / "index.pb").write_bytes(compressed)
    (output / "index.json").write_text(json_format.MessageToJson(index) + "\n", encoding="utf-8")
    legacy = [{
        "name": f"Tachiyomi: {info['name']}", "pkg": info["packageName"],
        "apk": apk_name, "lang": info["sources"][0]["lang"],
        "code": info["versionCode"], "version": info["versionName"],
        "nsfw": int(info["contentWarning"] == index_pb2.CONTENT_WARNING_NSFW),
        "sources": [{**source, "id": str(source["id"])} for source in info["sources"]],
    }]
    (output / "index.min.json").write_text(json.dumps(legacy, separators=(",", ":")), encoding="utf-8")
    repo = {"index_v2": f"{base_url}/index.pb", "meta": {
        "name": args.name, "website": args.contact, "signingKeyFingerprint": fingerprint,
    }}
    (output / "repo.json").write_text(json.dumps(repo, indent=2) + "\n", encoding="utf-8")
    (output / "index.html").write_text(
        '<!doctype html><html lang="en"><meta charset="utf-8">'
        f'<title>{html.escape(args.name)}</title><h1>{html.escape(args.name)}</h1>'
        f'<p>Add this repository URL in Mihon: <a href="{html.escape(base_url)}/index.pb">'
        f'{html.escape(base_url)}/index.pb</a></p>'
        f'<p><a href="{html.escape(resources.apkUrl)}">Download {html.escape(info["name"])} '
        f'{html.escape(info["versionName"])}</a></p></html>\n', encoding="utf-8",
    )
    # Decode the real compressed payload, rather than checking only the JSON mirror.
    restored = index_pb2.Index.FromString(gzip.decompress(compressed))
    assert restored == index
    assert restored.extensionList.extensions[0].versionCode == int(package.group(2))
    manifest = {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(output.rglob("*")) if p.is_file() and p.name != "checksums.json"}
    (output / "checksums.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"index": f"{base_url}/index.pb", "extensions": 1, "signingKey": fingerprint,
                      "package": extension.packageName, "versionCode": extension.versionCode}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("metadata", "apk", "icon", "apksigner", "aapt2", "output"):
        parser.add_argument(f"--{field}", type=Path, required=True)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--public-base-url", required=True)
    parser.add_argument("--name", default="thefool")
    parser.add_argument("--badge-label", default="thefool")
    parser.add_argument("--website", default="https://atlascomics.dpdns.org")
    parser.add_argument("--contact", default="https://github.com/The-Fool-YMR/atlas-mihon-repo")
    generate(parser.parse_args())

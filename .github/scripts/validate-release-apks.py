#!/usr/bin/env python3
"""Validate signed APK identity, upgrade compatibility and native server packaging."""
import argparse
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

REQUIRED_ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64", "universal"}
NATIVE_ABIS = REQUIRED_ABIS - {"universal"}
PACKAGE_NAME = "com.mwplay.app"


def command_output(*arguments):
    return subprocess.run(arguments, check=True, capture_output=True, text=True).stdout


def signing_certificate(apksigner, apk):
    output = command_output(apksigner, "verify", "--print-certs", str(apk))
    # v3.1 verification prints SDK-bounded signer labels for both v3.1 and v3.0.
    # The same production certificate can therefore appear more than once.
    signer_lines = [line.strip() for line in output.splitlines() if line.startswith("Signer ") and "certificate SHA-256 digest:" in line]
    label = r"Signer (?:#\d+|\(minSdkVersion=\d+(?: \(dev release=true\))?,\s*maxSdkVersion=\d+\))"
    digests = [re.fullmatch(label + r" certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", line) for line in signer_lines]
    certificates = {match.group(1).lower() for match in digests if match}
    if not signer_lines or any(match is None for match in digests) or len(certificates) != 1:
        observed = "\n".join(signer_lines) if signer_lines else "[No signer certificate SHA-256 lines in apksigner stdout]"
        raise ValueError(f"{apk.name}: expected exactly one distinct verified signing certificate; observed apksigner stdout:\n{observed}")
    return certificates.pop()


def apk_identity(aapt, apk):
    output = command_output(aapt, "dump", "badging", str(apk))
    package_line = next((line for line in output.splitlines() if line.startswith("package: ")), "")
    attributes = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    if not {"name", "versionCode", "versionName"}.issubset(attributes):
        raise ValueError(f"{apk.name}: missing APK package/version information")
    attributes["versionCode"] = int(attributes["versionCode"])
    attributes["leanback"] = "uses-feature: name='android.software.leanback'" in output
    return attributes


def apk_outputs(apk_dir):
    metadata = json.loads((apk_dir / "output-metadata.json").read_text())
    elements = metadata.get("elements")
    if not isinstance(elements, list) or not elements:
        raise ValueError("APK metadata contains no outputs")
    outputs = {}
    for element in elements:
        filters = [item["value"] for item in element.get("filters", []) if item.get("filterType") == "ABI"]
        if len(filters) > 1:
            raise ValueError("APK output contains multiple ABI filters")
        abi = filters[0] if filters else "universal"
        if abi not in REQUIRED_ABIS or abi in outputs:
            raise ValueError(f"Unexpected or duplicate APK ABI: {abi}")
        relative = Path(element["outputFile"])
        if relative.is_absolute() or relative.name != str(relative):
            raise ValueError("APK outputFile must be a file name")
        apk = apk_dir / relative
        if not apk.is_file():
            raise ValueError(f"APK output missing: {apk}")
        outputs[abi] = apk
    if set(outputs) != REQUIRED_ABIS:
        raise ValueError(f"Missing APK outputs: {sorted(REQUIRED_ABIS - set(outputs))}")
    if set(apk_dir.glob("*.apk")) != set(outputs.values()):
        raise ValueError("Release directory has APKs absent from output metadata")
    return outputs


def native_server_abis(apk):
    with zipfile.ZipFile(apk) as archive:
        return {
            name.split("/")[1]
            for name in archive.namelist()
            if re.fullmatch(r"lib/[^/]+/libstream_server\.so", name)
            and archive.getinfo(name).file_size > 0
        }


def validate(apk_dir, previous_apk, version_name, version_code, platform, apksigner, aapt):
    previous_identity = apk_identity(aapt, previous_apk)
    if previous_identity["name"] != PACKAGE_NAME or previous_identity["versionName"] != "1.0.2":
        raise ValueError("Previous APK must be the published MW Play 1.0.2")
    if version_code <= previous_identity["versionCode"]:
        raise ValueError("Release versionCode must be greater than the previous APK")
    previous_certificate = signing_certificate(apksigner, previous_apk)
    outputs = apk_outputs(apk_dir)
    for abi, apk in sorted(outputs.items()):
        identity = apk_identity(aapt, apk)
        expected = (PACKAGE_NAME, version_name, version_code, platform == "tv")
        actual = (identity["name"], identity["versionName"], identity["versionCode"], identity["leanback"])
        if actual != expected:
            raise ValueError(f"{apk.name}: incorrect app identity, version or platform: {actual}")
        if signing_certificate(apksigner, apk) != previous_certificate:
            raise ValueError(f"{apk.name}: signing certificate differs from MW Play 1.0.2")
        expected_abis = NATIVE_ABIS if abi == "universal" else {abi}
        if native_server_abis(apk) != expected_abis:
            raise ValueError(f"{apk.name}: incorrect native streaming server ABI packaging")
        print(f"Verified {abi}: {version_name} ({version_code}), production signature and native server")
    return {
        "application_id": PACKAGE_NAME,
        "version_name": version_name,
        "version_code": version_code,
        "platform": platform,
        "source_commit": os.environ.get("GITHUB_SHA"),
        "source_branch": f"feat/mw-play-{platform}",
        "signer_certificate_sha256": previous_certificate,
        "previous_release": "v1.0.2",
        "previous_version_code": previous_identity["versionCode"],
        "verified_abis": sorted(outputs),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument("--previous-apk", required=True, type=Path)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--platform", required=True, choices=("mobile", "tv"))
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--report-path", required=True, type=Path)
    args = parser.parse_args()
    report = validate(args.apk_dir, args.previous_apk, args.version_name, args.version_code, args.platform, args.apksigner, args.aapt)
    args.report_path.parent.mkdir(parents=True, exist_ok=True)
    args.report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()

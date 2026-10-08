import contextlib
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("validate_release_apks", SCRIPTS / "validate-release-apks.py")
validation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apks = self.root / "apks"
        self.apks.mkdir()
        self.previous = self.root / "previous.apk"
        self.previous.write_bytes(b"previous fixture")
        self.identity_overrides = {}
        self.certificate_overrides = {}
        elements = []
        for abi in sorted(validation.REQUIRED_ABIS):
            filename = f"app-{abi}-release.apk"
            self.write_apk(abi)
            elements.append({"filters": [] if abi == "universal" else [{"filterType": "ABI", "value": abi}], "outputFile": filename})
        self.metadata = {"elements": elements}
        self.write_metadata()

    def write_metadata(self):
        (self.apks / "output-metadata.json").write_text(json.dumps(self.metadata))

    def write_apk(self, abi, server_abis=None):
        server_abis = server_abis if server_abis is not None else (validation.NATIVE_ABIS if abi == "universal" else {abi})
        with zipfile.ZipFile(self.apks / f"app-{abi}-release.apk", "w") as archive:
            for native_abi in server_abis:
                archive.writestr(f"lib/{native_abi}/libstream_server.so", b"native fixture")

    def tool_output(self, *arguments):
        apk = Path(arguments[-1])
        if arguments[0] == "apksigner":
            certificate = self.certificate_overrides.get(apk.name, "a" * 64)
            return f"Signer #1 certificate SHA-256 digest: {certificate}\n"
        identity = {"name": "com.mwplay.app", "versionName": "1.0.2" if apk == self.previous else "1.0.3", "versionCode": 5 if apk == self.previous else 125, "leanback": False}
        identity.update(self.identity_overrides.get(apk.name, {}))
        output = f"package: name='{identity['name']}' versionCode='{identity['versionCode']}' versionName='{identity['versionName']}'\n"
        if identity["leanback"]:
            output += "uses-feature: name='android.software.leanback'\n"
        return output

    def validate(self, platform="mobile", version_code=125):
        with patch.object(validation, "command_output", self.tool_output), contextlib.redirect_stdout(io.StringIO()):
            return validation.validate(self.apks, self.previous, "1.0.3", version_code, platform, "apksigner", "aapt")

    def test_all_five_mobile_outputs_match_previous_production_certificate(self):
        result = self.validate()
        self.assertEqual(result["signer_certificate_sha256"], "a" * 64)
        self.assertEqual(result["verified_abis"], sorted(validation.REQUIRED_ABIS))

    def parse_certificate(self, output):
        with patch.object(validation, "command_output", return_value=output):
            return validation.signing_certificate("apksigner", self.previous)

    def test_sdk_bounded_v31_and_v3_labels_with_same_certificate(self):
        output = f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {'a' * 64}\nSigner (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {'a' * 64}\n"
        self.assertEqual(self.parse_certificate(output), "a" * 64)

    def test_sdk_bounded_dev_release_label(self):
        output = f"Signer (minSdkVersion=33 (dev release=true), maxSdkVersion=2147483647) certificate SHA-256 digest: {'A' * 64}\n"
        self.assertEqual(self.parse_certificate(output), "a" * 64)

    def test_source_stamp_and_public_key_digests_are_not_signing_certificates(self):
        output = f"Signer #1 certificate SHA-256 digest: {'a' * 64}\nSigner #1 public key SHA-256 digest: {'b' * 64}\nSource Stamp Signer certificate SHA-256 digest: {'c' * 64}\n"
        self.assertEqual(self.parse_certificate(output), "a" * 64)

    def test_rejects_distinct_sdk_bounded_certificates_with_public_diagnostic(self):
        output = f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {'a' * 64}\nSigner (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {'b' * 64}\n"
        with self.assertRaisesRegex(ValueError, "distinct verified signing certificate") as failure:
            self.parse_certificate(output)
        self.assertIn("a" * 64, str(failure.exception))
        self.assertIn("b" * 64, str(failure.exception))

    def test_rejects_distinct_numbered_signers(self):
        output = f"Signer #1 certificate SHA-256 digest: {'a' * 64}\nSigner #2 certificate SHA-256 digest: {'b' * 64}\n"
        with self.assertRaisesRegex(ValueError, "distinct verified signing certificate"):
            self.parse_certificate(output)

    def test_rejects_unrecognized_extra_signer_certificate_line(self):
        output = f"Signer #1 certificate SHA-256 digest: {'a' * 64}\nSigner unknown certificate SHA-256 digest: {'b' * 64}\n"
        with self.assertRaisesRegex(ValueError, "distinct verified signing certificate"):
            self.parse_certificate(output)

    def test_rejects_missing_certificate_stdout(self):
        with self.assertRaisesRegex(ValueError, "No signer certificate SHA-256 lines"):
            self.parse_certificate("Verified\nSigner #1 public key SHA-256 digest: " + "a" * 64)

    def test_apksigner_verification_failure_remains_a_failure(self):
        with patch.object(validation, "command_output", side_effect=subprocess.CalledProcessError(1, ["apksigner", "verify"])):
            with self.assertRaises(subprocess.CalledProcessError):
                validation.signing_certificate("apksigner", self.previous)

    def test_tv_outputs_require_leanback_identity(self):
        for apk in self.apks.glob("*.apk"):
            self.identity_overrides[apk.name] = {"leanback": True}
        self.assertEqual(self.validate("tv")["platform"], "tv")

    def test_rejects_changed_signing_certificate(self):
        self.certificate_overrides["app-arm64-v8a-release.apk"] = "b" * 64
        with self.assertRaisesRegex(ValueError, "signing certificate differs"):
            self.validate()

    def test_rejects_incorrect_apk_version(self):
        self.identity_overrides["app-arm64-v8a-release.apk"] = {"versionName": "1.2.4"}
        with self.assertRaisesRegex(ValueError, "incorrect app identity"):
            self.validate()

    def test_rejects_wrong_platform(self):
        with self.assertRaisesRegex(ValueError, "incorrect app identity"):
            self.validate("tv")

    def test_rejects_downgrade_against_previous_release(self):
        with self.assertRaisesRegex(ValueError, "greater than the previous APK"):
            self.validate(version_code=5)

    def test_rejects_universal_missing_one_native_server_abi(self):
        self.write_apk("universal", {"arm64-v8a", "armeabi-v7a", "x86"})
        with self.assertRaisesRegex(ValueError, "native streaming server ABI"):
            self.validate()

    def test_rejects_split_with_wrong_native_server_abi(self):
        self.write_apk("arm64-v8a", {"armeabi-v7a"})
        with self.assertRaisesRegex(ValueError, "native streaming server ABI"):
            self.validate()

    def test_rejects_missing_apk_output(self):
        self.metadata["elements"].pop()
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "Missing APK outputs"):
            self.validate()

    def test_rejects_output_path_outside_apk_directory(self):
        self.metadata["elements"][0]["outputFile"] = "../previous.apk"
        self.write_metadata()
        with self.assertRaisesRegex(ValueError, "must be a file name"):
            self.validate()

    def test_mobile_and_tv_assets_and_checksums_cannot_collide(self):
        destination = self.root / "dist"
        for platform in ("Mobile", "TV"):
            subprocess.run([sys.executable, str(SCRIPTS / "collect-apks.py"), "--metadata", str(self.apks / "output-metadata.json"), "--apk-dir", str(self.apks), "--output-dir", str(destination), "--version-name", "1.0.3", "--build-type", "release", "--file-prefix", f"MW-Play-{platform}"], check=True, capture_output=True)
            checksums = (destination / f"MW-Play-{platform}-v1.0.3-SHA256SUMS.txt").read_text().splitlines()
            self.assertEqual(len(checksums), 5)
            self.assertTrue(all(f"MW-Play-{platform}-v1.0.3-" in line for line in checksums))
        self.assertEqual(len(list(destination.glob("*.apk"))), 10)
        self.assertEqual(len(list(destination.glob("*SHA256SUMS.txt"))), 2)


if __name__ == "__main__":
    unittest.main()

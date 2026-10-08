import contextlib
import io
import json
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest.mock import patch

WORKFLOW = Path(__file__).resolve().parents[2] / "workflows" / "release-apk.yml"


class PublishWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "dist").mkdir()
        for index in range(8):
            (self.root / "dist" / f"asset-{index}").write_bytes(b"validated asset")
        (self.root / ".github").mkdir()
        (self.root / ".github" / "release-notes.md").write_text("Release notes")
        self.sha = "a" * 40
        self.release = None
        self.tag_exists = False
        self.upload_mismatch = False
        self.commands = []
        self.platform = "tv" if "RELEASE_PLATFORM: tv" in WORKFLOW.read_text() else "mobile"
        self.environment = {"GITHUB_REPOSITORY": "example/app", "GITHUB_SHA": self.sha, "RELEASE_TAG": "tv-v1.0.3" if self.platform == "tv" else "v1.0.3", "RELEASE_PLATFORM": self.platform, "VERSION_NAME": "1.0.3", "RUNNER_TEMP": str(self.root)}
        source = WORKFLOW.read_text().split("      - name: Publish complete GitHub Release from exact source\n", 1)[1]
        source = source.split("          python - <<'PY'\n", 1)[1].split("          PY\n", 1)[0]
        self.script = compile(textwrap.dedent(source), str(WORKFLOW), "exec")

    def subprocess_run(self, arguments, **kwargs):
        if arguments[1:3] == ["release", "view"]:
            return subprocess.CompletedProcess(arguments, 0 if self.release else 1, "", "" if self.release else "release not found")
        if arguments[1] == "api":
            return subprocess.CompletedProcess(arguments, 0 if self.tag_exists else 1, "", "" if self.tag_exists else "HTTP 404: Not Found")
        self.fail(f"Unexpected external command: {arguments}")

    def subprocess_output(self, arguments, **kwargs):
        self.commands.append(arguments)
        if arguments[1] == "api":
            if "POST" in arguments:
                self.tag_exists = True
            return json.dumps({"object": {"type": "commit", "sha": self.sha}})
        operation = arguments[2]
        if operation == "view":
            return json.dumps(self.release)
        if operation == "create":
            self.assertTrue(self.tag_exists, "Tag must be verified before draft creation")
            self.assertIn("--draft", arguments)
            self.assertIn("--verify-tag", arguments)
            self.assertEqual(arguments[arguments.index("--target") + 1], self.sha)
            notes = Path(arguments[arguments.index("--notes-file") + 1]).read_text()
            self.release = {"isDraft": True, "targetCommitish": self.sha, "body": notes, "assets": []}
        elif operation == "upload":
            self.assertTrue(self.release["isDraft"])
            assets = sorted((self.root / "dist").iterdir())
            self.release["assets"] = [{"name": asset.name, "size": asset.stat().st_size} for asset in assets]
            if self.upload_mismatch:
                self.release["assets"][0]["size"] += 1
        elif operation == "edit":
            self.assertIn("--draft=false", arguments)
            self.assertIn(f'--latest={"true" if self.platform == "mobile" else "false"}', arguments)
            self.release["isDraft"] = False
        else:
            self.fail(f"Unexpected external command: {arguments}")
        return ""

    def publish(self):
        previous_directory = Path.cwd()
        try:
            os.chdir(self.root)
            with patch.dict(os.environ, self.environment), patch.object(subprocess, "run", self.subprocess_run), patch.object(subprocess, "check_output", self.subprocess_output), contextlib.redirect_stdout(io.StringIO()):
                exec(self.script, {})
        finally:
            os.chdir(previous_directory)

    def test_new_release_creates_exact_tag_then_draft_and_publishes_complete_assets(self):
        self.publish()
        self.assertFalse(self.release["isDraft"])
        self.assertEqual(len(self.release["assets"]), 8)
        mutations = [arguments[2] for arguments in self.commands if arguments[1] == "release" and arguments[2] != "view"]
        self.assertEqual(mutations, ["create", "upload", "edit"])

    def test_published_release_is_never_overwritten(self):
        self.release = {"isDraft": False, "targetCommitish": self.sha, "body": f"Source commit: `{self.sha}`", "assets": []}
        with self.assertRaisesRegex(SystemExit, "refusing to overwrite"):
            self.publish()
        self.assertFalse(any(arguments[2] in ("create", "upload", "edit") for arguments in self.commands))

    def test_unrelated_draft_is_never_overwritten(self):
        self.release = {"isDraft": True, "targetCommitish": "b" * 40, "body": "Other source", "assets": []}
        with self.assertRaisesRegex(SystemExit, "refusing to overwrite"):
            self.publish()
        self.assertFalse(self.tag_exists)

    def test_upload_mismatch_leaves_release_as_draft(self):
        self.upload_mismatch = True
        with self.assertRaisesRegex(SystemExit, "differ from the validated"):
            self.publish()
        self.assertTrue(self.release["isDraft"])


if __name__ == "__main__":
    unittest.main()

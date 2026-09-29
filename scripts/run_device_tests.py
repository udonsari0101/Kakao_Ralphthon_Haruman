"""Install and run Android tests without removing apps or clearing existing app data.

Raw output, serial, and run-as production data archives stay under the ignored
apk-project/verification/private-backups directory. No build is performed.
"""
import argparse
import hashlib
import io
import json
import re
import subprocess
import sys
import tarfile
import uuid
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("serial", "adb", "apk", "test-apk"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--package", default="org.ralphthon.haruman_ralphthon")
    parser.add_argument("--class", dest="test_class")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package):
        parser.error("Invalid package name.")
    if args.test_class and not re.fullmatch(r"[A-Za-z0-9_.$#,\[\]-]+", args.test_class):
        parser.error("Invalid test class selector.")
    adb, apk, test_apk = (Path(p).expanduser().resolve() for p in (args.adb, args.apk, args.test_apk))
    if not all(p.is_file() for p in (adb, apk, test_apk)):
        parser.error("ADB and both APK paths must identify existing files.")
    candidates = (Path.cwd(), *Path.cwd().parents, Path(__file__).resolve().parents[1])
    repo = next((p for p in candidates if (p / "apk-project").is_dir() and (p / ".git").exists()), None)
    if repo is None:
        parser.error("Cannot find the repository containing apk-project.")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    evidence = repo / "apk-project/verification/private-backups" / stamp
    evidence.mkdir(parents=True, exist_ok=False)
    metadata = {"serial": args.serial, "package": args.package,
                "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                "testApkSha256": hashlib.sha256(test_apk.read_bytes()).hexdigest()}
    workspace = PurePosixPath("files/ui-workspace") / hashlib.sha256(b"haruman_local_v1").hexdigest()
    metadata["productionWorkspace"] = workspace.as_posix()

    def run(label, *command, timeout=180):
        argv = [str(adb), "-s", args.serial, *command]
        (evidence / (label + ".command.json")).write_text(json.dumps(argv), encoding="utf-8")
        try:
            result = subprocess.run(argv, capture_output=True, timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            (evidence / (label + ".error.txt")).write_text(repr(exc), encoding="utf-8")
            raise RuntimeError(label + " could not complete; see private evidence") from None
        (evidence / (label + ".stdout")).write_bytes(result.stdout)
        (evidence / (label + ".stderr")).write_bytes(result.stderr)
        if result.returncode:
            raise RuntimeError(label + " failed; see private evidence")
        return result.stdout

    def installed(label):
        # Unlike `pm path`, this succeeds when our package has not been installed.
        packages = run(label, "shell", "pm", "list", "packages").splitlines()
        if not any(line.startswith(b"package:") for line in packages):
            raise RuntimeError("Cannot read the device's installed-package list")
        return ("package:" + args.package).encode() in packages

    def snapshot(phase):
        # A successful run-as listing distinguishes a genuinely absent directory
        # from a non-debuggable/inaccessible installed app. The latter must stop.
        listing = run(phase + "-access", "exec-out", "run-as", args.package, "ls", "-a").splitlines()
        targets = []
        if b"shared_prefs" in listing:
            targets.append(PurePosixPath("shared_prefs"))
        else:
            (evidence / (phase + "-no-prefs.txt")).write_text("run-as succeeded; shared_prefs absent\n")
        # Walk only known ancestors. A failed listing is not treated as missing data.
        # UUID test stores hash to different directories and are never archived here.
        workspace_exists = b"files" in listing
        if workspace_exists:
            files = run(phase + "-files", "exec-out", "run-as", args.package, "ls", "-a", "files").splitlines()
            workspace_exists = b"ui-workspace" in files
        if workspace_exists:
            namespaces = run(phase + "-namespaces", "exec-out", "run-as", args.package, "ls", "-a", "files/ui-workspace").splitlines()
            workspace_exists = workspace.name.encode() in namespaces
        if workspace_exists:
            targets.append(workspace)
        else:
            (evidence / (phase + "-no-workspace.txt")).write_text("run-as listings succeeded; production workspace absent\n")
        if not targets:
            return {}
        raw = run(phase + "-archive", "exec-out", "run-as", args.package, "tar", "-cf", "-", *(p.as_posix() for p in targets))
        (evidence / (phase + "-production-data.tar")).write_bytes(raw)
        hashes = {}
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as archive:
            for member in archive.getmembers():
                path = PurePosixPath(member.name)
                within_targets = any(path == target or target in path.parents for target in targets)
                ancestor_directory = member.isdir() and any(path in target.parents for target in targets)
                if not path.parts or path.is_absolute() or ".." in path.parts or not (within_targets or ancestor_directory):
                    raise RuntimeError("Unexpected production archive path")
                if member.isdir():
                    continue
                if not member.isfile():
                    raise RuntimeError("Production archive contains a non-regular file")
                # Current instrumentation fixtures use a fresh UUID preference name.
                if path.parts[0] == "shared_prefs" and re.fullmatch(r"workflow-test-[0-9a-f-]{36}\.xml(?:\.bak)?", path.name):
                    continue
                hashes[path.as_posix()] = hashlib.sha256(archive.extractfile(member).read()).hexdigest()
        return hashes

    failures, count = [], 0
    try:
        metadata["installedBefore"] = installed("before-package")
        before = snapshot("before") if metadata["installedBefore"] else {}
        metadata["beforeProductionFiles"] = before
        metadata["beforeProductionPrefs"] = {p: h for p, h in before.items() if p.startswith("shared_prefs/")}
    except Exception as exc:
        metadata["failure"] = str(exc)
        (evidence / "result.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        print("FAIL: pre-install data backup/access check failed; no install was attempted.")
        return 1
    try:
        for label, artifact in (("install-app", apk), ("install-tests", test_apk)):
            output = run(label, "install", "-r", str(artifact))
            if not re.search(rb"(?m)^Success\s*$", output):
                raise RuntimeError(label + " did not report Success")
        command = ["shell", "am", "instrument", "-w"]
        if args.test_class:
            # adb shell joins argv for the remote shell; keep nested-class `$` literal.
            command.extend(["-e", "class", "'" + args.test_class + "'"])
        command.append(args.package + ".test/androidx.test.runner.AndroidJUnitRunner")
        output = run("instrumentation", *command, timeout=1200)
        match = re.search(rb"(?m)^OK \((\d+) tests?\)\s*$", output)
        if not match or int(match[1]) < 1 or re.search(rb"FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=", output):
            raise RuntimeError("Instrumentation did not report a clean OK result")
        count = int(match[1])
    except Exception as exc:
        failures.append(str(exc))
    finally:
        try:
            if not installed("after-package"):
                raise RuntimeError("Application package is missing after the test attempt")
            after = snapshot("after")
            metadata["afterProductionFiles"] = after
            metadata["afterProductionPrefs"] = {p: h for p, h in after.items() if p.startswith("shared_prefs/")}
            if after != before:
                raise RuntimeError("Production preferences or workspace changed; original backup is preserved")
            metadata["productionPrefsUnchanged"] = True
            metadata["productionWorkspaceUnchanged"] = True
            metadata["productionDataUnchanged"] = True
        except Exception as exc:
            failures.append(str(exc))
    metadata.update(tests=count, success=not failures, failures=failures)
    (evidence / "result.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print((f"FAIL: {len(failures)} check(s) failed; see private evidence.") if failures else
          f"PASS: {count} tests; app remains installed; production preferences and workspace unchanged.")
    print("Private evidence: " + evidence.relative_to(repo).as_posix())
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

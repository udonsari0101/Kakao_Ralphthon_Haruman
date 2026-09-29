"""Watch the user specification inbox; report stable content changes, never execute them."""
import argparse
import hashlib
import json
import time
from datetime import datetime, timezone
from pathlib import Path


def scan(folder: Path):
    snapshot = {}
    for path in sorted(folder.rglob("*")):
        if not path.is_file() or path.suffix.lower() != ".md":
            continue
        relative = path.relative_to(folder).as_posix()
        if relative in {"README.md", "APPLIED.md"}:
            continue
        try:
            before = path.stat()
            content = path.read_bytes()
            after = path.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                continue
            snapshot[relative] = hashlib.sha256(content).hexdigest()
        except OSError:
            continue
    return snapshot


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--folder", default="md_plus")
    parser.add_argument("--log", default="apk-project/verification/md-watch.jsonl")
    parser.add_argument("--interval", type=float, default=15)
    parser.add_argument("--duration", type=float, default=10800)
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    folder, log = Path(args.folder), Path(args.log)
    folder.mkdir(parents=True, exist_ok=True)
    log.parent.mkdir(parents=True, exist_ok=True)
    seen, previous = {}, {}
    if log.exists():
        for line in log.read_text(encoding="utf-8").splitlines():
            try:
                event = json.loads(line)
                relative = Path(event["path"]).relative_to(folder).as_posix()
                seen[relative] = event["sha256"]
            except (ValueError, KeyError, TypeError):
                continue
    deadline = time.monotonic() + args.duration
    while True:
        current = scan(folder)
        stable = current if args.once else {k: v for k, v in current.items() if previous.get(k) == v}
        for name, digest in stable.items():
            if seen.get(name) == digest:
                continue
            event = {"at": datetime.now(timezone.utc).isoformat(), "event": "changed" if name in seen else "added", "path": str(folder / name), "sha256": digest}
            line = json.dumps(event, ensure_ascii=True)
            print(line, flush=True)
            with log.open("a", encoding="utf-8") as stream:
                stream.write(line + "\n")
            seen[name] = digest
        if args.once or time.monotonic() >= deadline:
            print(json.dumps({"event": "scan_finished", "files": len(current)}), flush=True)
            break
        previous = current
        time.sleep(max(1, min(args.interval, deadline - time.monotonic())))


if __name__ == "__main__":
    main()

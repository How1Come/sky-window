import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TARGETS = ROOT / "scripts/compatibility-targets.json"
KINDS = ("vanilla_static", "vanilla_movement", "iris_off_static", "iris_off_movement", "shader", "shader_movement", "resource", "resource_movement")
FATAL = re.compile(r"#@!@# Game crashed|Minecraft Crash Report|SKY_WINDOW_RENDER_SMOKE_FAILED|Exception in thread|BUILD FAILED")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path):
    return str(path.resolve().relative_to(ROOT))


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line and not line.startswith("#"))


def artifact(project, version):
    props = properties(project / "gradle.properties")
    path = project / "build/libs" / f"sky-window-{props['mod_version']}.jar"
    result = {"path": relative(path), "expected_version": props["mod_version"], "status": "missing"}
    if not path.is_file():
        return result
    failures = []
    expected_major = 44 + int(props.get("java_version", 25 if version.startswith("26.") else 17 if version in ("1.20.1", "1.20.2", "1.20.3", "1.20.4") else 21))
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if jar.testzip() is not None:
            failures.append("JAR integrity check failed")
        if len(names) != len(set(names)):
            failures.append("duplicate JAR entries")
        metadata = json.loads(jar.read("fabric.mod.json"))
        json_names = [name for name in names if name.endswith((".json", ".mcmeta"))]
        for name in json_names:
            try:
                json.loads(jar.read(name))
            except (ValueError, UnicodeError) as failure:
                failures.append(f"Invalid JSON in {name}: {failure}")
        majors = sorted({struct.unpack(">H", jar.read(name)[6:8])[0] for name in names if name.endswith(".class")})
        if metadata.get("id") != "sky_window":
            failures.append("unexpected mod id")
        if metadata.get("version") != props["mod_version"]:
            failures.append("version does not match the target project")
        if metadata.get("depends", {}).get("minecraft") != version:
            failures.append("Minecraft dependency is not the exact target")
        for name, expected in (("java", str(expected_major - 44)), ("fabricloader", props["loader_version"]), ("fabric-api", props["fabric_version"])):
            if metadata.get("depends", {}).get(name) != ">=" + expected:
                failures.append(name + " dependency does not match the target project")
        if majors != [expected_major]:
            failures.append(f"class major versions {majors} differ from {expected_major}")
        forbidden = [name for name in names if name.endswith(".jar") or name.startswith(("dev/skylight/test/", "net/irisshaders/", "net/caffeinemc/", "org/junit/")) or "gametest" in name.lower()]
        if forbidden:
            failures.append("test content or third party runtime bundled")
        if any("client_test" in str(value) or "gametest" in str(value) for value in metadata.get("entrypoints", {}).values()):
            failures.append("test entry point bundled")
    result.update(status="valid" if not failures else "invalid", sha256=digest(path), bytes=path.stat().st_size,
                  class_major_versions=majors, java=expected_major - 44, metadata=metadata,
                  validated_json_files=len(json_names), failures=failures)
    return result


def logs_for(project, version):
    paths = list((project / "build/verification").glob("*.log"))
    paths += list(project.glob("run/**/logs/latest.log"))
    pattern = re.compile(re.escape(version) + r"(?![0-9]|\.[0-9])")
    paths += [p for p in (ROOT / ".tooling/compat").rglob("*.log") if pattern.search(p.name) and not any(word in p.name for word in ("client", "movement", "asset", "download"))]
    return sorted(set(paths), key=lambda p: p.stat().st_mtime, reverse=True)


def server_tests(project, version):
    for path in logs_for(project, version):
        text = path.read_text(errors="replace")
        matches = re.findall(r"All (\d+) required tests passed", text)
        if matches:
            return {"status": "passed", "required_tests": int(matches[-1]), "log": relative(path), "artifact_binding": "not_recorded"}
    return {"status": "not_observed"}


def build_status(project, version):
    for path in logs_for(project, version):
        if "BUILD SUCCESSFUL" in path.read_text(errors="replace"):
            return {"status": "passed", "log": relative(path)}
    return {"status": "not_observed"}


@lru_cache(maxsize=1)
def client_run_logs():
    return [(path, path.read_text(errors="replace")) for path in (ROOT / ".tooling/compat").rglob("*.log")
            if any(word in path.name for word in ("client", "movement", "263-", "gui-"))]


def log_matches_capture(text, path):
    return bool(re.search(r"SKY_WINDOW_RENDER_SMOKE_COMPLETE[^\n]*\s" + re.escape(str(path.resolve())) + r"(?=\s|$)", text))


def validated_run_record(path, current_artifact, original_output=None):
    record_path = path / "run-record.json"
    log_path = path / "client.log"
    complete = path / "COMPLETE.txt"
    if not all(value.is_file() for value in (record_path, log_path, complete)):
        return None
    record = json.loads(record_path.read_text())
    if not record.get("success") or record.get("exit_code") != 0 or record.get("artifact_sha256") != current_artifact.get("sha256"):
        return None
    if record.get("directory") != relative(path):
        return None
    expected_output = (ROOT / original_output).resolve() if original_output else path
    text = log_path.read_text(errors="replace")
    if FATAL.search(text) or not log_matches_capture(text, expected_output):
        return None
    images = sorted(p for p in path.glob("[0-9][0-9]-*.png") if "-frame-" not in p.name)
    logged_images = re.findall(r"SKY_WINDOW_RENDER_CAPTURE\s+([^\s]+)", text)
    if record.get("captures") != len(images) or sorted(logged_images) != sorted(p.stem for p in images):
        return None
    end = datetime.fromisoformat(record["finished_at"]).timestamp()
    start = end - record["duration_seconds"]
    if any(not start - 5 <= value.stat().st_mtime <= end + 5 for value in images + [complete]):
        return None
    expected_java = Path(record["expected_java_home"]) / "bin/java"
    if Path(record.get("runtime", {}).get("java_executable", "")).resolve() != expected_java.resolve():
        return None
    return {"run_record": relative(record_path), "run_record_sha256": digest(record_path),
            "client_log": relative(log_path), "client_log_sha256": digest(log_path),
            "completion_sha256": digest(complete), "original_output_directory": relative(expected_output)}


def capture_directory(path, current_artifact):
    complete = path / "COMPLETE.txt"
    failed = path / "FAILED.txt"
    images = sorted(p for p in path.glob("[0-9][0-9]-*.png") if "-frame-" not in p.name)
    result = {"directory": relative(path), "status": "missing", "captures": len(images), "visually_reviewed": False}
    result["settings_mode"] = "manual" if "manual-movement" in path.name else "follow_world" if "movement" in path.name else "mixed"
    if path.name == "render-test":
        result["kind"] = "vanilla_static"
    elif path.name == "render-test-iris":
        result["kind"] = "shader"
    elif path.name == "render-test-iris-off":
        result["kind"] = "iris_off_static"
    elif "movement" in path.name and "iris-off" in path.name:
        result["kind"] = "iris_off_movement"
    elif path.name in ("render-test-movement", "render-test-movement-vanilla"):
        result["kind"] = "vanilla_movement"
    elif "movement" in path.name and "iris-on" in path.name:
        result["kind"] = "shader_movement"
    elif "movement" in path.name and "resources" in path.name:
        result["kind"] = "resource_movement"
    elif path.name.startswith("render-test-resources"):
        result["kind"] = "resource"
    queue_record = path / "run-record.json"
    if queue_record.is_file():
        profile = json.loads(queue_record.read_text()).get("profile", "")
        profile_kinds = {"static": "vanilla_static", "iris-off": "iris_off_static", "iris-on": "shader", "resource-static": "resource",
                         "movement-vanilla": "vanilla_movement", "movement-iris-off": "iris_off_movement",
                         "movement-iris-on": "shader_movement", "movement-resources": "resource_movement",
                         "manual-movement-vanilla": "vanilla_movement", "manual-movement-iris-off": "iris_off_movement",
                         "manual-movement-iris-on": "shader_movement", "manual-movement-resources": "resource_movement"}
        if profile in profile_kinds:
            result["kind"] = profile_kinds[profile]
            result["settings_mode"] = "manual" if profile.startswith("manual-") else "follow_world" if "movement" in profile else "mixed"
            result["profile"] = profile
    if failed.is_file():
        result.update(status="failed", failure=failed.read_text().strip())
    elif complete.is_file():
        match = re.search(r"Captured (\d+) render scenarios", complete.read_text())
        expected = int(match.group(1)) if match else 0
        result.update(status="completed" if expected and len(images) >= expected else "incomplete", expected_captures=expected)
    if complete.is_file():
        result["completion"] = relative(complete)
        result["completed_at"] = datetime.fromtimestamp(complete.stat().st_mtime, timezone.utc).isoformat()
        jar_path = ROOT / current_artifact["path"]
        result["artifact_relation"] = "unbound" if not jar_path.is_file() else "predates_artifact" if complete.stat().st_mtime < jar_path.stat().st_mtime else "run_after_artifact"
    run_logs = [(log, text) for log, text in client_run_logs() if log_matches_capture(text, path)]
    if run_logs:
        log, text = max(run_logs, key=lambda pair: pair[0].stat().st_mtime)
        result["run_log"] = relative(log)
        result["process_exit"] = "successful" if "BUILD SUCCESSFUL" in text and not FATAL.search(text) else "failed" if FATAL.search(text) else "unverified"
        if result["process_exit"] == "failed":
            result.update(status="failed", failure="Client run or shutdown failed; COMPLETE does not establish a successful exit")
    local_log = path / "client.log"
    observed_log = local_log.read_text(errors="replace") if local_log.is_file() else text if run_logs else ""
    if local_log.is_file() and FATAL.search(observed_log):
        result.update(status="failed", process_exit="failed", failure="Client log contains a crash or smoke-test failure")
    shader_pack = re.findall(r"Using shaderpack: ([^\n]+)", observed_log)
    shader_profile = re.findall(r"\(Iris\) Profile: ([^\n]+)", observed_log)
    resource_packs = re.findall(r"Reloading ResourceManager: ([^\n]+)", observed_log)
    if shader_pack:
        result["shader_pack"] = shader_pack[-1].strip()
    if shader_profile:
        result["shader_profile"] = shader_profile[-1].strip()
    if resource_packs:
        result["effective_resource_packs"] = resource_packs[-1].strip()
    run_record = validated_run_record(path, current_artifact)
    if run_record:
        result.update(process_exit="successful", run_record=run_record["run_record"], artifact_relation="matches_sha256")
    review = path / "visual-review.md"
    if review.is_file():
        result["historical_visual_review"] = relative(review)
        result["visually_reviewed"] = bool(images) and review.stat().st_mtime >= max(p.stat().st_mtime for p in images)
        result["review_notes"] = review.read_text().strip()
    proof_path = path / "compatibility-proof.json"
    if proof_path.is_file():
        proof = json.loads(proof_path.read_text())
        result["proof"] = relative(proof_path)
        result["kind"] = proof["kind"]
        if result["kind"] in ("shader", "resource") and "movement" in path.name:
            result["kind"] += "_movement"
        result["proof_matches_captures"] = proof.get("captures_sha256") == {p.name: digest(p) for p in images}
        result["visually_reviewed"] = result["visually_reviewed"] or proof.get("visually_reviewed", False) and result["proof_matches_captures"]
        result["artifact_relation"] = "stale_captures" if not result["proof_matches_captures"] else "matches_sha256" if proof.get("artifact_sha256") == current_artifact.get("sha256") else "different_sha256"
        result["review_notes"] = proof.get("review_notes", "")
        if proof.get("original_output_directory"):
            provenance = validated_run_record(path, {"sha256": proof.get("artifact_sha256")}, proof["original_output_directory"])
            result["provenance_matches"] = bool(provenance) and all(proof.get(key) == value for key, value in provenance.items())
            if result["provenance_matches"] and result["proof_matches_captures"]:
                result.update(process_exit="successful", original_output_directory=proof["original_output_directory"], run_record=provenance["run_record"])
            else:
                result.update(artifact_relation="stale_provenance", visually_reviewed=False)
    pixels = path / "pixels.txt"
    if pixels.is_file():
        fractions = [float(value) for value in re.findall(r"sky_fraction=([0-9.]+)", pixels.read_text())]
        result["minimum_clear_sky_fraction"] = min(fractions) if fractions else None
        result["pixel_check_applicable"] = result.get("kind") in ("vanilla_movement", "iris_off_movement")
        if result["pixel_check_applicable"]:
            result["pixel_checks_passed"] = len(fractions) >= 4 and min(fractions) >= 0.995
            if result["status"] == "completed" and not result["pixel_checks_passed"]:
                result.update(status="failed", failure="Movement capture contains fallback ceiling pixels")
    visual_result = path / "visual-review-result.json"
    if visual_result.is_file():
        inspection = json.loads(visual_result.read_text())
        result["visual_review_result"] = relative(visual_result)
        result["visual_review_matches_captures"] = inspection.get("captures_sha256") == {p.name: digest(p) for p in images}
        if result["visual_review_matches_captures"]:
            result["visual_review_status"] = inspection["result"]
            result["review_notes"] = inspection.get("review_notes", "")
            result["visually_reviewed"] = inspection["result"] == "passed"
            if inspection["result"] == "failed":
                result.update(status="failed", failure="Visual inspection rejected the rendering: " + result["review_notes"])
    return result


def dependency_metadata(version, props):
    result = {"loader": props["loader_version"], "fabric_api": props["fabric_version"], "iris": None, "sodium": None}
    prior = ROOT / "compatibility.json"
    if prior.is_file():
        previous = next((row for row in json.loads(prior.read_text()).get("targets", []) if row["minecraft"] == version), {})
        for name in ("iris", "sodium"):
            saved = previous.get("dependencies", {}).get(name)
            if saved and (not props.get(name + "_version_id") or props[name + "_version_id"] == saved.get("modrinth_version_id")):
                result[name] = saved
    mid_path = ROOT / ".tooling/compat/mid/versions.json"
    if mid_path.is_file():
        mid = json.loads(mid_path.read_text()).get(version, {})
        for name in ("iris", "sodium"):
            identifier = props.get(name + "_version_id")
            if identifier and identifier == mid.get(name + "_version_id"):
                result[name] = {"version": mid[name + "_version"], "modrinth_version_id": identifier, "source": f"https://api.modrinth.com/v2/version/{identifier}"}
    runtime = ROOT / ".tooling/compat/26x/deps/runtime-ready.json"
    if runtime.is_file():
        for row in json.loads(runtime.read_text()):
            if row["minecraft"] == version:
                for name in ("iris", "sodium"):
                    result[name] = {"version": row[name]["version"], "modrinth_version_id": row[name]["id"], "source": f"https://api.modrinth.com/v2/version/{row[name]['id']}"}
    runtime_mid = ROOT / ".tooling/compat/mid/deps/runtime-ready.json"
    if runtime_mid.is_file():
        for row in json.loads(runtime_mid.read_text()):
            if row["minecraft"] == version:
                for name in ("iris", "sodium"):
                    value = row[name]
                    result[name] = {"version": value["version"], "modrinth_version_id": value["id"],
                                    "source": f"https://api.modrinth.com/v2/version/{value['id']}",
                                    "jar_sha256": value["sha256"], "depends": value["depends"],
                                    "author_game_versions": value["author_game_versions"]}
    for base in (ROOT / f".tooling/compat/modern121/{version}", ROOT / ".tooling/compat/legacy"):
        for name in ("iris", "sodium"):
            for path in list(base.glob(name + "-versions.json")) + list(base.glob(version + "-" + name + ".json")):
                rows = json.loads(path.read_text())
                rows = rows if isinstance(rows, list) else [rows]
                identifier = props.get(name + "_version_id")
                row = next((r for r in rows if r.get("id") == identifier), None)
                if row is None and len(rows) == 1 and base.name == "legacy":
                    row = rows[0]
                if row:
                    identifier = row["id"]
                    result[name] = {"version": row["version_number"], "modrinth_version_id": identifier, "source": f"https://api.modrinth.com/v2/version/{identifier}"}
    selected_path = ROOT / f".tooling/compat/modern121/{version}/mods.json"
    if selected_path.is_file():
        selected = json.loads(selected_path.read_text())
        for name in ("iris", "sodium"):
            row = selected[name]
            result[name] = {"version": row["version"], "modrinth_version_id": row["id"], "source": f"https://api.modrinth.com/v2/version/{row['id']}"}
    if version == "26.3":
        for name, path in (("iris", ROOT / ".tooling/references/iris-26.3.jar"), ("sodium", ROOT / ".tooling/mc263/sodium-fabric-0.9.2+mc26.3.jar")):
            if path.is_file():
                with zipfile.ZipFile(path) as jar:
                    result[name] = {"version": json.loads(jar.read("fabric.mod.json"))["version"], "source": "https://modrinth.com/mod/" + name + "/versions?g=26.3"}
    return result


def record_proof(version, kind, directory, reviewed, notes, run_log, original_output):
    project = ROOT / "ports" / ("mc" + version)
    path = (ROOT / directory).resolve()
    path.relative_to(project.resolve())
    if kind in ("shader", "resource") and "movement" in path.name:
        kind += "_movement"
    current_artifact = artifact(project, version)
    if current_artifact["status"] != "valid":
        raise ValueError("A valid current production JAR is required to bind a proof")
    observed = capture_directory(path, current_artifact)
    if observed.get("kind") != kind:
        raise ValueError("The requested proof kind does not match the capture profile")
    provenance = validated_run_record(path, current_artifact)
    if original_output:
        original = (ROOT / original_output).resolve()
        original.relative_to(project.resolve())
        provenance = validated_run_record(path, current_artifact, relative(original))
        if not provenance:
            raise ValueError("Moved captures require matching original client output, clean queue exit, image names/timing, runtime, and current artifact hash")
        observed["process_exit"] = "successful"
    if observed["status"] != "completed":
        raise ValueError("The capture run has not completed successfully")
    if (path / "COMPLETE.txt").stat().st_mtime < (ROOT / current_artifact["path"]).stat().st_mtime:
        raise ValueError("The captures predate the current JAR; preserve their historical review instead of rebinding without source equivalence evidence")
    if kind.endswith("movement") and observed.get("expected_captures") != 4:
        raise ValueError("A movement proof requires four completed scenarios")
    if kind in ("vanilla_movement", "iris_off_movement") and not observed.get("pixel_checks_passed"):
        raise ValueError("A movement proof requires four successful pixel checks")
    if kind in ("vanilla_static", "iris_off_static", "shader", "resource") and observed.get("expected_captures", 0) < 22:
        raise ValueError("A static compatibility proof requires all 22 scenarios")
    if run_log:
        log = (ROOT / run_log).resolve()
        text = log.read_text(errors="replace")
        if "BUILD SUCCESSFUL" not in text or FATAL.search(text):
            raise ValueError("The client run log does not establish a successful exit")
        if not log_matches_capture(text, path):
            raise ValueError("The client run log does not belong to the requested capture directory")
    elif kind.endswith("movement") and observed.get("process_exit") != "successful":
        raise ValueError("Movement proof needs a successful client exit; provide --run-log if automatic discovery cannot find it")
    images = sorted(p for p in path.glob("[0-9][0-9]-*.png") if "-frame-" not in p.name)
    value = {"minecraft": version, "kind": kind, "artifact_sha256": current_artifact["sha256"],
             "settings_mode": observed["settings_mode"],
             "captures_sha256": {p.name: digest(p) for p in images}, "process_exit": "successful" if run_log else observed.get("process_exit", "unverified"),
             "recorded_at": datetime.now(timezone.utc).isoformat(), "visually_reviewed": reviewed, "review_notes": notes}
    if run_log:
        value["run_log"] = relative(log)
    if provenance:
        value.update(provenance)
    for name in ("shader_pack", "shader_profile", "effective_resource_packs"):
        if name in observed:
            value[name] = observed[name]
    (path / "compatibility-proof.json").write_text(json.dumps(value, indent=2) + "\n")


def record_visual_review(version, directory, result, notes):
    project = ROOT / "ports" / ("mc" + version)
    path = (ROOT / directory).resolve()
    path.relative_to(project.resolve())
    images = sorted(p for p in path.glob("[0-9][0-9]-*.png") if "-frame-" not in p.name)
    if not images:
        raise ValueError("A visual review requires captured images")
    record = path / "run-record.json"
    proof = path / "compatibility-proof.json"
    provenance = json.loads(record.read_text()) if record.is_file() else json.loads(proof.read_text()) if proof.is_file() else {}
    value = {"minecraft": version, "result": result, "artifact_sha256": provenance.get("artifact_sha256"),
             "captures_sha256": {p.name: digest(p) for p in images}, "recorded_at": datetime.now(timezone.utc).isoformat(),
             "review_notes": notes}
    (path / "visual-review-result.json").write_text(json.dumps(value, indent=2) + "\n")


def render_status(captures, kind, manual=False):
    relevant = [row for row in captures if (row.get("kind") == kind or kind == "vanilla_static" and row["directory"].endswith("/render-test"))
                and (not kind.endswith("movement") or (row.get("settings_mode") == "manual") == manual)]
    if not relevant:
        return "Pending"
    row = max(relevant, key=lambda value: (value.get("artifact_relation") == "matches_sha256", value.get("completed_at", ""), value["status"] == "completed"))
    if row["status"] != "completed":
        return row["status"].capitalize()
    if kind in ("vanilla_movement", "iris_off_movement") and not row.get("pixel_checks_passed"):
        return "Pixel check failed"
    suffix = "; reviewed" if row["visually_reviewed"] else "; review pending"
    binding = "; current JAR" if row.get("artifact_relation") == "matches_sha256" else "; JAR binding pending"
    return str(row["captures"]) + " captures" + suffix + binding


def write_docs(manifest):
    lines = ["# Minecraft compatibility", "", "Install the JAR for your exact Minecraft Java version on the client and server. Install Fabric Loader and the matching Fabric API separately. Each target has its own build; one JAR does not cover every release.", "", "This inventory covers the 23 stable releases from 1.20.1 through 26.3. Snapshots, prereleases, Forge, and NeoForge are outside this matrix.", "", "This repository publishes the current source snapshot. A finished compatibility ZIP is not included. The audit below records existing local build artifacts and evidence; artifact metadata and hashes describe those builds, while source metadata names How1Come as author. Rebuild the target project to obtain a JAR from the published source.", "", "## Builds and verification", "", "A valid JAR means its metadata, Java bytecode version, and contents passed the package audit. Server tests exercise lighting and settings. Completed captures are recorded separately from visual review and from their association with a release hash. Development clients load compiled production output; the proof records the corresponding release artifact rather than claiming the client loaded that JAR. Walking checks exercise view bobbing and a chunk rebuild; stationary captures do not establish that behavior.", "", "| Minecraft | Java | Fabric Loader | Fabric API | JAR audit | Server tests | Vanilla static | Vanilla walking | Walking with Iris, shaders off |", "| --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for row in manifest["targets"]:
        tests = row["server_tests"]
        count = str(tests["required_tests"]) + " passed" if tests["status"] == "passed" else "Pending"
        deps = row["dependencies"]
        lines.append(f"| {row['minecraft']} | {row['artifact'].get('java', row['java'])} | {deps['loader']} | {deps['fabric_api']} | {row['artifact']['status']} | {count} | {render_status(row['captures'], 'vanilla_static')} | {render_status(row['captures'], 'vanilla_movement')} | {render_status(row['captures'], 'iris_off_movement')} |")
    lines += ["", "## Resource packs and shader packs", "", "Follow world mode reveals Minecraft's native sky and clouds, including changes made by the installed resource pack and supported Iris shader pipeline. Manual time/weather mode uses the installed sun, moon, cloud, rain, and snow textures with the mod's own atmosphere. Manual mode does not run a shader pack's custom sky or volumetric-cloud programs.", "", "The dependency pairs below are prepared test configurations. Availability or compilation does not establish visual compatibility. Only the explicit reviewed capture status establishes a tested configuration. Pack behavior can differ by GPU, pack settings, and pack version.", "", "| Minecraft | Iris | Sodium | Shader static | Shader walking | Resource static | Resource walking |", "| --- | --- | --- | --- | --- | --- | --- |"]
    for row in manifest["targets"]:
        deps = row["dependencies"]
        values = [f"[{deps[name]['version']}]({deps[name]['source']})" if deps[name] else "Not recorded" for name in ("iris", "sodium")]
        lines.append(f"| {row['minecraft']} | {values[0]} | {values[1]} | {render_status(row['captures'], 'shader')} | {render_status(row['captures'], 'shader_movement')} | {render_status(row['captures'], 'resource')} | {render_status(row['captures'], 'resource_movement')} |")
    configurations = sorted({(capture["shader_pack"], capture.get("shader_profile", "Not recorded").partition(" (")[0])
                             for row in manifest["targets"] for capture in row["captures"]
                             if capture.get("kind") in ("shader", "shader_movement") and capture["status"] == "completed"
                             and capture["visually_reviewed"] and capture.get("shader_pack")})
    if configurations:
        lines += ["", "Reviewed enabled-shader captures use " + "; ".join(f"`{pack}` with profile `{profile}`" for pack, profile in configurations) + "."]
    lines += ["", "Resource captures use the generated `sky-window-test.zip` replacement-sun fixture. They verify visible texture substitution; they do not establish arbitrary custom-sky resource-pack behavior.", "", "## Manual sky while walking", "", "These captures use manual noon/Sunny on the ceiling while the real world remains unchanged. They exercise the manual atmosphere renderer, which is separate from the native sky revealed in follow world mode. A clean client exit does not establish correct depth alignment; image review is required.", "", "| Minecraft | Vanilla | Iris, shaders off | Iris, shaders on | Resource pack |", "| --- | --- | --- | --- | --- |"]
    for row in manifest["targets"]:
        if any(capture.get("settings_mode") == "manual" for capture in row["captures"]):
            values = [render_status(row["captures"], kind, manual=True) for kind in ("vanilla_movement", "iris_off_movement", "shader_movement", "resource_movement")]
            lines.append(f"| {row['minecraft']} | " + " | ".join(values) + " |")
    lines += ["", "Iris and Sodium are client dependencies for the optional shader path. They are not bundled in the mod or the compatibility ZIP. Use the version pair for your target; some author metadata lists a patch release in the filename while permitting adjacent patch releases.", "", "Executed GPU checks use macOS with OpenGL and an integrated server. Vulkan, remote multiplayer, and arbitrary shader or resource packs remain unverified. Manual mode does not reproduce shader-pack-specific atmosphere.", "", "## Release audit", "", "[compatibility.json](../compatibility.json) records exact filenames, SHA256 values, Java class versions, dependency metadata, and evidence paths. The audit rejects Minecraft dependency ranges, test mods, nested dependency JARs, and bundled Iris or Sodium classes, and parses every JSON and mcmeta file in each release JAR.", "", "Evidence and project paths refer to the source checkout; they are not included in the install ZIP. Release packaging requires a valid production JAR, passing server tests, and 22 visually reviewed vanilla captures for every target, plus clean, visually reviewed 26.3 walking runs for follow world mode with Iris installed and shaders disabled and for manual mode with Iris shaders enabled. Both walking proofs must match the current release hash.", "", "Static and walking captures cover different scenarios. The vanilla blue-sky pixel classifier is used only with vanilla rendering or disabled shaders; pack-specific colors require image inspection. Failed visual inspections are bound to image digests and reject release proofs even when the client process exits cleanly.", "", "Regenerate the inventory with `python3 scripts/package-compatibility.py`. Bind a completed, current run with `--record-proof VERSION KIND ports/mcVERSION/build/OUTPUT`; add `--reviewed --notes TEXT` only after inspecting the images. Record a rejected inspection with `--record-visual-review VERSION failed ports/mcVERSION/build/OUTPUT --notes TEXT`. Create the final ZIP with `--package` after the release gates pass.", "", "Metadata sources: [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), [Fabric Loader metadata](https://meta.fabricmc.net/v2/versions/loader), [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml), and the authors' linked Modrinth version metadata."]
    (ROOT / "docs/compatibility.md").write_text("\n".join(lines) + "\n")


def package(manifest):
    missing = [row["minecraft"] for row in manifest["targets"] if row["artifact"]["status"] != "valid" or row["server_tests"]["status"] != "passed" or row["server_tests"].get("required_tests", 0) < 23 or not any(c["status"] == "completed" and c.get("expected_captures", 0) >= 22 and c["visually_reviewed"] and (c.get("kind") == "vanilla_static" or c["directory"].endswith("/render-test")) for c in row["captures"])]
    modern = next(row for row in manifest["targets"] if row["minecraft"] == "26.3")
    movement = any(c.get("kind") == "iris_off_movement" and c.get("settings_mode") == "follow_world" and c["status"] == "completed" and c.get("process_exit") == "successful" and c.get("pixel_checks_passed") and c["visually_reviewed"] and c.get("artifact_relation") == "matches_sha256" for c in modern["captures"])
    manual = any(c.get("kind") == "shader_movement" and c.get("settings_mode") == "manual" and c["status"] == "completed" and c.get("expected_captures") == 4 and c.get("process_exit") == "successful" and c["visually_reviewed"] and c.get("artifact_relation") == "matches_sha256" for c in modern["captures"])
    if missing or not movement or not manual:
        raise ValueError("Release gates are pending: " + (", ".join(missing) if missing else "26.3 current-JAR follow world and manual walking proofs and review"))
    destination = ROOT / "dist/sky-window-compatibility-1.20.1-to-26.3.zip"
    destination.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for row in manifest["targets"]:
            path = ROOT / row["artifact"]["path"]
            archive.write(path, "mods/" + row["minecraft"] + "/" + path.name)
        for name in ("compatibility.json", "docs/compatibility.md", "INSTALL.md", "LICENSE"):
            archive.write(ROOT / name, name)
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise ValueError("The compatibility archive failed its integrity check")
        expected_names = {"mods/" + row["minecraft"] + "/" + Path(row["artifact"]["path"]).name for row in manifest["targets"]}
        expected_names.update(("compatibility.json", "docs/compatibility.md", "INSTALL.md", "LICENSE"))
        if set(archive.namelist()) != expected_names or len(archive.namelist()) != len(expected_names):
            raise ValueError("The compatibility archive contains unexpected or duplicate files")
        if json.loads(archive.read("compatibility.json")) != manifest:
            raise ValueError("The packaged inventory differs from the audited inventory")
        for row in manifest["targets"]:
            path = ROOT / row["artifact"]["path"]
            name = "mods/" + row["minecraft"] + "/" + path.name
            if hashlib.sha256(archive.read(name)).hexdigest() != row["artifact"]["sha256"]:
                raise ValueError("Packaged JAR differs from the audited artifact: " + row["minecraft"])
    (destination.parent / (destination.name + ".sha256")).write_text(digest(destination) + "  " + destination.name + "\n")
    print(relative(destination))


def write_install():
    text = """# Install Sky Window

This repository contains the current source snapshot. Build the project for your exact Minecraft version; a finished compatibility ZIP is not included.

1. Select your exact Minecraft Java version and required Java runtime from the table below.
2. Open `ports/mcVERSION/` and run `./gradlew build` on macOS/Linux or `gradlew.bat build` on Windows. The root project is a separate 1.21.1 development baseline.
3. Install Fabric Loader and Fabric API for that exact Minecraft version. Use the dependency versions listed in `docs/compatibility.md`.
4. Copy exactly one matching `sky-window-*.jar` from the target project's `build/libs/` into your Minecraft instance's `mods` folder. Copy the matching Fabric API JAR there too. Keep other Sky Window version JARs out of that instance.
5. Install the same matching Sky Window JAR and Fabric API on the server when playing multiplayer, then launch the Fabric profile.

If a compatibility ZIP is packaged separately, select the JAR from its `mods/VERSION/` directory instead of building from source.

For source builds, launch Gradle with JDK 17 for 1.20.1–1.20.4, JDK 21 for 1.20.5–1.21.4, and JDK 25 for 1.21.5–26.3. The 1.21.5–1.21.11 builds still produce Java 21 bytecode for the game runtime.

| Minecraft | Java runtime |
| --- | --- |
| 1.20.1 through 1.20.4 | 17 |
| 1.20.5 through 1.21.11 | 21 |
| 26.1 through 26.3 | 25 |

Find Sky Window in the Functional Blocks creative tab, or use `/give @s sky_window:sky_window 64`. Place the blocks in a ceiling and look up from below. Hold Shift and right-click a block to open its settings. Change the modifier in Minecraft's Controls settings. Apply updates all connected Sky Window blocks while leaving the real world's time and weather unchanged. Room skylight follows the real time and weather even when the ceiling displays a manual preset.

Connected ceilings join through shared block faces. All connected blocks and their neighboring chunks must be loaded. Apply rejects the whole edit if a block is protected or the ceiling exceeds 16,384 blocks.

Iris and Sodium are optional client mods and are not included. For shaders, install the matching pair listed in `docs/compatibility.md`, put your shader pack in the instance's `shaderpacks` folder, and enable it in Iris. Install resource packs through Minecraft's Resource Packs screen.

`docs/compatibility.md` distinguishes completed captures, image review, walking checks, and association with the current release JAR. Follow world mode uses the native sky and clouds. Manual time/weather mode uses resource pack textures with the mod's atmosphere; it does not run shader pack sky or volumetric-cloud programs.

`compatibility.json` records the previously audited local artifacts and their SHA256 values. A fresh source build has its own hash. Evidence paths refer to the development checkout; ignored build outputs and runtime data are not part of the source snapshot. A separately packaged compatibility ZIP includes a SHA256 sidecar.
"""
    (ROOT / "INSTALL.md").write_text(text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", action="store_true")
    parser.add_argument("--record-proof", nargs=3, metavar=("VERSION", "KIND", "DIRECTORY"))
    parser.add_argument("--record-visual-review", nargs=3, metavar=("VERSION", "RESULT", "DIRECTORY"))
    parser.add_argument("--reviewed", action="store_true")
    parser.add_argument("--notes", default="")
    parser.add_argument("--run-log")
    parser.add_argument("--original-output-directory")
    args = parser.parse_args()
    if args.record_visual_review:
        version, result, directory = args.record_visual_review
        if result not in ("passed", "failed"):
            raise ValueError("Visual review result must be passed or failed")
        record_visual_review(version, directory, result, args.notes)
    if args.record_proof:
        version, kind, directory = args.record_proof
        if kind not in KINDS:
            raise ValueError("Proof kind must be one of " + ", ".join(KINDS))
        record_proof(version, kind, directory, args.reviewed, args.notes, args.run_log, args.original_output_directory)
    targets = json.loads(TARGETS.read_text())
    if len(targets) != 23 or len({row["minecraft"] for row in targets}) != 23:
        raise ValueError("The target inventory must contain 23 unique stable releases")
    rows = []
    for target in targets:
        version = target["minecraft"]
        project = ROOT / "ports" / ("mc" + version)
        props = properties(project / "gradle.properties")
        current_artifact = artifact(project, version)
        captures = [capture_directory(path, current_artifact) for path in sorted(project.glob("build/render-test*")) if path.is_dir()]
        rows.append({"minecraft": version, "java": target["java"], "mojang_metadata": target["url"],
                     "project": relative(project), "dependencies": dependency_metadata(version, props), "artifact": current_artifact,
                     "build": build_status(project, version), "server_tests": server_tests(project, version), "captures": captures})
    manifest = {"schema_version": 1, "generated_at": datetime.now(timezone.utc).isoformat(), "loader": "fabric", "target_count": 23, "targets": rows}
    (ROOT / "compatibility.json").write_text(json.dumps(manifest, indent=2) + "\n")
    write_docs(manifest)
    write_install()
    valid = sum(row["artifact"]["status"] == "valid" for row in rows)
    passed = sum(row["server_tests"]["status"] == "passed" for row in rows)
    print(f"Audited {valid}/23 production JARs; observed {passed}/23 server test runs. No ZIP is created without --package.")
    if args.package:
        package(manifest)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as failure:
        print(str(failure), file=sys.stderr)
        sys.exit(1)

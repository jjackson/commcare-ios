from pathlib import Path
from unittest.mock import patch, MagicMock
from pipeline.generate_tasks import generate_phase1_tasks


def test_generate_phase1_tasks_dry_run(tmp_path):
    # Create minimal fake Java project
    pkg = tmp_path / "src/main/java/org/example/util"
    pkg.mkdir(parents=True)
    (pkg / "Helper.java").write_text(
        "package org.example.util;\npublic class Helper { }\n"
    )

    results = generate_phase1_tasks(
        source_root=tmp_path / "src/main/java",
        repo="test/repo",
        dry_run=True,
    )

    assert len(results) == 1
    assert results[0]["dry_run"] is True
    assert "org.example.util" in results[0]["title"]

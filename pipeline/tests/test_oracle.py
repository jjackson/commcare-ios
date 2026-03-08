from pathlib import Path
from pipeline.oracle import OracleFixture, OracleHarness, ComparisonResult


def test_oracle_fixture_serialization(tmp_path):
    fixture = OracleFixture(
        app_id="basic",
        scenario="navigate_menu_0_form_0",
        inputs={"selections": ["0", "0"], "answers": {"0": "test"}},
        expected_outputs={
            "questions": [{"type": "text", "text": "Name?"}],
            "submission_xml": "<data><name>test</name></data>",
        },
    )
    fixture_path = tmp_path / "fixture.json"
    fixture.save(fixture_path)

    loaded = OracleFixture.load(fixture_path)
    assert loaded.app_id == "basic"
    assert loaded.expected_outputs["submission_xml"] == "<data><name>test</name></data>"


def test_comparison_result_pass():
    result = ComparisonResult(
        fixture_id="basic/navigate_menu_0",
        passed=True,
        differences=[],
    )
    assert result.passed is True


def test_comparison_result_fail():
    result = ComparisonResult(
        fixture_id="basic/navigate_menu_0",
        passed=False,
        differences=["Question text mismatch: expected 'Name?' got 'First Name?'"],
    )
    assert result.passed is False
    assert len(result.differences) == 1


def test_oracle_harness_compare(tmp_path):
    fixture = OracleFixture(
        app_id="basic",
        scenario="test_scenario",
        inputs={"answer": "hello"},
        expected_outputs={"result": "hello_processed"},
    )

    harness = OracleHarness(fixtures_dir=tmp_path)
    result = harness.compare(
        fixture=fixture,
        actual_outputs={"result": "hello_processed"},
    )
    assert result.passed is True

    result = harness.compare(
        fixture=fixture,
        actual_outputs={"result": "different"},
    )
    assert result.passed is False
    assert len(result.differences) > 0

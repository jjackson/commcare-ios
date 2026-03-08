import json
from pathlib import Path
from pydantic import BaseModel


class OracleFixture(BaseModel):
    app_id: str
    scenario: str
    inputs: dict
    expected_outputs: dict

    def save(self, path: Path) -> None:
        path.write_text(self.model_dump_json(indent=2))

    @classmethod
    def load(cls, path: Path) -> "OracleFixture":
        return cls.model_validate_json(path.read_text())


class ComparisonResult(BaseModel):
    fixture_id: str
    passed: bool
    differences: list[str]


class OracleHarness:
    def __init__(self, fixtures_dir: Path):
        self.fixtures_dir = fixtures_dir

    def compare(
        self,
        fixture: OracleFixture,
        actual_outputs: dict,
    ) -> ComparisonResult:
        differences = []
        fixture_id = f"{fixture.app_id}/{fixture.scenario}"

        self._compare_dicts(
            expected=fixture.expected_outputs,
            actual=actual_outputs,
            path="",
            differences=differences,
        )

        return ComparisonResult(
            fixture_id=fixture_id,
            passed=len(differences) == 0,
            differences=differences,
        )

    def _compare_dicts(
        self,
        expected: dict,
        actual: dict,
        path: str,
        differences: list[str],
    ) -> None:
        for key in expected:
            full_path = f"{path}.{key}" if path else key
            if key not in actual:
                differences.append(f"Missing key: {full_path}")
                continue

            exp_val = expected[key]
            act_val = actual[key]

            if isinstance(exp_val, dict) and isinstance(act_val, dict):
                self._compare_dicts(exp_val, act_val, full_path, differences)
            elif isinstance(exp_val, list) and isinstance(act_val, list):
                if len(exp_val) != len(act_val):
                    differences.append(
                        f"List length mismatch at {full_path}: "
                        f"expected {len(exp_val)}, got {len(act_val)}"
                    )
                else:
                    for i, (e, a) in enumerate(zip(exp_val, act_val)):
                        if e != a:
                            differences.append(
                                f"Mismatch at {full_path}[{i}]: "
                                f"expected {e!r}, got {a!r}"
                            )
            elif exp_val != act_val:
                differences.append(
                    f"Value mismatch at {full_path}: "
                    f"expected {exp_val!r}, got {act_val!r}"
                )

    def load_fixtures(self, app_id: str | None = None) -> list[OracleFixture]:
        fixtures = []
        for fixture_file in self.fixtures_dir.rglob("*.json"):
            fixture = OracleFixture.load(fixture_file)
            if app_id is None or fixture.app_id == app_id:
                fixtures.append(fixture)
        return fixtures

    def run_comparison_suite(
        self,
        actual_outputs_by_scenario: dict[str, dict],
    ) -> list[ComparisonResult]:
        fixtures = self.load_fixtures()
        results = []
        for fixture in fixtures:
            scenario_key = f"{fixture.app_id}/{fixture.scenario}"
            if scenario_key in actual_outputs_by_scenario:
                result = self.compare(
                    fixture=fixture,
                    actual_outputs=actual_outputs_by_scenario[scenario_key],
                )
                results.append(result)
        return results

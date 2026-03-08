from enum import Enum
from pathlib import Path
import yaml
from pydantic import BaseModel


class OracleType(str, Enum):
    FORMPLAYER = "formplayer"
    ANDROID = "android"
    SPEC_ONLY = "spec_only"


class TestMode(str, Enum):
    OUTPUT_MATCH = "output_match"
    ANDROID_PARITY = "android_parity"
    SPEC_CONFORMANCE = "spec_conformance"


class BehaviorEntry(BaseModel):
    id: str
    description: str
    oracle: OracleType
    category: str
    test_mode: TestMode
    known_differences: list[str]


class BehaviorCatalog(BaseModel):
    behaviors: list[BehaviorEntry]

    @classmethod
    def from_yaml(cls, path: Path) -> "BehaviorCatalog":
        with open(path) as f:
            data = yaml.safe_load(f)
        return cls(behaviors=[BehaviorEntry(**b) for b in data["behaviors"]])

    def get(self, behavior_id: str) -> BehaviorEntry | None:
        for b in self.behaviors:
            if b.id == behavior_id:
                return b
        return None

    def filter_by_oracle(self, oracle: OracleType) -> list[BehaviorEntry]:
        return [b for b in self.behaviors if b.oracle == oracle]

from pathlib import Path
from pydantic import BaseModel


class PipelineConfig(BaseModel):
    repo_root: Path
    commcare_core_path: Path
    max_parallel_agents: int = 3
    github_repo: str = ""
    auto_merge_enabled: bool = False

    @classmethod
    def from_env(cls, repo_root: Path | None = None) -> "PipelineConfig":
        root = repo_root or Path.cwd()
        return cls(
            repo_root=root,
            commcare_core_path=root.parent / "commcare-core",
        )

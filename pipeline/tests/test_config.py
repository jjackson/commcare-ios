from pathlib import Path
from pipeline.config import PipelineConfig


def test_config_defaults():
    config = PipelineConfig(
        repo_root=Path("/tmp/test"),
        commcare_core_path=Path("/tmp/commcare-core"),
    )
    assert config.max_parallel_agents == 3
    assert config.auto_merge_enabled is False


def test_config_from_env(tmp_path):
    config = PipelineConfig.from_env(repo_root=tmp_path)
    assert config.repo_root == tmp_path
    assert config.commcare_core_path == tmp_path.parent / "commcare-core"

from enum import Enum
from pydantic import BaseModel


class TaskPhase(str, Enum):
    SCAFFOLD = "scaffold"
    CORE_PORT = "core_port"
    APP_SHELL = "app_shell"
    FEATURES = "features"
    POLISH = "polish"


class TaskStatus(str, Enum):
    PENDING = "pending"
    READY = "ready"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskSpec(BaseModel):
    id: str
    title: str
    phase: TaskPhase
    wave: int
    description: str
    files_to_read: list[str]
    files_to_modify: list[str]
    test_criteria: list[str]
    depends_on: list[str]
    labels: list[str]
    status: TaskStatus = TaskStatus.PENDING

    def to_github_issue_body(self) -> str:
        sections = []

        sections.append(f"## Context\n\n{self.description}")

        files_read = "\n".join(f"- `{f}`" for f in self.files_to_read)
        sections.append(f"## Files to Read\n\n{files_read}")

        sections.append(
            "## What to Do\n\n"
            "1. Read the files listed above to understand the current implementation.\n"
            "2. Port the Java code to idiomatic Kotlin.\n"
            "3. Run the tests listed below and ensure they all pass.\n"
            "4. Create a PR with the changes."
        )

        files_modify = "\n".join(f"- `{f}`" for f in self.files_to_modify)
        sections.append(f"## Files to Modify\n\n{files_modify}")

        tests = "\n".join(f"- [ ] {t}" for t in self.test_criteria)
        sections.append(f"## Tests That Must Pass\n\n{tests}")

        if self.depends_on:
            deps = "\n".join(f"- #{d}" for d in self.depends_on)
            sections.append(f"## Dependencies\n\nThis task depends on:\n{deps}")
        else:
            sections.append("## Dependencies\n\nNone — this task can start immediately.")

        metadata = (
            f"**Phase:** {self.phase.value} | "
            f"**Wave:** {self.wave} | "
            f"**ID:** {self.id}"
        )
        sections.append(f"## Metadata\n\n{metadata}")

        return "\n\n".join(sections)


class WaveAssignment(BaseModel):
    wave_number: int
    task_ids: list[str]
    estimated_parallelism: int

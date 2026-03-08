from pipeline.models import TaskSpec, TaskPhase, TaskStatus, WaveAssignment


def test_task_spec_creation():
    task = TaskSpec(
        id="core-port-javarosa-util",
        title="Port org.javarosa.core.util to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=1,
        description="Convert all Java files in org.javarosa.core.util to Kotlin.",
        files_to_read=["src/main/java/org/javarosa/core/util/*.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/util/*.kt"],
        test_criteria=["All existing tests in org.javarosa.core.util pass"],
        depends_on=[],
        labels=["phase/1", "wave/1", "auto-merge"],
    )
    assert task.phase == TaskPhase.CORE_PORT
    assert task.wave == 1
    assert len(task.depends_on) == 0


def test_task_spec_with_dependencies():
    task = TaskSpec(
        id="core-port-javarosa-model",
        title="Port org.javarosa.core.model to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=2,
        description="Convert core model classes.",
        files_to_read=["src/main/java/org/javarosa/core/model/*.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/model/*.kt"],
        test_criteria=["All existing model tests pass"],
        depends_on=["core-port-javarosa-util", "core-port-javarosa-data"],
        labels=["phase/1", "wave/2"],
    )
    assert len(task.depends_on) == 2
    assert task.status == TaskStatus.PENDING


def test_wave_assignment():
    wave = WaveAssignment(
        wave_number=1,
        task_ids=["a", "b", "c"],
        estimated_parallelism=3,
    )
    assert wave.wave_number == 1
    assert len(wave.task_ids) == 3


def test_task_to_github_issue_body():
    task = TaskSpec(
        id="core-port-javarosa-util",
        title="Port org.javarosa.core.util to Kotlin",
        phase=TaskPhase.CORE_PORT,
        wave=1,
        description="Convert all Java files in org.javarosa.core.util to Kotlin.",
        files_to_read=["src/main/java/org/javarosa/core/util/ArrayUtilities.java"],
        files_to_modify=["src/main/kotlin/org/javarosa/core/util/ArrayUtilities.kt"],
        test_criteria=[
            "org.javarosa.core.util.ArrayUtilitiesTest passes",
            "No compilation errors in dependent packages",
        ],
        depends_on=[],
        labels=["phase/1", "wave/1", "auto-merge"],
    )
    body = task.to_github_issue_body()
    assert "## Context" in body
    assert "## Files to Read" in body
    assert "## What to Do" in body
    assert "## Tests That Must Pass" in body
    assert "## Dependencies" in body
    assert "ArrayUtilities.java" in body

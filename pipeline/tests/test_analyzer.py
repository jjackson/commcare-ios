from pathlib import Path
from pipeline.analyzer import PackageAnalyzer, PackageInfo, DependencyGraph


def test_discover_packages(tmp_path):
    # Create fake Java source tree
    pkg_a = tmp_path / "src/main/java/org/example/core/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "StringUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class StringUtils { }\n"
    )
    (pkg_a / "MathUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class MathUtils { }\n"
    )

    pkg_b = tmp_path / "src/main/java/org/example/core/model"
    pkg_b.mkdir(parents=True)
    (pkg_b / "FormDef.java").write_text(
        "package org.example.core.model;\n"
        "import org.example.core.util.StringUtils;\n"
        "public class FormDef { }\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()

    assert len(packages) == 2
    names = {p.package_name for p in packages}
    assert "org.example.core.util" in names
    assert "org.example.core.model" in names


def test_analyze_dependencies(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/core/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "StringUtils.java").write_text(
        "package org.example.core.util;\n"
        "public class StringUtils { }\n"
    )

    pkg_b = tmp_path / "src/main/java/org/example/core/model"
    pkg_b.mkdir(parents=True)
    (pkg_b / "FormDef.java").write_text(
        "package org.example.core.model;\n"
        "import org.example.core.util.StringUtils;\n"
        "public class FormDef {\n"
        "    StringUtils utils = new StringUtils();\n"
        "}\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)

    model_deps = graph.dependencies_of("org.example.core.model")
    assert "org.example.core.util" in model_deps

    util_deps = graph.dependencies_of("org.example.core.util")
    assert len(util_deps) == 0


def test_assign_waves(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/a"
    pkg_a.mkdir(parents=True)
    (pkg_a / "A.java").write_text("package org.example.a;\npublic class A { }\n")

    pkg_b = tmp_path / "src/main/java/org/example/b"
    pkg_b.mkdir(parents=True)
    (pkg_b / "B.java").write_text(
        "package org.example.b;\nimport org.example.a.A;\npublic class B { }\n"
    )

    pkg_c = tmp_path / "src/main/java/org/example/c"
    pkg_c.mkdir(parents=True)
    (pkg_c / "C.java").write_text(
        "package org.example.c;\nimport org.example.b.B;\npublic class C { }\n"
    )

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    waves = graph.assign_waves()

    # a has no deps -> wave 1
    # b depends on a -> wave 2
    # c depends on b -> wave 3
    assert waves["org.example.a"] == 1
    assert waves["org.example.b"] == 2
    assert waves["org.example.c"] == 3


def test_generate_task_specs(tmp_path):
    pkg_a = tmp_path / "src/main/java/org/example/util"
    pkg_a.mkdir(parents=True)
    (pkg_a / "Helper.java").write_text("package org.example.util;\npublic class Helper { }\n")

    analyzer = PackageAnalyzer(source_root=tmp_path / "src/main/java")
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    tasks = analyzer.generate_task_specs(packages, graph)

    assert len(tasks) == 1
    task = tasks[0]
    assert task.phase.value == "core_port"
    assert "org.example.util" in task.title
    assert task.wave == 1


def _create_javarosa_tree(tmp_path):
    """Helper: create a fake Java source tree with javarosa/commcare packages."""
    src = tmp_path / "src/main/java"

    # javarosa-utilities group
    util_dir = src / "org/javarosa/core/util"
    util_dir.mkdir(parents=True)
    (util_dir / "ArrayUtils.java").write_text(
        "package org.javarosa.core.util;\npublic class ArrayUtils { }\n"
    )

    io_dir = src / "org/javarosa/core/io"
    io_dir.mkdir(parents=True)
    (io_dir / "StreamReader.java").write_text(
        "package org.javarosa.core.io;\n"
        "import org.javarosa.core.util.ArrayUtils;\n"
        "public class StreamReader { }\n"
    )

    # javarosa-model group
    model_dir = src / "org/javarosa/core/model"
    model_dir.mkdir(parents=True)
    (model_dir / "FormDef.java").write_text(
        "package org.javarosa.core.model;\n"
        "import org.javarosa.core.util.ArrayUtils;\n"
        "public class FormDef { }\n"
    )

    # xpath-engine group
    xpath_dir = src / "org/javarosa/xpath"
    xpath_dir.mkdir(parents=True)
    (xpath_dir / "XPathParser.java").write_text(
        "package org.javarosa.xpath;\n"
        "import org.javarosa.core.model.FormDef;\n"
        "public class XPathParser { }\n"
    )

    # case-management group
    cases_dir = src / "org/commcare/cases"
    cases_dir.mkdir(parents=True)
    (cases_dir / "CaseModel.java").write_text(
        "package org.commcare.cases;\npublic class CaseModel { }\n"
    )

    return src


def test_assign_functional_groups():
    packages = [
        PackageInfo("org.javarosa.core.util", [], Path(".")),
        PackageInfo("org.javarosa.core.io", [], Path(".")),
        PackageInfo("org.javarosa.core.model", [], Path(".")),
        PackageInfo("org.javarosa.core.model.data", [], Path(".")),
        PackageInfo("org.javarosa.xpath", [], Path(".")),
        PackageInfo("org.javarosa.xform.parse", [], Path(".")),
        PackageInfo("org.commcare.cases", [], Path(".")),
        PackageInfo("org.commcare.suite.model", [], Path(".")),
        PackageInfo("org.commcare.resources", [], Path(".")),
        PackageInfo("org.commcare.core.process", [], Path(".")),
    ]

    groups = PackageAnalyzer._assign_functional_groups(packages)

    assert "org.javarosa.core.util" in [p.package_name for p in groups["javarosa-utilities"]]
    assert "org.javarosa.core.io" in [p.package_name for p in groups["javarosa-utilities"]]
    assert "org.javarosa.core.model.data" in [p.package_name for p in groups["javarosa-utilities"]]
    assert "org.javarosa.core.model" in [p.package_name for p in groups["javarosa-model"]]
    assert "org.javarosa.xpath" in [p.package_name for p in groups["xpath-engine"]]
    assert "org.javarosa.xform.parse" in [p.package_name for p in groups["xform-parser"]]
    assert "org.commcare.cases" in [p.package_name for p in groups["case-management"]]
    assert "org.commcare.suite.model" in [p.package_name for p in groups["suite-and-session"]]
    assert "org.commcare.resources" in [p.package_name for p in groups["resources"]]
    assert "org.commcare.core.process" in [p.package_name for p in groups["commcare-core-services"]]


def test_generate_grouped_task_specs(tmp_path):
    src = _create_javarosa_tree(tmp_path)

    analyzer = PackageAnalyzer(source_root=src)
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    tasks = analyzer.generate_grouped_task_specs(packages, graph)

    # Should produce tasks for groups that have packages
    assert len(tasks) >= 3  # at least utilities, model, xpath, case-management

    # Tasks should be sorted by wave
    waves = [t.wave for t in tasks]
    assert waves == sorted(waves)

    # Each task ID should start with core-port-
    for task in tasks:
        assert task.id.startswith("core-port-")

    # Utilities group (wave 1) should have no dependencies
    util_task = next(t for t in tasks if "javarosa-utilities" in t.id)
    assert util_task.depends_on == []
    assert util_task.wave == 1

    # Model group (wave 2) should depend on utilities
    model_task = next(t for t in tasks if "javarosa-model" in t.id)
    assert "core-port-javarosa-utilities" in model_task.depends_on
    assert model_task.wave == 2

    # XPath group (wave 3) should depend on utilities and model
    xpath_task = next(t for t in tasks if "xpath-engine" in t.id)
    assert "core-port-javarosa-utilities" in xpath_task.depends_on
    assert "core-port-javarosa-model" in xpath_task.depends_on
    assert xpath_task.wave == 3


def test_grouped_tasks_include_file_paths(tmp_path):
    src = _create_javarosa_tree(tmp_path)

    analyzer = PackageAnalyzer(source_root=src)
    packages = analyzer.discover_packages()
    graph = analyzer.build_dependency_graph(packages)
    tasks = analyzer.generate_grouped_task_specs(packages, graph)

    util_task = next(t for t in tasks if "javarosa-utilities" in t.id)
    # Should have files_to_read populated
    assert len(util_task.files_to_read) > 0
    # Should have files_to_modify with .kt extension
    assert all(f.endswith(".kt") for f in util_task.files_to_modify)

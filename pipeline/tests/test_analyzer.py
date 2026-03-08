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

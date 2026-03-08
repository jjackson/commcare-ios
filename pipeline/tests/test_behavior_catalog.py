from pathlib import Path
from pipeline.behavior_catalog import BehaviorCatalog, BehaviorEntry, OracleType, TestMode


def test_load_catalog(tmp_path):
    catalog_file = tmp_path / "catalog.yaml"
    catalog_file.write_text("""
behaviors:
  - id: xpath_evaluation
    description: XPath expressions should evaluate identically
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []

  - id: auto_advance_single_select
    description: Mobile auto-advances after single select choice
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences:
      - "FormPlayer shows Next button; mobile auto-advances"
""")

    catalog = BehaviorCatalog.from_yaml(catalog_file)
    assert len(catalog.behaviors) == 2

    xpath = catalog.get("xpath_evaluation")
    assert xpath is not None
    assert xpath.oracle == OracleType.FORMPLAYER
    assert xpath.test_mode == TestMode.OUTPUT_MATCH

    auto_adv = catalog.get("auto_advance_single_select")
    assert auto_adv is not None
    assert auto_adv.oracle == OracleType.ANDROID
    assert len(auto_adv.known_differences) == 1


def test_catalog_filter_by_oracle(tmp_path):
    catalog_file = tmp_path / "catalog.yaml"
    catalog_file.write_text("""
behaviors:
  - id: a
    description: test
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []
  - id: b
    description: test
    oracle: android
    category: mobile_specific
    test_mode: android_parity
    known_differences: []
  - id: c
    description: test
    oracle: formplayer
    category: engine
    test_mode: output_match
    known_differences: []
""")

    catalog = BehaviorCatalog.from_yaml(catalog_file)
    fp_behaviors = catalog.filter_by_oracle(OracleType.FORMPLAYER)
    assert len(fp_behaviors) == 2
    android_behaviors = catalog.filter_by_oracle(OracleType.ANDROID)
    assert len(android_behaviors) == 1

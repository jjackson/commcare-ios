"""Tests for sim_verify — no real simulator needed; subprocess is mocked."""

from __future__ import annotations

from unittest.mock import patch, MagicMock

import pytest

import sim_verify


def _fake_run(stdout="", returncode=0):
    m = MagicMock()
    m.stdout = stdout
    m.returncode = returncode
    return m


class TestVisibleText:
    def test_empty(self):
        assert sim_verify.visible_text(None) == []
        assert sim_verify.visible_text({}) == []

    def test_flat(self):
        h = {"text": "Hello", "label": "World"}
        assert "Hello" in sim_verify.visible_text(h)
        assert "World" in sim_verify.visible_text(h)

    def test_nested(self):
        h = {"children": [{"text": "A"}, {"children": [{"label": "B"}]}]}
        texts = sim_verify.visible_text(h)
        assert "A" in texts and "B" in texts

    def test_ignores_non_string_fields(self):
        h = {"text": "keep", "enabled": True, "count": 42}
        assert sim_verify.visible_text(h) == ["keep"]


class TestVerifyVisible:
    def _setup(self, monkeypatch, hierarchy=None):
        monkeypatch.setattr(sim_verify, "capture_screenshot", lambda path=None: "/tmp/fake.png")
        monkeypatch.setattr(sim_verify, "dump_hierarchy", lambda: hierarchy)

    def test_expect_passes(self, monkeypatch):
        self._setup(monkeypatch, hierarchy={"text": "Welcome +74260000042"})
        result = sim_verify.verify_visible(expect="+74260000042")
        assert result["ok"], result

    def test_expect_fails(self, monkeypatch):
        self._setup(monkeypatch, hierarchy={"text": "Welcome"})
        result = sim_verify.verify_visible(expect="+74260000042")
        assert not result["ok"]
        assert any("+74260000042" in f for f in result["failures"])

    def test_expect_without_hierarchy(self, monkeypatch):
        """Can't verify when hierarchy is unavailable — must flag a failure."""
        self._setup(monkeypatch, hierarchy=None)
        result = sim_verify.verify_visible(expect="anything")
        assert not result["ok"]
        assert any("Maestro hierarchy not available" in f for f in result["failures"])

    def test_forbid_catches_undefined(self, monkeypatch):
        """The motivating bug — 'undefined' entered into fields."""
        self._setup(monkeypatch, hierarchy={"text": "Phone: undefined"})
        result = sim_verify.verify_visible()
        assert not result["ok"]
        assert any("undefined" in f for f in result["failures"])

    def test_forbid_explicit(self, monkeypatch):
        self._setup(monkeypatch, hierarchy={"text": "ERROR: crashed"})
        result = sim_verify.verify_visible(forbid="ERROR")
        assert not result["ok"]
        assert any("ERROR" in f for f in result["failures"])

    def test_no_hierarchy_still_captures(self, monkeypatch):
        """With no expectations and no hierarchy, capture alone succeeds."""
        self._setup(monkeypatch, hierarchy=None)
        result = sim_verify.verify_visible()
        assert result["ok"]
        assert result["screenshot"] == "/tmp/fake.png"
        assert result["hierarchy_available"] is False


class TestBootedDeviceUdid:
    def test_returns_booted_udid(self):
        fake = _fake_run(stdout='{"devices":{"iOS-17":[{"udid":"ABC","state":"Booted"}]}}')
        with patch("sim_verify.subprocess.run", return_value=fake):
            assert sim_verify.booted_device_udid() == "ABC"

    def test_raises_when_none_booted(self):
        fake = _fake_run(stdout='{"devices":{"iOS-17":[]}}')
        with patch("sim_verify.subprocess.run", return_value=fake):
            with pytest.raises(RuntimeError, match="No booted simulator"):
                sim_verify.booted_device_udid()

"""Tests for the OTP fetch helper."""

from __future__ import annotations

import json
import re
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from unittest.mock import patch

import pytest

from otp import get_test_otp


# ---------------------------------------------------------------------------
# Unit tests (no network)
# ---------------------------------------------------------------------------

class TestPhoneGuard:
    """Reject non-test phone numbers."""

    def test_rejects_us_number(self):
        with pytest.raises(ValueError, match=r"Only \+7426"):
            get_test_otp("+12025550100")

    def test_rejects_empty(self):
        with pytest.raises(ValueError, match=r"Only \+7426"):
            get_test_otp("")

    def test_rejects_similar_prefix(self):
        with pytest.raises(ValueError, match=r"Only \+7426"):
            get_test_otp("+7425000000")

    def test_accepts_test_prefix(self):
        """Valid prefix should pass the guard (will fail on missing creds)."""
        with pytest.raises(RuntimeError, match="No credentials"):
            get_test_otp("+74260000042")


class TestResponseParsing:
    """Verify OTP extraction from mocked HTTP responses."""

    def _mock_urlopen(self, body: str, status: int = 200):
        """Return a context-manager mock that yields body bytes."""
        from unittest.mock import MagicMock
        from io import BytesIO

        resp = MagicMock()
        resp.read.return_value = body.encode()
        resp.status = status
        resp.__enter__ = lambda s: s
        resp.__exit__ = lambda s, *a: None
        return resp

    @patch("otp.urllib.request.urlopen")
    def test_json_otp_field(self, mock_urlopen):
        mock_urlopen.return_value = self._mock_urlopen(
            json.dumps({"otp": "482901"})
        )
        result = get_test_otp(
            "+74260000042", session_cookie="fake-session"
        )
        assert result == "482901"

    @patch("otp.urllib.request.urlopen")
    def test_plain_text_6digit(self, mock_urlopen):
        mock_urlopen.return_value = self._mock_urlopen("Your OTP is 193847.")
        result = get_test_otp(
            "+74260000042", session_cookie="fake-session"
        )
        assert result == "193847"

    @patch("otp.urllib.request.urlopen")
    def test_html_body_with_otp(self, mock_urlopen):
        html = "<html><body><td>+74260000042</td><td>529301</td></body></html>"
        mock_urlopen.return_value = self._mock_urlopen(html)
        result = get_test_otp(
            "+74260000042", session_cookie="fake-session"
        )
        assert result == "529301"

    @patch("otp.urllib.request.urlopen")
    def test_no_6digit_raises(self, mock_urlopen):
        mock_urlopen.return_value = self._mock_urlopen("No codes here.")
        with pytest.raises(RuntimeError, match="Could not find"):
            get_test_otp("+74260000042", session_cookie="fake-session")


class TestOAuthFlow:
    """Verify the two-step OAuth2 client-credentials flow."""

    def _mock_urlopen_side_effect(self, calls):
        """Return different responses for successive urlopen calls."""
        from unittest.mock import MagicMock
        from io import BytesIO

        results = []
        for body in calls:
            resp = MagicMock()
            resp.read.return_value = body.encode()
            resp.__enter__ = lambda s: s
            resp.__exit__ = lambda s, *a: None
            results.append(resp)
        return results

    @patch("otp.urllib.request.urlopen")
    def test_oauth_two_step(self, mock_urlopen):
        mock_urlopen.side_effect = self._mock_urlopen_side_effect([
            json.dumps({"access_token": "tok123"}),
            json.dumps({"otp": "661234"}),
        ])
        result = get_test_otp(
            "+74260000042",
            client_id="cid",
            client_secret="csec",
        )
        assert result == "661234"
        assert mock_urlopen.call_count == 2


# ---------------------------------------------------------------------------
# Integration test (real network — skip in CI)
# ---------------------------------------------------------------------------

@pytest.mark.integration
def test_real_otp_fetch():
    """Fetch a real OTP from connect-id. Requires credentials in env."""
    otp = get_test_otp("+74260000042")
    assert re.fullmatch(r"\d{6}", otp), f"Expected 6-digit OTP, got: {otp}"

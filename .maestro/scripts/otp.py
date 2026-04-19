#!/usr/bin/env python3
"""
OTP fetch helper for commcare-ios e2e tests.

Fetches a one-time password from Dimagi's connect-id service for test
phone numbers (must start with +7426 — the connect-id test prefix that
suppresses real SMS delivery).

Two modes:
  1. OAuth2 client credentials (preferred, for CI):
       Set CONNECTID_E2E_CLIENT_ID and CONNECTID_E2E_CLIENT_SECRET.
       Calls POST /o/token/ then GET /users/generate_manual_otp.

  2. Session cookie (local dev fallback):
       Set CONNECTID_SESSION_COOKIE to a valid sessionid cookie value
       from a Dimagi SSO login. Calls GET /users/connect_user_otp/?phone=.

Usage:
  # CLI
  python .maestro/scripts/otp.py +74260000042

  # As a module
  from otp import get_test_otp
  code = get_test_otp("+74260000042")

  # With explicit OAuth2 credentials
  code = get_test_otp("+74260000042",
                       client_id="...", client_secret="...")

Environment variables (all optional if passed as arguments):
  CONNECTID_E2E_CLIENT_ID       OAuth2 client ID
  CONNECTID_E2E_CLIENT_SECRET   OAuth2 client secret
  CONNECTID_SESSION_COOKIE      Fallback: sessionid cookie value
  CONNECTID_E2E_BASE_URL        Override base URL
                                (default: https://connectid.dimagi.com)
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

TEST_PREFIX = "+7426"
DEFAULT_BASE_URL = "https://connectid.dimagi.com"


def get_test_otp(
    phone_number: str,
    *,
    client_id: str | None = None,
    client_secret: str | None = None,
    session_cookie: str | None = None,
    base_url: str | None = None,
) -> str:
    """Fetch the current OTP for a +7426 test phone number.

    Raises ValueError for non-test numbers and RuntimeError on API errors.
    """
    if not phone_number.startswith(TEST_PREFIX):
        raise ValueError(
            f"Only {TEST_PREFIX} test numbers supported "
            f"— refusing to fetch OTP for real phone: {phone_number}"
        )

    base = (base_url or os.environ.get("CONNECTID_E2E_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
    cid = client_id or os.environ.get("CONNECTID_E2E_CLIENT_ID")
    csec = client_secret or os.environ.get("CONNECTID_E2E_CLIENT_SECRET")
    cookie = session_cookie or os.environ.get("CONNECTID_SESSION_COOKIE")

    if cid and csec:
        return _fetch_via_oauth(phone_number, base, cid, csec)
    if cookie:
        return _fetch_via_session(phone_number, base, cookie)

    raise RuntimeError(
        "No credentials provided. Set CONNECTID_E2E_CLIENT_ID + "
        "CONNECTID_E2E_CLIENT_SECRET (OAuth2) or CONNECTID_SESSION_COOKIE "
        "(session fallback)."
    )


def _fetch_via_oauth(phone: str, base: str, client_id: str, client_secret: str) -> str:
    """OAuth2 client_credentials flow -> generate_manual_otp endpoint."""
    token_url = f"{base}/o/token/"
    token_data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": client_secret,
    }).encode()
    token_req = urllib.request.Request(token_url, data=token_data, method="POST")
    token_req.add_header("Content-Type", "application/x-www-form-urlencoded")

    try:
        with urllib.request.urlopen(token_req, timeout=15) as resp:
            token_body = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"Token request failed (HTTP {e.code}): {e.read().decode()}")

    access_token = token_body.get("access_token")
    if not access_token:
        raise RuntimeError(f"No access_token in response: {token_body}")

    encoded_phone = urllib.parse.quote(phone, safe="")
    otp_url = f"{base}/users/generate_manual_otp?phone_number={encoded_phone}"
    otp_req = urllib.request.Request(otp_url)
    otp_req.add_header("Authorization", f"Bearer {access_token}")

    return _parse_otp_response(otp_req, phone)


def _fetch_via_session(phone: str, base: str, session_cookie: str) -> str:
    """Session cookie fallback -> connect_user_otp admin page."""
    encoded_phone = urllib.parse.quote(phone, safe="")
    url = f"{base}/users/connect_user_otp/?phone={encoded_phone}"
    req = urllib.request.Request(url)
    req.add_header("Cookie", f"sessionid={session_cookie}")

    return _parse_otp_response(req, phone)


def _parse_otp_response(req: urllib.request.Request, phone: str) -> str:
    """Send request and extract a 6-digit OTP from the response."""
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode()
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"OTP request failed (HTTP {e.code}): {e.read().decode()}")

    # Try JSON first: {"otp": "123456"}
    try:
        data = json.loads(body)
        if isinstance(data, dict) and "otp" in data:
            otp = str(data["otp"])
            if re.fullmatch(r"\d{6}", otp):
                return otp
    except (json.JSONDecodeError, TypeError):
        pass

    # Fallback: find first 6-digit number in the response body
    match = re.search(r"\b(\d{6})\b", body)
    if match:
        return match.group(1)

    raise RuntimeError(
        f"Could not find a 6-digit OTP for {phone} in response "
        f"(first 500 chars): {body[:500]}"
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: python {sys.argv[0]} <+7426...phone_number>", file=sys.stderr)
        sys.exit(1)
    try:
        otp = get_test_otp(sys.argv[1])
        print(otp)
    except (ValueError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)

#!/usr/bin/env node
/**
 * Phase 9 E2E: Playwright-based OTP fetcher for the Dimagi connect_user_otp page.
 *
 * Uses a persistent Chromium user-data-dir so the developer only needs to
 * sign in to Dimagi SSO once. Subsequent runs reuse the saved session.
 *
 * Usage:
 *   node .maestro/scripts/playwright/fetch-otp.js <phone_number>
 *   node .maestro/scripts/playwright/fetch-otp.js +74260000042
 *
 * First run: opens a headed Chromium window. If not already signed in,
 * the user signs in to Dimagi SSO. The script then navigates to the
 * connect_user_otp page, finds the OTP for the given phone number,
 * prints it to stdout, and exits.
 *
 * Subsequent runs: opens Chromium with the saved session, skips straight
 * to the lookup, prints OTP, exits. Runs headless by default (override
 * with PHASE9_HEADED=1 to watch).
 *
 * Environment variables:
 *   PHASE9_HEADED=1         Run the browser in headed mode (for debugging
 *                           or first-time login)
 *   PHASE9_URL=<url>        Override the OTP lookup URL (defaults to
 *                           https://connect.dimagi.com/users/connect_user_otp/)
 *   PHASE9_USER_DATA_DIR    Override the Playwright user data directory
 *                           (defaults to .maestro/scripts/playwright/userdata)
 *
 * Exit codes:
 *   0   Success — OTP printed to stdout
 *   1   Usage error
 *   2   Signed-out / authentication required (headless mode)
 *   3   OTP not found for given phone
 *   4   Unexpected error
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const PHASE9_URL = process.env.PHASE9_URL || 'https://connect.dimagi.com/users/connect_user_otp/';
const HEADED = process.env.PHASE9_HEADED === '1';
const USER_DATA_DIR = process.env.PHASE9_USER_DATA_DIR ||
  path.join(__dirname, 'userdata');

function die(code, msg) {
  process.stderr.write(`ERROR: ${msg}\n`);
  process.exit(code);
}

async function main() {
  const phoneNumber = process.argv[2];
  if (!phoneNumber) {
    die(1, 'usage: fetch-otp.js <phone_number>\n  e.g. fetch-otp.js +74260000042');
  }

  // Ensure the user data dir exists
  fs.mkdirSync(USER_DATA_DIR, { recursive: true });

  const context = await chromium.launchPersistentContext(USER_DATA_DIR, {
    headless: !HEADED,
    viewport: { width: 1280, height: 900 },
  });

  try {
    const page = context.pages()[0] || await context.newPage();

    process.stderr.write(`[phase9] navigating to ${PHASE9_URL}\n`);
    await page.goto(PHASE9_URL, { waitUntil: 'networkidle' });

    // Detect if we got redirected to a login page. Dimagi SSO redirects
    // unauthenticated users to an sso.dimagi.com / accounts.google.com /
    // other login provider URL.
    const currentUrl = page.url();
    process.stderr.write(`[phase9] landed on ${currentUrl}\n`);

    const isSignedIn = currentUrl.startsWith(PHASE9_URL) ||
      currentUrl.includes('connect.dimagi.com/users/connect_user_otp');

    if (!isSignedIn) {
      if (HEADED) {
        process.stderr.write(
          `[phase9] not signed in to Dimagi SSO — please complete login in the browser window.\n` +
          `[phase9] once the connect_user_otp page is visible, press ENTER in this terminal.\n`
        );
        // Wait for the user to hit enter
        await new Promise((resolve) => {
          process.stdin.once('data', resolve);
          process.stdin.resume();
        });
        process.stdin.pause();
        // Re-navigate after login
        await page.goto(PHASE9_URL, { waitUntil: 'networkidle' });
      } else {
        die(
          2,
          `not authenticated to ${PHASE9_URL} — re-run with PHASE9_HEADED=1 to log in:\n` +
          `  PHASE9_HEADED=1 node ${path.basename(__filename)} ${phoneNumber}`
        );
      }
    }

    // Now we should be on the connect_user_otp page. Find the OTP.
    //
    // The page structure is unknown until first run. This function tries
    // several strategies (form input, table lookup, getByText) and on
    // failure dumps the page body for manual inspection.

    // Strategy 1: form + text input
    const input = page.locator('input[name*="phone"], input[placeholder*="phone" i], input[type="tel"]').first();
    if (await input.count() > 0) {
      process.stderr.write('[phase9] found phone input form — filling and submitting\n');
      await input.fill(phoneNumber);
      // find nearest submit button
      const submit = page.locator('button[type="submit"], input[type="submit"]').first();
      if (await submit.count() > 0) {
        await submit.click();
        await page.waitForLoadState('networkidle');
      }
    }

    // Strategy 2: look for a 6-digit number near the phone number
    // Scan the body text.
    const bodyText = await page.locator('body').innerText();
    process.stderr.write(`[phase9] body text (first 500 chars):\n${bodyText.substring(0, 500)}\n---\n`);

    // Heuristic: look for a 6-digit code. Prefer one near the phone number.
    // First try to find the phone number in the body, then look at the
    // next 200 characters for a 6-digit code.
    const phoneRegex = new RegExp(phoneNumber.replace('+', '\\+').replace(/\d/g, '\\d?$&'));
    const phoneMatch = bodyText.match(phoneRegex);
    let otp = null;
    if (phoneMatch) {
      const after = bodyText.substring(phoneMatch.index + phoneMatch[0].length, phoneMatch.index + phoneMatch[0].length + 500);
      const m = after.match(/\b(\d{6})\b/);
      if (m) otp = m[1];
    }
    // Fallback: first 6-digit code on the page
    if (!otp) {
      const m = bodyText.match(/\b(\d{6})\b/);
      if (m) otp = m[1];
    }

    if (!otp) {
      die(3, `could not find a 6-digit OTP on ${PHASE9_URL} for phone ${phoneNumber}.\n` +
        `First 1000 chars of body:\n${bodyText.substring(0, 1000)}`);
    }

    // Print OTP to stdout. Caller can capture with:
    //   OTP=$(node fetch-otp.js +74260000042)
    process.stdout.write(otp + '\n');
    process.exit(0);
  } catch (e) {
    die(4, `unexpected error: ${e && e.stack ? e.stack : e}`);
  } finally {
    await context.close();
  }
}

main();

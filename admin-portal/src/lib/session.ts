/**
 * Staff session for the admin portal: a single shared passphrase from
 * PORTAL_PASSCODE (server-side only) mints an HMAC-signed, expiring cookie.
 * No server-side session store; verification recomputes the signature, so
 * rotating the passphrase invalidates every existing session immediately.
 * All comparisons are timing-safe.
 */

import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";

export const SESSION_COOKIE = "ledger_session";
const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
export const SESSION_TTL_SECONDS = SESSION_TTL_MS / 1000;
const SESSION_KEY_CONTEXT = "ledger-portal-session-v1";

function sessionKey(): Buffer {
	const passphrase = process.env.PORTAL_PASSCODE;
	if (!passphrase) {
		throw new Error("PORTAL_PASSCODE is not set");
	}
	// Bind the signing key to a fixed context string plus the passphrase, so
	// the same passphrase used elsewhere produces a different key here.
	return createHmac("sha256", passphrase).update(SESSION_KEY_CONTEXT).digest();
}

/** A signed session token for now: `<expiryMs>.<hmac(expiryMs)>`. */
export function signSession(): string {
	const expiresAt = Date.now() + SESSION_TTL_MS;
	const digest = createHmac("sha256", sessionKey()).update(String(expiresAt)).digest("base64url");
	return `${expiresAt}.${digest}`;
}

/** True only when the token's signature verifies and it has not expired. */
export function verifySession(token: string | undefined): boolean {
	if (!token || !process.env.PORTAL_PASSCODE) {
		return false;
	}
	const dot = token.indexOf(".");
	if (dot <= 0) {
		return false;
	}
	const expiryPart = token.slice(0, dot);
	const digest = token.slice(dot + 1);
	const expiresAt = Number(expiryPart);
	if (!Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) {
		return false;
	}
	const expected = createHmac("sha256", sessionKey()).update(expiryPart).digest("base64url");
	const given = Buffer.from(digest);
	const check = Buffer.from(expected);
	return given.length === check.length && timingSafeEqual(given, check);
}

/** Constant-time passphrase check; fails closed when unset or empty. */
export function passphraseMatches(candidate: string): boolean {
	const expected = process.env.PORTAL_PASSCODE;
	if (!expected) {
		return false;
	}
	// Compare fixed-length digests so timing does not leak length or prefix.
	const given = createHash("sha256").update(candidate).digest();
	const check = createHash("sha256").update(expected).digest();
	return timingSafeEqual(given, check);
}

export function sessionCookie(maxAgeSeconds?: number): {
	httpOnly: boolean;
	sameSite: "lax";
	secure: boolean;
	path: string;
	maxAge?: number;
} {
	return {
		httpOnly: true,
		sameSite: "lax",
		secure: process.env.NODE_ENV === "production",
		path: "/",
		...(maxAgeSeconds !== undefined ? { maxAge: maxAgeSeconds } : {}),
	};
}

/** Reads and verifies the session cookie from the current request. */
export async function isAuthenticated(): Promise<boolean> {
	const store = await cookies();
	return verifySession(store.get(SESSION_COOKIE)?.value);
}

/**
 * Merchant session: the merchant's API key mints an HMAC-signed, expiring
 * cookie. The raw key is NOT stored in the cookie — only a signature over
 * its hash — so a stolen cookie does not leak the key. Verification recomputes
 * the signature; the key itself lives server-side in a companion store.
 *
 * Cookie design: `<expiryMs>.<keyHash-b64url>.<hmac(expiryMs.keyHash)>` where
 * keyHash = sha256(key). The in-process key vault maps keyHash -> key so API
 * calls can authenticate without the key ever crossing to the browser.
 */
import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";

export { SESSION_COOKIE } from "./session-edge";
import { SESSION_COOKIE } from "./session-edge";

const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
export const SESSION_TTL_SECONDS = SESSION_TTL_MS / 1000;
const SESSION_KEY_CONTEXT = "merchant-portal-session-v1";

/** Server-side vault: keyHash -> raw key. Survives for the process lifetime;
 * a restart simply asks merchants to sign in again. */
const keyVault = new Map<string, string>();

/** MUST match session-edge.ts's key material: the same logical HMAC key on
 * both runtimes. Edge can't do HKDF-style double hashing cheaply, so both
 * sides sign with the bytes of this literal string. */
function sessionKey(): Buffer {
	const secret = process.env.MERCHANT_SESSION_SECRET ?? "merchant-portal-dev-secret";
	return Buffer.from(`merchant-portal-session-v1:${secret}`, "utf8");
}

export function keyHashOf(apiKey: string): string {
	return createHash("sha256").update(apiKey).digest("base64url");
}

/** A signed session token binding the expiry to this key's hash. */
export function signSession(apiKey: string): string {
	const keyHash = keyHashOf(apiKey);
	keyVault.set(keyHash, apiKey);
	const expiresAt = Date.now() + SESSION_TTL_MS;
	const digest = createHmac("sha256", sessionKey())
		.update(`${expiresAt}.${keyHash}`)
		.digest("base64url");
	return `${expiresAt}.${keyHash}.${digest}`;
}

export interface MerchantSession {
	apiKey: string;
}

/** Verifies the token and returns the merchant's key, or null. */
export function verifySession(token: string | undefined): MerchantSession | null {
	if (!token) {
		return null;
	}
	const parts = token.split(".");
	if (parts.length !== 3) {
		return null;
	}
	const [expiryPart, keyHash, digest] = parts;
	const expiresAt = Number(expiryPart);
	if (!Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) {
		keyVault.delete(keyHash);
		return null;
	}
	const expected = createHmac("sha256", sessionKey())
		.update(`${expiryPart}.${keyHash}`)
		.digest("base64url");
	const given = Buffer.from(digest);
	const check = Buffer.from(expected);
	if (given.length !== check.length || !timingSafeEqual(given, check)) {
		return null;
	}
	const apiKey = keyVault.get(keyHash);
	return apiKey ? { apiKey } : null;
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

/** Reads and verifies the session cookie for the current request. */
export async function currentSession(): Promise<MerchantSession | null> {
	const store = await cookies();
	return verifySession(store.get(SESSION_COOKIE)?.value);
}

/**
 * Merchant session: the merchant's API key is sealed (AES-256-GCM) into a
 * self-contained, expiring cookie keyed to MERCHANT_SESSION_SECRET. The raw
 * key never appears in the cookie or the browser; any server instance holding
 * the secret can unseal it — no shared vault, works across processes.
 *
 * Cookie design: `<iv-b64url>.<ciphertext-b64url>.<tag-b64url>` where the
 * plaintext is `<expiryMs>:<apiKey>` and the AAD binds the cookie name.
 */
import { createCipheriv, createDecipheriv, createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";

export { SESSION_COOKIE } from "./session-edge";
import { SESSION_COOKIE } from "./session-edge";

const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
export const SESSION_TTL_SECONDS = SESSION_TTL_MS / 1000;

function sessionKey(): Buffer {
	const secret = process.env.MERCHANT_SESSION_SECRET ?? "merchant-portal-dev-secret";
	return createHash("sha256")
		.update(`merchant-portal-session-v1:${secret}`, "utf8")
		.digest();
}

export interface MerchantSession {
	apiKey: string;
}

/** Seals the API key into a self-contained, expiring session token. */
export function signSession(apiKey: string): string {
	const iv = randomBytes(12);
	const cipher = createCipheriv("aes-256-gcm", sessionKey(), iv, { authTagLength: 16 });
	cipher.setAAD(Buffer.from(SESSION_COOKIE));
	const expiresAt = Date.now() + SESSION_TTL_MS;
	const ciphertext = Buffer.concat([
		cipher.update(`${expiresAt}:${apiKey}`, "utf8"),
		cipher.final(),
	]);
	const tag = cipher.getAuthTag();
	return [
		iv.toString("base64url"),
		ciphertext.toString("base64url"),
		tag.toString("base64url"),
	].join(".");
}

function unsealSession(token: string): MerchantSession | null {
	const parts = token.split(".");
	if (parts.length !== 3) {
		return null;
	}
	try {
		const iv = Buffer.from(parts[0], "base64url");
		const ciphertext = Buffer.from(parts[1], "base64url");
		const tag = Buffer.from(parts[2], "base64url");
		const decipher = createDecipheriv("aes-256-gcm", sessionKey(), iv, { authTagLength: 16 });
		decipher.setAAD(Buffer.from(SESSION_COOKIE));
		decipher.setAuthTag(tag);
		const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8");
		const sep = plaintext.indexOf(":");
		if (sep < 0) {
			return null;
		}
		const expiresAt = Number(plaintext.slice(0, sep));
		if (!Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) {
			return null;
		}
		const apiKey = plaintext.slice(sep + 1);
		return apiKey ? { apiKey } : null;
	} catch {
		return null;
	}
}

/** Verifies the token and returns the merchant's key, or null. */
export function verifySession(token: string | undefined): MerchantSession | null {
	if (!token) {
		return null;
	}
	const session = unsealSession(token);
	if (!session) {
		return null;
	}
	// Constant-time no-op on the key so timing is uniform with the previous
	// comparison-based implementation; correctness comes from GCM's tag.
	const check = Buffer.from(session.apiKey);
	const expected = Buffer.from(session.apiKey);
	timingSafeEqual(check, expected);
	return session;
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

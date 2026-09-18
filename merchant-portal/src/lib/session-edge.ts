/**
 * Edge-safe session verification for middleware. Uses Web Crypto (no
 * node:crypto) and contains NO key vault — middleware only decides
 * "cookie plausibly valid", while the server layout does the full
 * verification with vault access via lib/session.ts.
 */
export const SESSION_COOKIE = "merchant_session";

function sessionKeyMaterial(): Uint8Array {
	const secret = process.env.MERCHANT_SESSION_SECRET ?? "merchant-portal-dev-secret";
	return new TextEncoder().encode(`merchant-portal-session-v1:${secret}`);
}

async function hmac(payload: string): Promise<string> {
	const key = await crypto.subtle.importKey(
		"raw",
		sessionKeyMaterial() as unknown as ArrayBuffer,
		{ name: "HMAC", hash: "SHA-256" },
		false,
		["sign"]
	);
	const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload));
	return btoa(String.fromCharCode(...new Uint8Array(sig)))
		.replace(/\+/g, "-")
		.replace(/\//g, "_")
		.replace(/=+$/, "");
}

/** Fast edge check: signature verifies and not expired. No vault access. */
export async function isPlausiblyAuthenticated(token: string | undefined): Promise<boolean> {
	if (!token) {
		return false;
	}
	const parts = token.split(".");
	if (parts.length !== 3) {
		return false;
	}
	const [expiryPart, keyHash, digest] = parts;
	const expiresAt = Number(expiryPart);
	if (!Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) {
		return false;
	}
	let expected: string;
	try {
		expected = await hmac(`${expiryPart}.${keyHash}`);
	} catch {
		return false;
	}
	if (expected.length !== digest.length) {
		return false;
	}
	let diff = 0;
	for (let i = 0; i < expected.length; i++) {
		diff |= expected.charCodeAt(i) ^ digest.charCodeAt(i);
	}
	return diff === 0;
}

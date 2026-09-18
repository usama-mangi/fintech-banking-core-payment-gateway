import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/session-edge";

/**
 * The sign-in gate: any page request without a plausibly valid session is
 * redirected to /login, preserving the path so sign-in can return to it.
 * Full authentication (AES-GCM unseal + expiry) happens in the layout.
 */
export function middleware(request: NextRequest) {
	// Shape-only gate: a well-formed sealed token is `iv.ciphertext.tag`
	// (three base64url parts). The real authentication (AES-GCM unseal +
	// expiry check) happens in the layout — middleware only avoids rendering
	// pages for requests with no session-shaped cookie at all.
	const token = request.cookies.get(SESSION_COOKIE)?.value;
	const plausible = (token?.split(".") ?? []).length === 3;
	if (!plausible) {
		const loginUrl = new URL("/login", request.url);
		loginUrl.searchParams.set("next", request.nextUrl.pathname);
		return NextResponse.redirect(loginUrl);
	}

	const headers = new Headers(request.headers);
	headers.set("x-portal-path", request.nextUrl.pathname);
	return NextResponse.next({ request: { headers } });
}

export const config = {
	matcher: ["/((?!_next/static|_next/image|favicon.ico|login|api/sign-in).*)"],
};

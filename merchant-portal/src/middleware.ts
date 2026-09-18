import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE, isPlausiblyAuthenticated } from "@/lib/session-edge";

/**
 * The sign-in gate: any page request without a plausibly valid session is
 * redirected to /login, preserving the path so sign-in can return to it.
 * Full verification (with the key vault) happens again in the layout.
 */
export async function middleware(request: NextRequest) {
	const plausible = await isPlausiblyAuthenticated(request.cookies.get(SESSION_COOKIE)?.value);
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
	matcher: ["/((?!_next/static|_next/image|favicon.ico|login|cookie-smoke).*)"],
};

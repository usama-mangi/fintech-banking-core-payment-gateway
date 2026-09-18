import { NextResponse, type NextRequest } from "next/server";
import { listPayments } from "@/lib/api";
import { SESSION_COOKIE, SESSION_TTL_SECONDS, sessionCookie, signSession } from "@/lib/session";

/**
 * Sign-in as a route handler rather than a server action: the action path's
 * Set-Cookie on the 303 proved unreliable in this setup, while a handler's
 * cookie + redirect is the plain HTTP mechanism. Also works without JS.
 */
export async function POST(request: NextRequest) {
	const form = await request.formData();
	const apiKey = String(form.get("apiKey") ?? "").trim();
	const nextRaw = String(form.get("next") ?? "");
	const next = nextRaw.startsWith("/") && !nextRaw.startsWith("//") ? nextRaw : "/";

	const loginUrl = new URL("/login", request.url);
	const targetUrl = (path: string) => new URL(path, request.url);
	if (next !== "/") {
		loginUrl.searchParams.set("next", next);
	}

	if (!apiKey) {
		loginUrl.searchParams.set("error", "empty");
		return NextResponse.redirect(loginUrl);
	}

	try {
		// Key validity probe: the gateway accepts the key (ownership rules
		// scope everything it returns to this merchant).
		await listPayments(apiKey, undefined, 0);
	} catch (error) {
		const status = error instanceof Error && "status" in error
			? (error as { status: number }).status
			: 0;
		if (status === 401) {
			loginUrl.searchParams.set("error", "invalid");
		} else if (status === 403) {
			loginUrl.searchParams.set("error", "locked");
		} else {
			loginUrl.searchParams.set("error", "unreachable");
		}
		return NextResponse.redirect(loginUrl);
	}

	const response = NextResponse.redirect(new URL(next, request.url));
	response.cookies.set(SESSION_COOKIE, signSession(apiKey), sessionCookie(SESSION_TTL_SECONDS));
	return response;
}

import { NextResponse, type NextRequest } from "next/server";

/**
 * Forwards the request path as a header so server layouts can build
 * "return to this page" redirect targets (the sign-in gate).
 */
export function middleware(request: NextRequest) {
	const headers = new Headers(request.headers);
	headers.set("x-portal-path", request.nextUrl.pathname);
	return NextResponse.next({ request: { headers } });
}

export const config = {
	matcher: ["/((?!_next/static|_next/image|favicon.ico|login).*)"],
};

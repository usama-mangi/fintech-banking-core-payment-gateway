import { NextResponse, type NextRequest } from "next/server";
import { cookies } from "next/headers";
import { SESSION_COOKIE, sessionCookie } from "@/lib/session";

export async function POST(request: NextRequest) {
  const store = await cookies();
  store.set(SESSION_COOKIE, "", sessionCookie(0));
  return NextResponse.redirect(new URL("/login", request.url), { status: 303 });
}

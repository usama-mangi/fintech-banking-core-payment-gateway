"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { registerMerchant, transitionMerchant, ApiError, type MerchantLifecycleAction } from "@/lib/api";
import {
  SESSION_COOKIE,
  SESSION_TTL_SECONDS,
  passphraseMatches,
  sessionCookie,
  signSession,
} from "@/lib/session";

export interface FormState {
  error?: string;
  ok?: boolean;
}

/** Only same-origin absolute paths may serve as a post-login target. */
function safeRedirectTarget(raw: string | null | undefined): string {
  return raw && raw.startsWith("/") && !raw.startsWith("//") ? raw : "/merchants";
}

export async function loginAction(
  _prev: FormState | null,
  formData: FormData
): Promise<FormState> {
  const passphrase = String(formData.get("passphrase") ?? "");
  const target = safeRedirectTarget(String(formData.get("next") ?? ""));

  if (!passphrase || !passphraseMatches(passphrase)) {
    return { error: "Wrong passphrase. Ask whoever runs the gateway for the current one." };
  }

  const store = await cookies();
  store.set(SESSION_COOKIE, signSession(), sessionCookie(SESSION_TTL_SECONDS));
  redirect(target);
}

export async function logoutAction(): Promise<void> {
  const store = await cookies();
  store.set(SESSION_COOKIE, "", sessionCookie(0));
  redirect("/login");
}

const LIFECYCLE_ACTIONS: MerchantLifecycleAction[] = ["suspend", "reactivate", "close"];

export async function merchantLifecycleAction(
  _prev: FormState | null,
  formData: FormData
): Promise<FormState> {
  const id = String(formData.get("merchantId") ?? "");
  const action = String(formData.get("action") ?? "") as MerchantLifecycleAction;

  if (!id || !LIFECYCLE_ACTIONS.includes(action)) {
    return { error: "Unknown lifecycle action." };
  }

  try {
    await transitionMerchant(Number(id), action);
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message };
    }
    return { error: "Could not reach the gateway API. Is the backend running?" };
  }
  revalidatePath("/merchants");
  return { ok: true };
}

export async function registerMerchantAction(
  _prev: FormState | null,
  formData: FormData
): Promise<FormState> {
  const businessName = String(formData.get("businessName") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();

  if (!businessName || !email) {
    return { error: "Business name and email are both required." };
  }

  try {
    await registerMerchant(businessName, email);
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: error.message };
    }
    return { error: "Could not reach the gateway API. Is the backend running?" };
  }
  revalidatePath("/merchants");
  return { ok: true };
}

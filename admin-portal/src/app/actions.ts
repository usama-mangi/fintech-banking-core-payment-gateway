"use server";

import { revalidatePath } from "next/cache";
import { registerMerchant, ApiError } from "@/lib/api";

export interface FormState {
  error?: string;
  ok?: boolean;
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

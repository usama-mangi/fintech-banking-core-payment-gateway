import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { isAuthenticated } from "@/lib/session";
import { LoginForm } from "@/components/LoginForm";

export const metadata: Metadata = {
  title: "Sign in",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  if (await isAuthenticated()) {
    redirect("/merchants");
  }
  const { next } = await searchParams;

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-8">
      <div className="mb-8">
        <p className="font-heading text-lg font-semibold tracking-tight">Ledger</p>
        <p className="mt-1 text-sm text-ink-muted">
          Payment operations for the gateway team.
        </p>
      </div>
      <h1 className="font-heading text-xl font-semibold">Sign in</h1>
      <p className="mt-1 mb-6 text-sm text-ink-muted">
        One shared passphrase for the operations surface. Ask whoever runs the
        gateway for it.
      </p>
      <LoginForm next={next} />
    </main>
  );
}

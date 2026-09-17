/**
 * URL-parameter helpers for the list screens. The portal degrades bad input
 * to a sane default instead of showing staff the API's 400 page.
 */

/** Parses a page number; anything but a non-negative integer falls back to 0. */
export function parsePageParam(raw: string | undefined): number {
  if (!raw) {
    return 0;
  }
  const parsed = Number(raw);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 0;
}

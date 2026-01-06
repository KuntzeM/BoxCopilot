/**
 * PDF Font Registration
 *
 * This function is an intentional no-op when using only the built-in
 * standard fonts provided by `@react-pdf/renderer` (e.g. Helvetica).
 *
 * Rationale:
 * - Avoids external font dependencies and network requests.
 * - Keeps a stable extension point so that custom fonts can be
 *   registered in one place in the future without changing callers.
 *
 * Standard fonts available in @react-pdf/renderer:
 * - Helvetica (default)
 * - Times-Roman
 * - Courier
 */
export const registerPDFFonts = (): void => {
  // Intentionally left blank:
  // using built-in standard fonts, no explicit registration required.
};

/**
 * Image URLs always go through the configured API base. A backend-supplied absolute URL is reduced to its path, so a
 * hostile or misconfigured value can never make the browser load an image from a third-party origin (CSP img-src).
 */
export function resolveImagePath(url: string | undefined, withApiBase: (path: string) => string): string | undefined {
  if (!url) return undefined;
  let path = url;
  if (/^https?:\/\//i.test(url)) {
    try {
      const u = new URL(url);
      path = u.pathname + u.search;
    } catch {
      return undefined;
    }
  }
  return withApiBase(path.startsWith('/') ? path : `/${path}`);
}

import axios from 'axios';

// Configure axios defaults
// Same-origin: the SPA is served by the backend (Vite proxies in dev), so URLs stay relative
axios.defaults.baseURL = '';
axios.defaults.withCredentials = true;

// The CSRF token comes from /api/v1/csrf and /api/v1/me and is set as X-XSRF-TOKEN default header (masked value).
// Same-origin, axios would otherwise copy the raw XSRF-TOKEN cookie over it; the backend rejects the raw value.
axios.defaults.xsrfCookieName = 'XSRF-TOKEN';
axios.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
axios.defaults.withXSRFToken = false;

// Calls whose 401 is expected and handled by the caller (auth probe, login, public/magic flows)
const UNAUTHORIZED_OK = /\/api\/(v1\/(me|csrf|public\/|auth\/magic-login)|auth\/)/;
const RELOAD_GUARD_KEY = 'sessionExpiredReloadAt';
const RELOAD_GUARD_MS = 10_000;

// Replaceable in tests (window.location.reload cannot be spied on in jsdom)
export const sessionExpired = {
  reload: () => window.location.reload(),
};

// Session expired mid-use: reload so App re-checks /api/v1/me and shows the login page.
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error?.config?.url ?? '';
    if (error?.response?.status === 401 && !UNAUTHORIZED_OK.test(url)) {
      const path = window.location.pathname;
      if (!path.startsWith('/public/') && !path.startsWith('/magic')) {
        try {
          // Loop guard: never reload more than once per RELOAD_GUARD_MS
          const last = Number(sessionStorage.getItem(RELOAD_GUARD_KEY) ?? 0);
          if (Date.now() - last > RELOAD_GUARD_MS) {
            sessionStorage.setItem(RELOAD_GUARD_KEY, String(Date.now()));
            sessionExpired.reload();
          }
        } catch {
          // storage unavailable: skip the reload rather than risk a loop
        }
      }
    }
    return Promise.reject(error);
  }
);

export default axios;

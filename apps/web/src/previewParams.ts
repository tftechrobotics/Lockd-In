// Address parameters, plus defaults a packaged preview can set before the app loads
// (`window.__LOCKDIN_DEFAULTS = 'shell=android&scene=setup'`) for hosts that hide the address,
// such as a file opened from a chat or a file viewer. Defaults apply only in the preview build.
export function previewParams(): URLSearchParams {
  const q = new URLSearchParams(typeof location === 'undefined' ? '' : location.search);
  if (import.meta.env.MODE === 'preview') {
    const d = (globalThis as { __LOCKDIN_DEFAULTS?: string }).__LOCKDIN_DEFAULTS;
    if (d) new URLSearchParams(d).forEach((v, k) => { if (!q.has(k)) q.set(k, v); });
  }
  return q;
}

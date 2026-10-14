export type Theme = "light" | "dark";
export const THEME_KEY = "negocio-facil-theme";

function storedTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(THEME_KEY);
    return value === "light" || value === "dark" ? value : null;
  } catch {
    return null;
  }
}

export function preferredTheme(): Theme {
  return storedTheme() ??
    (window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute(
    "content", theme === "dark" ? "#0c171b" : "#f5f7fa",
  );
  window.dispatchEvent(new Event("theme-changed"));
}

export function initializeTheme() {
  applyTheme(preferredTheme());
}

export function setTheme(theme: Theme) {
  try {
    // Store only this visual preference; authentication stays in HttpOnly cookies.
    window.localStorage.setItem(THEME_KEY, theme);
  } catch {
    // The switch still works when browser storage is unavailable.
  }
  applyTheme(theme);
}

export function currentTheme(): Theme {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

export function subscribeTheme(listener: () => void) {
  const media = window.matchMedia?.("(prefers-color-scheme: dark)");
  const storageChanged = (event: StorageEvent) => {
    if (event.key === THEME_KEY || event.key === null) initializeTheme();
  };
  const systemChanged = () => {
    if (!storedTheme()) initializeTheme();
  };
  window.addEventListener("theme-changed", listener);
  window.addEventListener("storage", storageChanged);
  media?.addEventListener("change", systemChanged);
  return () => {
    window.removeEventListener("theme-changed", listener);
    window.removeEventListener("storage", storageChanged);
    media?.removeEventListener("change", systemChanged);
  };
}

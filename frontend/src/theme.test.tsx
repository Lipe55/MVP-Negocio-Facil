import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import ThemeToggle from "./components/ThemeToggle";
import { currentTheme, initializeTheme, setTheme, subscribeTheme, THEME_KEY } from "./theme";

let media: { matches: boolean; addEventListener: EventTarget["addEventListener"]; removeEventListener: EventTarget["removeEventListener"] };
let mediaEvents: EventTarget;
beforeEach(() => {
  localStorage.removeItem(THEME_KEY);
  delete document.documentElement.dataset.theme;
  mediaEvents = new EventTarget();
  media = {
    matches: false,
    addEventListener: mediaEvents.addEventListener.bind(mediaEvents),
    removeEventListener: mediaEvents.removeEventListener.bind(mediaEvents),
  };
  vi.stubGlobal("matchMedia", vi.fn(() => media));
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  localStorage.removeItem(THEME_KEY);
  delete document.documentElement.dataset.theme;
});

it("uses the system theme until the user selects a preference", () => {
  media.matches = true;
  initializeTheme();
  expect(currentTheme()).toBe("dark");
  localStorage.setItem(THEME_KEY, "light");
  initializeTheme();
  expect(currentTheme()).toBe("light");
});

it("switches accessibly and persists only the visual preference", async () => {
  initializeTheme();
  render(<ThemeToggle />);
  const user = userEvent.setup();
  const button = screen.getByRole("button", { name: "Modo escuro" });
  expect(button).toHaveAttribute("aria-pressed", "false");
  await user.click(button);
  expect(button).toHaveAttribute("aria-pressed", "true");
  expect(currentTheme()).toBe("dark");
  expect(localStorage.getItem(THEME_KEY)).toBe("dark");
  initializeTheme();
  expect(currentTheme()).toBe("dark");
  await user.click(button);
  expect(currentTheme()).toBe("light");
  expect(localStorage.getItem(THEME_KEY)).toBe("light");
});

it("synchronizes a preference selected in another tab", () => {
  initializeTheme();
  render(<ThemeToggle />);
  act(() => {
    localStorage.setItem(THEME_KEY, "dark");
    window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "dark" }));
  });
  expect(screen.getByRole("button", { name: "Modo escuro" })).toHaveAttribute("aria-pressed", "true");
});

it("tracks system changes but keeps the explicit user choice", () => {
  initializeTheme();
  const listener = vi.fn();
  const unsubscribe = subscribeTheme(listener);
  try {
    media.matches = true;
    mediaEvents.dispatchEvent(new Event("change"));
    expect(currentTheme()).toBe("dark");
    setTheme("light");
    mediaEvents.dispatchEvent(new Event("change"));
    expect(currentTheme()).toBe("light");
    expect(listener).toHaveBeenCalled();
  } finally {
    unsubscribe();
  }
});

it("still works when browser storage is blocked", async () => {
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
  initializeTheme();
  render(<ThemeToggle />);
  await userEvent.setup().click(screen.getByRole("button", { name: "Modo escuro" }));
  expect(currentTheme()).toBe("dark");
});

it("ignores invalid stored preferences", () => {
  localStorage.setItem(THEME_KEY, "invalid");
  initializeTheme();
  expect(currentTheme()).toBe("light");
});

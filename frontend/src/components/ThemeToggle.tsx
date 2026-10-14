import { useSyncExternalStore } from "react";
import { Moon, Sun } from "lucide-react";
import { currentTheme, setTheme, subscribeTheme } from "../theme";

export default function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeTheme, currentTheme);
  const dark = theme === "dark";
  return (
    <button
      type="button"
      className="theme-switch"
      aria-label="Modo escuro"
      aria-pressed={dark}
      title={dark ? "Ativar modo claro" : "Ativar modo escuro"}
      onClick={() => setTheme(dark ? "light" : "dark")}
    >
      {dark ? <Moon size={17} aria-hidden="true" /> : <Sun size={17} aria-hidden="true" />}
      <span className="theme-label">{dark ? "Escuro" : "Claro"}</span>
    </button>
  );
}

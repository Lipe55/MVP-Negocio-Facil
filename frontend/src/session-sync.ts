// Only a notification crosses tabs; no identity, cookies or tokens are transmitted.
const channel = typeof BroadcastChannel !== "undefined"
  ? new BroadcastChannel("negocio-facil-session") : undefined;
channel?.addEventListener("message", (event) => {
  if (event.data === "changed") window.dispatchEvent(new Event("session-changed"));
});
export function notifySessionChanged() {
  channel?.postMessage("changed");
}

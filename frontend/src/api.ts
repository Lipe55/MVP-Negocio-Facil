import axios from "axios";
export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  timeout: 15000,
});
// Coalesce only requests in flight. Never retain a token across browser cookie changes.
let csrf: Promise<string> | undefined;
export function resetCsrf() {
  csrf = undefined;
}
function fetchCsrf() {
  if (!csrf) {
    const pending = api.get<{ token: string }>("/auth/csrf")
      .then((r) => r.data.token)
      .finally(() => {
        if (csrf === pending) csrf = undefined;
      });
    csrf = pending;
  }
  return csrf;
}
api.interceptors.request.use(async (config) => {
  if (!["get", "head", "options"].includes(config.method ?? "get")) {
    config.headers.set("X-XSRF-TOKEN", await fetchCsrf());
  }
  return config;
});
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error))
    return (
      error.response?.data?.message ??
      (error.code === "ECONNABORTED"
        ? "A conexão demorou demais. Tente novamente."
        : "Não foi possível conectar ao servidor.")
    );
  return error instanceof Error
    ? error.message
    : "Não foi possível concluir a operação.";
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 403 &&
        error.response.data?.code === "CSRF_INVALID" && error.config) {
      const config = error.config as typeof error.config & { csrfRetried?: boolean };
      if (!config.csrfRetried) {
        config.csrfRetried = true;
        resetCsrf();
        // The server rejects CSRF before reaching the controller, so this retry
        // cannot duplicate a business operation. Never retry timeouts or other 403s.
        return api.request(config);
      }
    }
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      !["/auth/login", "/auth/me"].includes(error.config?.url ?? "")
    ) {
      window.dispatchEvent(new Event("session-expired"));
    }
    return Promise.reject(error);
  },
);

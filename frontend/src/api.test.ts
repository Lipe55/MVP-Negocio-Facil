import { afterEach, expect, it, vi } from "vitest";
import { AxiosError, AxiosHeaders, type AxiosAdapter } from "axios";
import { api, resetCsrf } from "./api";

const originalAdapter = api.defaults.adapter;
afterEach(() => {
  api.defaults.adapter = originalAdapter;
  resetCsrf();
});
function response(config: Parameters<AxiosAdapter>[0], data: unknown, status = 200) {
  return { config, data, status, statusText: "", headers: new AxiosHeaders() };
}

it("fetches a fresh CSRF token for the next write after shared cookies change", async () => {
  let generation = 0;
  const headers: unknown[] = [];
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf") return response(config, { token: `synthetic-${++generation}` });
    headers.push(config.headers.get("X-XSRF-TOKEN"));
    return response(config, {});
  };
  await api.post("/customers", {});
  await api.post("/customers", {});
  expect(headers).toEqual(["synthetic-1", "synthetic-2"]);
});

it("retries once only when the server explicitly rejected CSRF before mutation", async () => {
  let writes = 0;
  let csrfRequests = 0;
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf") {
      csrfRequests++;
      return response(config, { token: "synthetic" });
    }
    if (++writes === 1) throw new AxiosError("CSRF", "ERR_BAD_REQUEST", config, undefined,
      response(config, { code: "CSRF_INVALID" }, 403));
    return response(config, { saved: true });
  };
  expect((await api.post("/customers", {})).data).toEqual({ saved: true });
  expect(writes).toBe(2);
  expect(csrfRequests).toBe(2);
});

it("does not loop on repeated CSRF rejection", async () => {
  let writes = 0;
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf") return response(config, { token: "synthetic" });
    writes++;
    throw new AxiosError("CSRF", "ERR_BAD_REQUEST", config, undefined,
      response(config, { code: "CSRF_INVALID" }, 403));
  };
  await expect(api.post("/customers", {})).rejects.toThrow("CSRF");
  expect(writes).toBe(2);
});

it.each([403, 409, 500])("does not repeat business writes after HTTP %s", async (status) => {
  const write = vi.fn();
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf") return response(config, { token: "synthetic" });
    write();
    throw new AxiosError("rejected", "ERR_BAD_REQUEST", config, undefined,
      response(config, {}, status));
  };
  await expect(api.post("/customers", {})).rejects.toThrow("rejected");
  expect(write).toHaveBeenCalledTimes(1);
});

it("does not repeat a write after a timeout with unknown server outcome", async () => {
  let writes = 0;
  api.defaults.adapter = async (config) => {
    if (config.url === "/auth/csrf") return response(config, { token: "synthetic" });
    writes++;
    throw new AxiosError("timeout", "ECONNABORTED", config);
  };
  await expect(api.post("/customers", {})).rejects.toThrow("timeout");
  expect(writes).toBe(1);
});

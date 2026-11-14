import { describe, expect, it } from "vitest";
import { getErrorMessage, http } from "../api/client";
import type { AxiosAdapter } from "axios";

describe("API error handling", () => {
  it("prefers the backend problem detail over a generic message", () => {
    const error = {
      isAxiosError: true,
      response: { data: { detail: "No compatible analysis window.", status: 409 } },
      toJSON: () => ({}),
    };
    expect(getErrorMessage(error)).toBe("No compatible analysis window.");
  });

  it("explains unavailable API connections without hiding the failure", () => {
    const error = {
      isAxiosError: true,
      response: undefined,
      toJSON: () => ({}),
    };
    expect(getErrorMessage(error)).toMatch(/Cannot reach the API/);
  });

  it("attaches the signed-in bearer token to API requests", async () => {
    localStorage.setItem("civic-air-token", "session-token");
    const originalAdapter = http.defaults.adapter;
    let authorization: unknown;
    const adapter: AxiosAdapter = async (config) => {
      authorization = config.headers.get("Authorization");
      return { data: [], status: 200, statusText: "OK", headers: {}, config };
    };
    http.defaults.adapter = adapter;
    try {
      const response = await http.get("/datasets");
      expect(response.data).toEqual([]);
      expect(authorization).toBe("Bearer session-token");
    } finally {
      http.defaults.adapter = originalAdapter;
    }
  });
});

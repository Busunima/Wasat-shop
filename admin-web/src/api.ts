import { firebaseAuth, apiBaseUrl } from "./firebase";

/** Карточка магазина в панели суперадмина (зеркало AdminStore сервера). */
export interface AdminStore {
  storeId: string;
  slug: string;
  name: string;
  ownerUid: string;
  ownerEmail: string;
  currency: string;
  plan: string;
  isPublic: boolean;
  isBlocked: boolean;
  createdAt: number | null;
}

export interface AdminStorePage {
  items: AdminStore[];
  nextCursor: string | null;
}

export const PLANS = ["free", "basic", "pro", "enterprise"] as const;
export type Plan = (typeof PLANS)[number];

export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const auth = firebaseAuth();
  const token = await auth?.currentUser?.getIdToken();
  if (!token) throw new ApiError("UNAUTHENTICATED", "Не выполнен вход", 401);

  const res = await fetch(new URL(path, apiBaseUrl), {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as
      | { error?: { code?: string; message?: string } }
      | null;
    throw new ApiError(
      body?.error?.code ?? "INTERNAL",
      body?.error?.message ?? `Ошибка сервера (${res.status})`,
      res.status,
    );
  }
  return (await res.json()) as T;
}

export function listStores(params: {
  q?: string;
  plan?: string;
  blocked?: string;
  cursor?: string;
}): Promise<AdminStorePage> {
  const search = new URLSearchParams();
  if (params.q) search.set("q", params.q);
  if (params.plan) search.set("plan", params.plan);
  if (params.blocked) search.set("blocked", params.blocked);
  if (params.cursor) search.set("cursor", params.cursor);
  const query = search.toString();
  return request<AdminStorePage>(`api/admin/stores${query ? `?${query}` : ""}`);
}

export function setStoreBlocked(
  storeId: string,
  blocked: boolean,
  reason?: string,
): Promise<AdminStore> {
  return request<AdminStore>(`api/admin/stores/${storeId}/block`, {
    method: "PATCH",
    body: JSON.stringify({ blocked, reason }),
  });
}

export function setStorePlan(storeId: string, plan: Plan): Promise<AdminStore> {
  return request<AdminStore>(`api/admin/stores/${storeId}/plan`, {
    method: "PATCH",
    body: JSON.stringify({ plan }),
  });
}

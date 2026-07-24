export type RoleCode = 'ANALISTA' | 'SUPERVISOR' | 'AUDITOR';

/** Base de la API: relativa en local (proxy Vite), absoluta en cloud (VITE_API_URL). */
const API_BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') || '/api';

let currentRole: RoleCode = 'ANALISTA';
export function setApiRole(role: RoleCode) { currentRole = role; }
export function getApiRole(): RoleCode { return currentRole; }

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch {
      /* sin cuerpo JSON */
    }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

function headers(json = true): HeadersInit {
  const h: Record<string, string> = { 'X-Role': currentRole };
  if (json) h['Content-Type'] = 'application/json';
  return h;
}

function url(path: string): string {
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`;
}

export const api = {
  get<T = any>(path: string): Promise<T> {
    return fetch(url(path), { headers: headers(false) }).then((r) => handle<T>(r));
  },
  post<T = any>(path: string, body?: unknown): Promise<T> {
    return fetch(url(path), {
      method: 'POST', headers: headers(), body: body === undefined ? undefined : JSON.stringify(body),
    }).then((r) => handle<T>(r));
  },
  put<T = any>(path: string, body?: unknown): Promise<T> {
    return fetch(url(path), {
      method: 'PUT', headers: headers(), body: body === undefined ? undefined : JSON.stringify(body),
    }).then((r) => handle<T>(r));
  },
  del<T = any>(path: string): Promise<T> {
    return fetch(url(path), { method: 'DELETE', headers: headers(false) }).then((r) => handle<T>(r));
  },
  upload<T = any>(path: string, form: FormData): Promise<T> {
    return fetch(url(path), { method: 'POST', headers: { 'X-Role': currentRole }, body: form }).then((r) => handle<T>(r));
  },
};

export function qs(params: Record<string, string | number | undefined | null>): string {
  const parts: string[] = [];
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

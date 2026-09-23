export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
let token: string | null = null;
export function setToken(value: string | null) { token = value; }
export function getToken() { return token; }
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(`${API_URL}${path}`, { ...init, headers, cache: 'no-store' });
  if (!response.ok) {
    if (response.status === 401) { setToken(null); window.dispatchEvent(new Event('gather-session-expired')); }
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(response.status, problem.detail ?? 'The request could not be completed. Please try again.');
  }
  return response.status === 204 ? undefined as T : response.json();
}

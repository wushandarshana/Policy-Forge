import type { Credentials, FieldError, ProblemDetail } from './types';

/** Thrown for any non-2xx response. Carries the parsed problem+json body when there is one (C3). */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /**
   * The named business code (C7). The invalid-policy-state problem carries it as its `type`
   * (`/problems/invalid-policy-state`) when the body has no `code`, so that case maps to INVALID_POLICY_STATE.
   */
  get code(): string | undefined {
    if (typeof this.problem.code === 'string') return this.problem.code;
    if (typeof this.problem.type === 'string' && this.problem.type.endsWith('/problems/invalid-policy-state')) {
      return 'INVALID_POLICY_STATE';
    }
    return undefined;
  }

  get fieldErrors(): FieldError[] {
    return Array.isArray(this.problem.errors) ? this.problem.errors : [];
  }
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body: unknown = await response.json();
    if (body !== null && typeof body === 'object') return body as ProblemDetail;
  } catch {
    // Not JSON: fall through to a bare problem.
  }
  return { status: response.status, title: response.statusText || 'Request failed' };
}

/** Called when a request that carried the app's Authorization header is answered 401. The session registers it. */
let unauthorizedHandler: (() => void) | null = null;

/**
 * Registers the handler run when the server rejects the app's own credentials (401 on a request that carried the
 * Authorization header). Returns a function that removes it again. A 401 on a request without the header (a public
 * call) never runs it.
 */
export function setUnauthorizedHandler(handler: (() => void) | null): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = null;
  };
}

/**
 * Every request uses `credentials: 'omit'`: the browser never adds cookies or a cached HTTP-auth credential (for
 * example one typed into a native Basic dialog). The only credentials that ever travel are the explicit Authorization
 * header that requestProtected sets for the signed-in user, so a signed-out quote is always anonymous (AC-01.13).
 */
export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'omit',
    headers: { Accept: 'application/json, application/problem+json', ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  return (await response.json()) as T;
}

/** The `Authorization: Basic ...` value for the signed-in user. Credentials only ever travel in this header. */
export function basicAuthHeader(credentials: Credentials): string {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`);
  let binary = '';
  bytes.forEach((byte) => {
    binary = binary.concat(String.fromCharCode(byte));
  });
  return `Basic ${btoa(binary)}`;
}

/**
 * Request with optional credentials. This is the only helper that attaches the Authorization header, so the public
 * product and quote-read calls (which use requestJson directly) never carry credentials. Quote creation also goes
 * through it, and sends no header when the caller passes null.
 */
export function requestProtected<T>(path: string, credentials: Credentials | null, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) };
  if (credentials) headers.Authorization = basicAuthHeader(credentials);
  return requestJson<T>(path, { ...init, headers }).catch((error: unknown) => {
    // The server rejected the app's own credentials: sign out, so they are not sent again. The caller still gets the
    // error and shows its fixed "sign in again" message.
    if (credentials && error instanceof ApiError && error.status === 401) unauthorizedHandler?.();
    throw error;
  });
}

/** JSON POST to a protected endpoint. */
export function postProtected<T>(path: string, credentials: Credentials | null, body: unknown): Promise<T> {
  return requestProtected<T>(path, credentials, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

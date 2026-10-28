import type { FieldError, ProblemDetail } from './types';

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

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json, application/problem+json', ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  return (await response.json()) as T;
}

import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Instance, InstanceSummary, Job, Problem, SolveOptions, SolveResult } from './models';

/** Typed client for the Java backend. In development `/api` is proxied to localhost:8080. */
@Injectable({ providedIn: 'root' })
export class VrpApi {
  private readonly http = inject(HttpClient);

  listInstances(): Promise<InstanceSummary[]> {
    return this.call(this.http.get<{ instances: InstanceSummary[] }>('/api/instances')).then((r) => r.instances);
  }

  getInstance(name: string): Promise<Instance> {
    return this.call(this.http.get<Instance>(`/api/instances/${encodeURIComponent(name)}`));
  }

  generate(customers: number, layout: 'uniform' | 'clustered', seed: number, routeSize: number): Promise<Instance> {
    return this.call(this.http.post<Instance>('/api/instances/generate', { customers, layout, seed, routeSize }));
  }

  solve(instance: Instance, options: SolveOptions): Promise<SolveResult> {
    return this.call(this.http.post<SolveResult>('/api/solve', { instance, options }));
  }

  submitJob(instance: Instance, options: SolveOptions): Promise<Job> {
    return this.call(this.http.post<Job>('/api/jobs', { instance, options }));
  }

  getJob(id: string): Promise<Job> {
    return this.call(this.http.get<Job>(`/api/jobs/${id}`));
  }

  /** Converts HTTP failures into a {@link Problem}, so the UI has a single error shape. */
  private async call<T>(obs: import('rxjs').Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(obs);
    } catch (e) {
      throw toProblem(e);
    }
  }
}

export function toProblem(e: unknown): Problem {
  if (e instanceof HttpErrorResponse) {
    const body = e.error as Partial<Problem> | null;
    if (body && typeof body === 'object' && typeof body.title === 'string') {
      return { title: body.title, status: e.status, detail: body.detail, errors: body.errors };
    }
    if (e.status === 0) {
      return { title: 'Backend not reachable', status: 0, detail: 'Start it with ./build.sh run (port 8080).' };
    }
    return { title: e.statusText || 'Request failed', status: e.status };
  }
  return { title: 'Unexpected error', status: 0, detail: String(e) };
}

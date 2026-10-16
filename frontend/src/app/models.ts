/** Wire types mirroring openapi.yaml. */

export interface Point {
  x: number;
  y: number;
}

export interface Customer extends Point {
  id: number;
  demand: number;
}

export type DistanceKind = 'euclidean' | 'euclidean-rounded';

export interface Instance {
  name: string;
  capacity: number;
  distance?: DistanceKind;
  depot: Point;
  customers: Customer[];
}

export interface InstanceSummary {
  name: string;
  customers: number;
  capacity: number;
  totalDemand: number;
  minVehicles: number;
}

export type Algorithm = 'savings' | 'local-search' | 'annealing' | 'exact';

export interface SolveOptions {
  algorithm: Algorithm;
  timeLimitMs?: number;
  seed?: number;
}

export interface RouteResult {
  customers: number[];
  load: number;
  cost: number;
}

export interface SolveResult {
  instance: string;
  algorithm: Algorithm;
  cost: number;
  vehicles: number;
  lowerBound: number;
  gapToLowerBound: number;
  initialCost: number;
  optimal: boolean;
  runtimeMs: number;
  iterations: number;
  routes: RouteResult[];
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface Job {
  id: string;
  status: JobStatus;
  instance: string;
  algorithm: Algorithm;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  cost?: number;
  error?: string;
  result?: SolveResult;
}

/** RFC 9457 problem document returned by the backend on errors. */
export interface Problem {
  title: string;
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}

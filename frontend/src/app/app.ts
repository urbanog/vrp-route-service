import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { Algorithm, Instance, InstanceSummary, Problem, SolveResult } from './models';
import { RouteMap } from './route-map';
import { routeColor } from './route-colors';
import { parseTsplib } from './tsplib';
import { VrpApi } from './vrp-api.service';

type Source = 'bundled' | 'generate' | 'upload';
type Mode = 'sync' | 'job';

@Component({
  selector: 'app-root',
  imports: [RouteMap, DecimalPipe, PercentPipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly api = inject(VrpApi);

  // ---- instance selection
  readonly source = signal<Source>('bundled');
  readonly catalog = signal<InstanceSummary[]>([]);
  readonly bundledName = signal('C-n50-k5');
  readonly genCustomers = signal(60);
  readonly genLayout = signal<'uniform' | 'clustered'>('clustered');
  readonly genSeed = signal(1);
  readonly genRouteSize = signal(10);
  readonly instance = signal<Instance | null>(null);

  // ---- solver options
  readonly algorithm = signal<Algorithm>('annealing');
  readonly timeLimitMs = signal(2000);
  readonly seed = signal(42);
  readonly mode = signal<Mode>('sync');

  // ---- results and state
  readonly result = signal<SolveResult | null>(null);
  readonly busy = signal(false);
  readonly status = signal('');
  readonly problem = signal<Problem | null>(null);
  readonly focus = signal<number | null>(null);

  readonly algorithms: { value: Algorithm; label: string; hint: string }[] = [
    { value: 'savings', label: 'Savings', hint: 'Clarke–Wright only. Instant.' },
    { value: 'local-search', label: 'Local search', hint: 'Savings, then 2-opt, relocate, swap and 2-opt* to a local optimum.' },
    { value: 'annealing', label: 'Annealing', hint: 'Local search plus ruin-and-recreate under simulated annealing until the time limit.' },
    { value: 'exact', label: 'Exact', hint: 'Proven optimum by dynamic programming. Up to 12 customers.' },
  ];

  readonly totalDemand = computed(() => this.instance()?.customers.reduce((s, c) => s + c.demand, 0) ?? 0);
  readonly minVehicles = computed(() => {
    const inst = this.instance();
    return inst ? Math.ceil(this.totalDemand() / inst.capacity) : 0;
  });
  readonly exactTooLarge = computed(() => this.algorithm() === 'exact' && (this.instance()?.customers.length ?? 0) > 12);
  readonly improvement = computed(() => {
    const r = this.result();
    return r && r.initialCost > 0 ? (r.initialCost - r.cost) / r.initialCost : 0;
  });
  readonly algorithmHint = computed(() => this.algorithms.find((a) => a.value === this.algorithm())?.hint ?? '');

  readonly color = routeColor;

  async ngOnInit(): Promise<void> {
    try {
      this.catalog.set(await this.api.listInstances());
      await this.loadBundled();
    } catch (e) {
      this.problem.set(e as Problem);
    }
  }

  setSource(s: Source): void {
    this.source.set(s);
    this.problem.set(null);
  }

  async loadBundled(name = this.bundledName()): Promise<void> {
    this.bundledName.set(name);
    await this.run('Loading instance…', async () => this.setInstance(await this.api.getInstance(name)));
  }

  async generate(): Promise<void> {
    await this.run('Generating…', async () =>
      this.setInstance(await this.api.generate(this.genCustomers(), this.genLayout(), this.genSeed(), this.genRouteSize())),
    );
  }

  async upload(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.problem.set(null);
    try {
      this.setInstance(parseTsplib(await file.text()));
    } catch (e) {
      this.problem.set({ title: `Could not read ${file.name}`, status: 0, detail: (e as Error).message });
    }
  }

  async solve(): Promise<void> {
    const inst = this.instance();
    if (!inst) return;
    const options = { algorithm: this.algorithm(), timeLimitMs: this.timeLimitMs(), seed: this.seed() };
    if (this.mode() === 'sync') {
      await this.run('Solving…', async () => this.result.set(await this.api.solve(inst, options)));
      return;
    }
    await this.run('Submitting job…', async () => {
      let job = await this.api.submitJob(inst, options);
      while (job.status === 'QUEUED' || job.status === 'RUNNING') {
        this.status.set(`Job ${job.id.slice(0, 8)} is ${job.status.toLowerCase()}…`);
        await new Promise((r) => setTimeout(r, 250));
        job = await this.api.getJob(job.id);
      }
      if (job.status === 'FAILED' || !job.result) {
        throw { title: 'Job failed', status: 500, detail: job.error } satisfies Problem;
      }
      this.result.set(job.result);
    });
  }

  numberInput(event: Event): number {
    return Number((event.target as HTMLInputElement).value);
  }

  private setInstance(inst: Instance): void {
    this.instance.set(inst);
    this.result.set(null);
    this.focus.set(null);
  }

  private async run(label: string, body: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.status.set(label);
    this.problem.set(null);
    try {
      await body();
    } catch (e) {
      this.problem.set(e as Problem);
    } finally {
      this.busy.set(false);
      this.status.set('');
    }
  }
}

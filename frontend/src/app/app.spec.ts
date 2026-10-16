import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { RouteMap } from './route-map';
import { GOLDEN_ANGLE, hueDistance, routeHue } from './route-colors';
import { parseTsplib } from './tsplib';
import { Instance, SolveResult } from './models';

/** Lets pending promise chains (firstValueFrom -> then -> next request) run to completion. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

const TINY: Instance = {
  name: 'tiny',
  capacity: 10,
  distance: 'euclidean',
  depot: { x: 0, y: 0 },
  customers: [
    { id: 1, x: 3, y: 4, demand: 4 },
    { id: 2, x: -3, y: 4, demand: 5 },
    { id: 3, x: 0, y: -5, demand: 6 },
  ],
};

const TINY_RESULT: SolveResult = {
  instance: 'tiny',
  algorithm: 'exact',
  cost: 26,
  vehicles: 2,
  lowerBound: 21,
  gapToLowerBound: 0.19,
  initialCost: 26,
  optimal: true,
  runtimeMs: 1,
  iterations: 0,
  routes: [
    { customers: [1, 2], load: 9, cost: 16 },
    { customers: [3], load: 6, cost: 10 },
  ],
};

describe('parseTsplib', () => {
  it('reads a CVRPLIB file and drops the depot from the customers', () => {
    const inst = parseTsplib(
      ['NAME : t', 'TYPE : CVRP', 'DIMENSION : 3', 'EDGE_WEIGHT_TYPE : EUC_2D', 'CAPACITY : 5',
        'NODE_COORD_SECTION', '1 0 0', '2 3 4', '3 6 8', 'DEMAND_SECTION', '1 0', '2 2', '3 3',
        'DEPOT_SECTION', ' 1', ' -1', 'EOF'].join('\n'),
    );
    expect(inst.capacity).toBe(5);
    expect(inst.distance).toBe('euclidean-rounded');
    expect(inst.depot).toEqual({ x: 0, y: 0 });
    expect(inst.customers.map((c) => c.id)).toEqual([2, 3]);
  });

  it('reports what is missing', () => {
    expect(() => parseTsplib('NAME : t\nNODE_COORD_SECTION\n1 0 0\nEOF')).toThrow(/CAPACITY/);
  });
});

describe('route colours', () => {
  it('keeps consecutive routes far apart on the colour wheel', () => {
    for (let i = 0; i < 50; i++) {
      expect(hueDistance(routeHue(i), routeHue(i + 1))).toBeCloseTo(GOLDEN_ANGLE, 6);
    }
  });
});

describe('RouteMap', () => {
  it('draws one closed polyline per route, starting and ending at the depot', async () => {
    const fixture = TestBed.createComponent(RouteMap);
    fixture.componentRef.setInput('instance', TINY);
    fixture.componentRef.setInput('result', TINY_RESULT);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const lines = el.querySelectorAll('polyline');
    expect(lines.length).toBe(2);
    expect(lines[0].getAttribute('points')).toBe('0,0 3,-4 -3,-4 0,0');
    expect(el.querySelectorAll('circle').length).toBe(3);
    expect(el.querySelector('svg')?.getAttribute('aria-label')).toContain('2 routes');
  });
});

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('loads the catalogue, solves, and lists one row per van', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne('/api/instances').flush({
      instances: [{ name: 'C-n50-k5', customers: 3, capacity: 10, totalDemand: 15, minVehicles: 2 }],
    });
    await settle();
    http.expectOne('/api/instances/C-n50-k5').flush(TINY);
    await settle();
    await fixture.whenStable();

    const app = fixture.componentInstance;
    app.algorithm.set('exact');
    const solving = app.solve();
    const req = http.expectOne('/api/solve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.options.algorithm).toBe('exact');
    req.flush(TINY_RESULT);
    await solving;
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.route-list li').length).toBe(2);
    expect(el.querySelector('.cost .value')?.textContent).toContain('26');
    http.verify();
  });

  it('shows every field error from a 422 response', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/instances').flush({ instances: [] });
    await settle();
    http.expectOne('/api/instances/C-n50-k5').flush(TINY);
    await settle();
    await fixture.whenStable();

    const solving = fixture.componentInstance.solve();
    http.expectOne('/api/solve').flush(
      {
        title: 'Validation Failed',
        status: 422,
        detail: '2 validation errors',
        errors: [
          { field: 'instance.capacity', message: 'is required' },
          { field: 'options.timeLimitMs', message: 'must be between 1 and 60000' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await solving;
    await fixture.whenStable();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role=alert]');
    expect(alert?.textContent).toContain('Validation Failed');
    expect(alert?.querySelectorAll('li').length).toBe(2);
  });
});

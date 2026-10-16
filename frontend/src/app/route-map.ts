import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Instance, SolveResult } from './models';
import { routeColor } from './route-colors';

interface DrawnRoute {
  index: number;
  points: string;
  color: string;
  length: number;
}

/**
 * SVG drawing of an instance and (optionally) its solution. Customer dot area is proportional to
 * demand; the depot is a square. Coordinates are flipped vertically so that +y points up, as in
 * the TSPLIB files.
 */
@Component({
  selector: 'app-route-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (instance(); as inst) {
      <svg
        [attr.viewBox]="frame().viewBox"
        role="img"
        [attr.aria-label]="ariaLabel()"
        preserveAspectRatio="xMidYMid meet"
      >
        <g class="grid">
          @for (g of frame().grid; track $index) {
            <line [attr.x1]="g.x1" [attr.y1]="g.y1" [attr.x2]="g.x2" [attr.y2]="g.y2" />
          }
        </g>
        <g class="routes" [class.has-focus]="focus() !== null">
          @for (r of routes(); track r.index) {
            <polyline
              [attr.points]="r.points"
              [style.stroke]="r.color"
              [style.--len]="r.length"
              [class.focused]="focus() === r.index"
              vector-effect="non-scaling-stroke"
            />
          }
        </g>
        <g class="customers">
          @for (c of inst.customers; track c.id) {
            <circle [attr.cx]="c.x" [attr.cy]="-c.y" [attr.r]="radius(c.demand)">
              <title>Customer {{ c.id }}, demand {{ c.demand }}</title>
            </circle>
          }
        </g>
        <rect
          class="depot"
          [attr.x]="inst.depot.x - frame().unit * 6"
          [attr.y]="-inst.depot.y - frame().unit * 6"
          [attr.width]="frame().unit * 12"
          [attr.height]="frame().unit * 12"
        >
          <title>Depot</title>
        </rect>
      </svg>
    }
  `,
  styleUrl: './route-map.css',
})
export class RouteMap {
  readonly instance = input<Instance | null>(null);
  readonly result = input<SolveResult | null>(null);
  /** Index of the route to emphasise, or null. */
  readonly focus = input<number | null>(null);

  /** viewBox, a scale unit (~1 px at 640 px width) and a light reference grid. */
  readonly frame = computed(() => {
    const inst = this.instance();
    if (!inst) return { viewBox: '0 0 1 1', unit: 1, grid: [] };
    const xs = [inst.depot.x, ...inst.customers.map((c) => c.x)];
    const ys = [-inst.depot.y, ...inst.customers.map((c) => -c.y)];
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const extent = Math.max(maxX - minX, maxY - minY, 1);
    const pad = extent * 0.05;
    const size = extent + 2 * pad;
    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    const x0 = cx - size / 2;
    const y0 = cy - size / 2;
    const step = niceStep(size / 8);
    const grid = [];
    for (let x = Math.ceil(x0 / step) * step; x <= x0 + size; x += step) {
      grid.push({ x1: x, y1: y0, x2: x, y2: y0 + size });
    }
    for (let y = Math.ceil(y0 / step) * step; y <= y0 + size; y += step) {
      grid.push({ x1: x0, y1: y, x2: x0 + size, y2: y });
    }
    return { viewBox: `${x0} ${y0} ${size} ${size}`, unit: size / 640, grid };
  });

  private readonly meanDemand = computed(() => {
    const cs = this.instance()?.customers ?? [];
    const total = cs.reduce((s, c) => s + c.demand, 0);
    return cs.length && total > 0 ? total / cs.length : 1;
  });

  readonly routes = computed<DrawnRoute[]>(() => {
    const inst = this.instance();
    const res = this.result();
    if (!inst || !res) return [];
    const byId = new Map(inst.customers.map((c) => [c.id, c]));
    return res.routes.map((r, index) => {
      const pts = [inst.depot, ...r.customers.map((id) => byId.get(id)!).filter(Boolean), inst.depot];
      let length = 0;
      for (let i = 1; i < pts.length; i++) length += Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y);
      return {
        index,
        points: pts.map((p) => `${p.x},${-p.y}`).join(' '),
        color: routeColor(index),
        length: length / this.frame().unit,
      };
    });
  });

  readonly ariaLabel = computed(() => {
    const inst = this.instance();
    const res = this.result();
    if (!inst) return '';
    const base = `Map of ${inst.customers.length} customers around one depot`;
    return res ? `${base}, served by ${res.routes.length} routes` : base;
  });

  radius(demand: number): number {
    // Area proportional to demand, 4 px for an average customer.
    return this.frame().unit * 4 * Math.sqrt(Math.max(demand, 0.25) / this.meanDemand());
  }
}

/** 1, 2 or 5 times a power of ten, at least `raw`. */
function niceStep(raw: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 5, 10]) if (m * p >= raw) return m * p;
  return 10 * p;
}

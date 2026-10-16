import { Customer, Instance } from './models';

/**
 * Parses a CVRPLIB/TSPLIB `.vrp` file (EUC_2D or EXACT_2D, single depot) in the browser, so an
 * uploaded file can be drawn before it is solved. Mirrors the Java TsplibReader.
 */
export function parseTsplib(text: string): Instance {
  const header = new Map<string, string>();
  const coords = new Map<number, { x: number; y: number }>();
  const demands = new Map<number, number>();
  const depots: number[] = [];
  let section: string | null = null;

  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    const lineNo = i + 1;
    if (line === '' ) continue;
    if (line === 'EOF') break;
    const upper = line.toUpperCase();
    if (upper.endsWith('_SECTION')) {
      if (!['NODE_COORD_SECTION', 'DEMAND_SECTION', 'DEPOT_SECTION'].includes(upper)) {
        throw new Error(`line ${lineNo}: unsupported section ${upper}`);
      }
      section = upper;
      continue;
    }
    const colon = line.indexOf(':');
    if (section === null || (colon > 0 && /^[A-Za-z]/.test(line))) {
      if (colon < 0) throw new Error(`line ${lineNo}: expected "KEY : value"`);
      header.set(line.slice(0, colon).trim().toUpperCase(), line.slice(colon + 1).trim());
      section = null;
      continue;
    }
    const tok = line.split(/\s+/).map(Number);
    if (tok.some((t) => Number.isNaN(t))) throw new Error(`line ${lineNo}: not a number`);
    if (section === 'NODE_COORD_SECTION') {
      if (tok.length !== 3) throw new Error(`line ${lineNo}: expected "id x y"`);
      coords.set(tok[0], { x: tok[1], y: tok[2] });
    } else if (section === 'DEMAND_SECTION') {
      if (tok.length !== 2) throw new Error(`line ${lineNo}: expected "id demand"`);
      demands.set(tok[0], tok[1]);
    } else {
      for (const t of tok) {
        if (t === -1) {
          section = null;
          break;
        }
        depots.push(t);
      }
    }
  }

  const type = header.get('TYPE') ?? 'CVRP';
  if (type.toUpperCase() !== 'CVRP') throw new Error(`TYPE must be CVRP, got ${type}`);
  const ewt = (header.get('EDGE_WEIGHT_TYPE') ?? 'EUC_2D').toUpperCase();
  if (ewt !== 'EUC_2D' && ewt !== 'EXACT_2D') throw new Error(`unsupported EDGE_WEIGHT_TYPE ${ewt}`);
  const capacity = Number(header.get('CAPACITY'));
  if (!Number.isInteger(capacity)) throw new Error('missing or non-integer CAPACITY');
  const depotId = depots[0] ?? 1;
  if (depots.length > 1) throw new Error('exactly one depot is supported');
  const depot = coords.get(depotId);
  if (!depot) throw new Error(`depot ${depotId} has no coordinates`);

  const customers: Customer[] = [];
  for (const [id, p] of coords) {
    if (id === depotId) continue;
    const demand = demands.get(id);
    if (demand === undefined) throw new Error(`node ${id} has no demand`);
    customers.push({ id, x: p.x, y: p.y, demand });
  }
  return {
    name: header.get('NAME') ?? 'uploaded',
    capacity,
    distance: ewt === 'EUC_2D' ? 'euclidean-rounded' : 'euclidean',
    depot,
    customers,
  };
}

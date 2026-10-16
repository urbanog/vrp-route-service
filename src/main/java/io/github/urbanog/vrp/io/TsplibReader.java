package io.github.urbanog.vrp.io;

import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reader for CVRP instances in the TSPLIB 95 format used by CVRPLIB.
 *
 * <p>Supported: {@code TYPE: CVRP}, {@code EDGE_WEIGHT_TYPE: EUC_2D} (rounded, the CVRPLIB
 * convention) or the non-standard {@code EXACT_2D} (unrounded), sections {@code
 * NODE_COORD_SECTION}, {@code DEMAND_SECTION}, {@code DEPOT_SECTION} (a single depot, terminated
 * by {@code -1}) and {@code EOF}. Header keys may be written {@code KEY: value} or {@code KEY :
 * value}. Explicit edge-weight matrices are rejected with a clear message.
 */
public final class TsplibReader {
    private TsplibReader() {}

    public static Instance parse(String text) {
        String[] lines = text.split("\\R");
        Map<String, String> header = new LinkedHashMap<>();
        Map<Integer, Point> coords = new LinkedHashMap<>();
        Map<Integer, Integer> demands = new HashMap<>();
        List<Integer> depots = new ArrayList<>();
        String section = null;

        for (int ln = 0; ln < lines.length; ln++) {
            int lineNo = ln + 1;
            String line = lines[ln].trim();
            if (line.isEmpty()) continue;
            if (line.equals("EOF")) break;
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.endsWith("_SECTION")) {
                section = upper;
                if (!section.equals("NODE_COORD_SECTION") && !section.equals("DEMAND_SECTION")
                        && !section.equals("DEPOT_SECTION")) {
                    throw new VrpFormatException("unsupported section " + section, lineNo);
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (section == null || (colon > 0 && Character.isLetter(line.charAt(0)))) {
                if (colon < 0) throw new VrpFormatException("expected 'KEY : value'", lineNo);
                String key = line.substring(0, colon).trim().toUpperCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                header.put(key, value);
                section = null;
                continue;
            }
            String[] tok = line.split("\\s+");
            try {
                switch (section) {
                    case "NODE_COORD_SECTION" -> {
                        if (tok.length != 3) throw new VrpFormatException("expected 'id x y'", lineNo);
                        int id = Integer.parseInt(tok[0]);
                        Point pt = new Point(Double.parseDouble(tok[1]), Double.parseDouble(tok[2]));
                        if (coords.put(id, pt) != null) throw new VrpFormatException("duplicate node " + id, lineNo);
                    }
                    case "DEMAND_SECTION" -> {
                        if (tok.length != 2) throw new VrpFormatException("expected 'id demand'", lineNo);
                        demands.put(Integer.parseInt(tok[0]), Integer.parseInt(tok[1]));
                    }
                    case "DEPOT_SECTION" -> {
                        for (String t : tok) {
                            int d = Integer.parseInt(t);
                            if (d == -1) {
                                section = null;
                                break;
                            }
                            depots.add(d);
                        }
                    }
                    default -> throw new VrpFormatException("data outside of a section", lineNo);
                }
            } catch (NumberFormatException e) {
                throw new VrpFormatException("not a number: " + e.getMessage(), lineNo);
            }
        }

        String type = header.getOrDefault("TYPE", "CVRP");
        if (!type.equalsIgnoreCase("CVRP")) throw new VrpFormatException("TYPE must be CVRP, got " + type, 1);
        String ewt = header.getOrDefault("EDGE_WEIGHT_TYPE", "EUC_2D").toUpperCase(Locale.ROOT);
        DistanceType dt = switch (ewt) {
            case "EUC_2D" -> DistanceType.EUCLIDEAN_ROUNDED;
            case "EXACT_2D" -> DistanceType.EUCLIDEAN;
            default -> throw new VrpFormatException("unsupported EDGE_WEIGHT_TYPE " + ewt, 1);
        };
        if (!header.containsKey("CAPACITY")) throw new VrpFormatException("missing CAPACITY", 1);
        int capacity;
        try {
            capacity = Integer.parseInt(header.get("CAPACITY"));
        } catch (NumberFormatException e) {
            throw new VrpFormatException("CAPACITY is not an integer", 1);
        }
        if (header.containsKey("DIMENSION")) {
            int dim = Integer.parseInt(header.get("DIMENSION"));
            if (dim != coords.size()) {
                throw new VrpFormatException("DIMENSION " + dim + " but " + coords.size() + " coordinates", 1);
            }
        }
        if (depots.isEmpty()) depots.add(1); // TSPLIB default
        if (depots.size() != 1) throw new VrpFormatException("exactly one depot is supported", 1);
        int depotId = depots.get(0);
        Point depot = coords.get(depotId);
        if (depot == null) throw new VrpFormatException("depot " + depotId + " has no coordinates", 1);

        List<Customer> customers = new ArrayList<>();
        for (var e : coords.entrySet()) {
            if (e.getKey() == depotId) continue;
            Integer q = demands.get(e.getKey());
            if (q == null) throw new VrpFormatException("node " + e.getKey() + " has no demand", 1);
            customers.add(new Customer(e.getKey(), e.getValue(), q));
        }
        String name = header.getOrDefault("NAME", "unnamed");
        return new Instance(name, capacity, dt, depot, customers);
    }

    /** Writes an instance in the same format (EUC_2D or EXACT_2D); the depot gets node id 1. */
    public static String write(Instance inst) {
        StringBuilder sb = new StringBuilder();
        sb.append("NAME : ").append(inst.name()).append('\n');
        sb.append("TYPE : CVRP\n");
        sb.append("DIMENSION : ").append(inst.size() + 1).append('\n');
        sb.append("EDGE_WEIGHT_TYPE : ")
                .append(inst.distanceType() == DistanceType.EUCLIDEAN ? "EXACT_2D" : "EUC_2D")
                .append('\n');
        sb.append("CAPACITY : ").append(inst.capacity()).append('\n');
        sb.append("NODE_COORD_SECTION\n");
        sb.append("1 ").append(fmt(inst.depot().x())).append(' ').append(fmt(inst.depot().y())).append('\n');
        for (Customer c : inst.customers()) {
            sb.append(c.id()).append(' ').append(fmt(c.location().x())).append(' ')
                    .append(fmt(c.location().y())).append('\n');
        }
        sb.append("DEMAND_SECTION\n1 0\n");
        for (Customer c : inst.customers()) sb.append(c.id()).append(' ').append(c.demand()).append('\n');
        sb.append("DEPOT_SECTION\n1\n-1\nEOF\n");
        return sb.toString();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}

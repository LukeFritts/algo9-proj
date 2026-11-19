/**
 * Dijkstra with an array scan.
 *
 * Each round, look through every unvisited vertex and pick the one with the
 * smallest distance. No queue at all. O(V^2 + E) time: V rounds of an O(V)
 * scan, plus every edge relaxed once.
 *
 * On dense graphs (E close to V^2) this is as good as it gets, since just
 * reading the edges already costs V^2. On sparse graphs the V^2 scanning is
 * mostly wasted work, which is where the heaps win.
 *
 * Counters used: countArrayScan() for each vertex looked at during a scan,
 * countRelaxation() for each edge checked.
 */
public class ArrayDijkstra implements ShortestPathAlgorithm {

    @Override
    public String name() {
        return "array";
    }

    @Override
    public Result run(Graph g, int source, Counters counters) {
        int vertexCount = g.vertexCount();
        Result result = Result.start(vertexCount, source);
        long[] dist = result.dist();
        int[] parent = result.parent();
        boolean[] visited = new boolean[vertexCount];

        for (int round = 0; round < vertexCount; round++) {
            // Scan every unvisited vertex for the smallest distance.
            int u = -1;
            for (int v = 0; v < vertexCount; v++) {
                if (visited[v]) {
                    continue;
                }
                counters.countArrayScan();
                if (u == -1 || dist[v] < dist[u]) {
                    u = v;
                }
            }

            // Nothing left, or everything left is unreachable: we're done.
            if (u == -1 || dist[u] == Result.INFINITY) {
                break;
            }
            visited[u] = true;

            // Try to get to each neighbor faster by going through u.
            for (Edge e : g.edgesFrom(u)) {
                counters.countRelaxation();
                long newDist = dist[u] + e.weight();
                if (newDist < dist[e.to()]) {
                    dist[e.to()] = newDist;
                    parent[e.to()] = u;
                }
            }
        }

        return result;
    }
}

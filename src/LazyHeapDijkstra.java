import java.util.PriorityQueue;

/**
 * Dijkstra with Java's PriorityQueue and lazy deletion.
 *
 * PriorityQueue can't do decrease-key, so whenever a distance gets better we
 * just add another entry for that vertex. The old entry stays in the queue.
 * When an entry comes out for a vertex that's already finished, it's
 * outdated and we skip it.
 *
 * O(E log V) time on our graphs: every edge can add at most one entry, and
 * each add or remove costs O(log E), which is O(log V) since E < V^2.
 * The trade-off is space: the queue can hold up to O(E) entries instead of
 * the indexed heap's V.
 *
 * Counters used: countInsert(), countExtractMin(), countStaleSkipped(),
 * countRelaxation(), recordQueueSize().
 */
public class LazyHeapDijkstra implements ShortestPathAlgorithm {

    @Override
    public String name() {
        return "lazy-heap";
    }

    @Override
    public Result run(Graph g, int source, Counters counters) {
        int vertexCount = g.vertexCount();
        Result result = Result.start(vertexCount, source);
        long[] dist = result.dist();
        int[] parent = result.parent();
        boolean[] done = new boolean[vertexCount];

        // Each entry is {distance, vertex}. Smallest distance comes out first.
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        queue.add(new long[] {0, source});
        counters.countInsert();
        counters.recordQueueSize(queue.size());

        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            counters.countExtractMin();
            int u = (int) entry[1];

            // An old entry for a vertex we already finished: skip it.
            if (done[u]) {
                counters.countStaleSkipped();
                continue;
            }
            done[u] = true;

            // Try to get to each neighbor faster by going through u.
            for (Edge e : g.edgesFrom(u)) {
                counters.countRelaxation();
                int v = e.to();
                long newDist = dist[u] + e.weight();

                if (newDist < dist[v]) {
                    dist[v] = newDist;
                    parent[v] = u;
                    // No decrease-key: just add a new entry with the better distance.
                    queue.add(new long[] {newDist, v});
                    counters.countInsert();
                    counters.recordQueueSize(queue.size());
                }
            }
        }

        return result;
    }
}

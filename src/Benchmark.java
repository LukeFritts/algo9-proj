import java.util.List;
import java.io.*;
import java.nio.file.*;

/**
 * Times every version on the benchmark graphs and saves the results as CSV.
 * Owner: Luke.
 *
 * Run it with:
 *   javac -d out src/*.java
 *   java -Xmx4g -cp out Benchmark
 *
 * (-Xmx4g gives Java up to 4 GB of memory, for the dense V = 3,000 graphs.)
 *
 * Rules from the proposal (section 4):
 * - 10 graphs per setup (seeds 1 to 10)
 * - warm up the JVM before timing anything
 * - repeat each timed run and keep every time, so we can get the median + IQR
 * - generating graphs and file I/O are NOT part of the timing
 * - counting runs are separate from timing runs
 * - write down the hardware, JVM version and heap settings
 */
public class Benchmark {

    /** Sizes every version runs on. */
    static final int[] SIZES = {100, 300, 1000, 3000};

    /** Extra sizes, sparse only, heap versions only (the array version would take too long). */
    static final int[] BIG_SPARSE_SIZES = {10_000, 100_000};

    static final int GRAPHS_PER_SETUP = 10;   // seeds 1..10
    static final int TIMED_REPEATS = 5;       // timed runs per graph (pick what the pilot run says is OK)

    static final List<ShortestPathAlgorithm> VERSIONS = List.of(new IndexedHeapDijkstra()); //Tests.VERSIONS; ** CHANGED TEMPORARILY WHILE WAITING FOR OTHER METHODS

    public static void main(String[] args) throws Exception {
        printMachineInfo();

        // TODO (Luke): the steps, in order
        //
        // 1. Warm up: run each version on a few medium graphs a bunch of times
        //    and throw the times away. Java speeds code up after it's run a
        //    while, so the first runs are always slow and would mess up results.
        
        System.out.println("Doing warmup...");

        Graph warmupGraph = GraphGenerator.generate(1000, GraphGenerator.Density.MEDIUM, 111);

        // run every algorithm 10 times on medium graph
        for(ShortestPathAlgorithm version : VERSIONS) {
            for (int i = 0; i < 10; i++) {
                version.run(warmupGraph, GraphGenerator.SOURCE, Counters.off());
            }
        }

        System.out.println("Warmup done.\n");

        // 2. Timing runs. For each size in SIZES, each Density, each seed 1..10:
        //      Graph g = GraphGenerator.generate(size, density, seed);   // not timed
    
        
        GraphGenerator.Density[] densities = GraphGenerator.Density.values();

        StringBuilder csv = new StringBuilder();
        csv.append("version, V, E, density, seed, repeat, nanos").append("\n");

        System.out.println("Doing benchmarks...");

        for (int size : SIZES) {
            for (GraphGenerator.Density density : densities) {
                for (int seed = 1; seed <= GRAPHS_PER_SETUP; seed++) {

                    Graph g = GraphGenerator.generate(size, density, seed);
                    int edges = g.edgeCount();

                    System.out.println("Testing size " + size + ", density " + density + ", seed " + seed);
                    
                    //      for each version:
                    //          repeat TIMED_REPEATS times:
                    //              long start = System.nanoTime();
                    //              version.run(g, GraphGenerator.SOURCE, Counters.off());
                    //              long nanos = System.nanoTime() - start;
                    //              -> one CSV row: version, V, E, density, seed, repeat, nanos
                    //    Then the same for BIG_SPARSE_SIZES, heap versions only
                    //    (skip the one whose name() is "array").
                    // 3. Counting runs. Same graphs, but run each version once with
                    //    Counters.on() and write a second CSV:
                    //      version, V, E, density, seed, then Counters.csvHeader() columns

                    for (ShortestPathAlgorithm version : VERSIONS) {
                        for (int repeat = 1; repeat <= TIMED_REPEATS; repeat++) {
                            
                            // run and log time
                            long start = System.nanoTime();
                            version.run(g, GraphGenerator.SOURCE, Counters.off());
                            long nanos = System.nanoTime() - start;

                            // make csv with run and time information
                            csv.append(version.name()).append(", ")
                            .append(size).append(", ")
                            .append(edges).append(", ")
                            .append(density).append(", ")
                            .append(seed).append(", ")
                            .append(repeat).append(", ")
                            .append(nanos).append("\n");

                        }
                    }

                }
            }
        }

        // 4. Save to results/times.csv and results/counts.csv
        //    (make the results folder if it isn't there).
        //
        // Median + IQR can be worked out from the CSV later (Python or a
        // spreadsheet), so the harness only needs to save the raw times.

        // create results folder if doesn't exist, and write csv string to times.csv inside
        Files.createDirectories(Paths.get("results"));
        Files.writeString(Paths.get("results/times.csv"), csv.toString());

        System.out.println(
            "\nBenhcmarks done, saved to 'results/times.csv'."
        );

        System.out.println("Benchmark not finished yet, still needs big sparse graphs and counting runs.");
    }

    /** Prints what we need to write down about the machine (proposal section 4). */
    static void printMachineInfo() {
        Runtime rt = Runtime.getRuntime();
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("JVM: " + System.getProperty("java.vm.name"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("CPU cores: " + rt.availableProcessors());
        System.out.println("Max heap: " + rt.maxMemory() / 1_000_000 + " MB");
        System.out.println("(Also write down the CPU model and RAM from your system settings.)");
        System.out.println();
    }
}

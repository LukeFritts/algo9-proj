import java.util.ArrayList;
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

    /** Every version that's finished. Unfinished ones get skipped, so the full run picks them up once they're done. */
    static final List<ShortestPathAlgorithm> VERSIONS = finishedVersions();

    /** Tries each version on a tiny graph and keeps the ones that don't say "not done yet". */
    static List<ShortestPathAlgorithm> finishedVersions() {
        List<ShortestPathAlgorithm> done = new ArrayList<>();
        Graph tiny = GraphGenerator.generate(5, GraphGenerator.Density.SPARSE, 1);
        for (ShortestPathAlgorithm version : Tests.VERSIONS) {
            try {
                version.run(tiny, GraphGenerator.SOURCE, Counters.off());
                done.add(version);
            } catch (UnsupportedOperationException e) {
                System.out.println("Skipping " + version.name() + ": not done yet");
            }
        }
        return done;
    }

    public static void main(String[] args) throws Exception {
        printMachineInfo();

        // Warm up. Runs each version on a few medium graphs a bunch of times and throws the times away.
        
        System.out.println("Doing warmup...");

        Graph warmupGraph = GraphGenerator.generate(1000, GraphGenerator.Density.MEDIUM, 111);

        // run every algorithm 10 times on medium graph
        for(ShortestPathAlgorithm version : VERSIONS) {
            for (int i = 0; i < 10; i++) {
                version.run(warmupGraph, GraphGenerator.SOURCE, Counters.off());
            }
        }

        System.out.println("Warmup done.\n");


        
        GraphGenerator.Density[] densities = GraphGenerator.Density.values();

        // what will go into times.csv
        StringBuilder csv = new StringBuilder();
        // plain commas (no spaces) so Python / spreadsheets read the column names cleanly
        csv.append("version,V,E,density,seed,repeat,nanos").append("\n");

        // what will go into counts.csv
        StringBuilder countsCsv = new StringBuilder();
        countsCsv.append("version,V,E,density,seed,").append(Counters.csvHeader()).append("\n");



        // BENCHMARKS START
        System.out.println("Doing benchmarks...");

        for (int size : SIZES) {
            for (GraphGenerator.Density density : densities) {
                for (int seed = 1; seed <= GRAPHS_PER_SETUP; seed++) {

                    Graph g = GraphGenerator.generate(size, density, seed);
                    int edges = g.edgeCount();

                    System.out.println("Testing size " + size + ", density " + density + ", seed " + seed);

                    for (ShortestPathAlgorithm version : VERSIONS) {

                        // timing runs
                        for (int repeat = 1; repeat <= TIMED_REPEATS; repeat++) {
                            
                            // run and log time
                            long start = System.nanoTime();
                            version.run(g, GraphGenerator.SOURCE, Counters.off());
                            long nanos = System.nanoTime() - start;

                            csv.append(version.name()).append(",")
                                .append(size).append(",")
                                .append(edges).append(",")
                                .append(density).append(",")
                                .append(seed).append(",")
                                .append(repeat).append(",")
                                .append(nanos).append("\n");

                        }

                        //  counting runs
                        Counters counters = Counters.on();
                        version.run(g, GraphGenerator.SOURCE, counters);

                        countsCsv.append(version.name()).append(",")
                            .append(size).append(",")
                            .append(edges).append(",")
                            .append(density).append(",")
                            .append(seed).append(",")
                            .append(counters.toCsvRow()).append("\n");

                    }

                }
            }
        }

        // Big sparse graphs
        System.out.println("\nDoing big sparse benchmarks...\n");

        for (int size : BIG_SPARSE_SIZES) {
            for (int seed = 1; seed <= GRAPHS_PER_SETUP; seed++) {

                // only doing big sparse graphs, no need for variable density
                Graph g = GraphGenerator.generate(size, GraphGenerator.Density.SPARSE, seed);
                int edges = g.edgeCount();

                System.out.println("Testing size " + size + ", density " + GraphGenerator.Density.SPARSE + ", seed " + seed);

                for (ShortestPathAlgorithm version : VERSIONS) {

                    // skip version name() "array"
                    if (version.name().equals("array")) {
                        continue;
                    }

                    // timing runs
                    for (int repeat = 1; repeat <= TIMED_REPEATS; repeat++) {
                        
                        // run and log time
                        long start = System.nanoTime();
                        version.run(g, GraphGenerator.SOURCE, Counters.off());
                        long nanos = System.nanoTime() - start;

                        csv.append(version.name()).append(",")
                            .append(size).append(",")
                            .append(edges).append(",")
                            .append(GraphGenerator.Density.SPARSE).append(",")
                            .append(seed).append(",")
                            .append(repeat).append(",")
                            .append(nanos).append("\n");

                    }

                    // counting runs
                    Counters counters = Counters.on();
                    version.run(g, GraphGenerator.SOURCE, counters);

                    countsCsv.append(version.name()).append(",")
                        .append(size).append(",")
                        .append(edges).append(",")
                        .append(GraphGenerator.Density.SPARSE).append(",")
                        .append(seed).append(",")
                        .append(counters.toCsvRow()).append("\n");

                }
            }
        }



        // create results folder if doesn't exist
        Files.createDirectories(Paths.get("results"));

        // write to csv files inside
        Files.writeString(Paths.get("results/times.csv"), csv.toString());
        Files.writeString(Paths.get("results/counts.csv"), countsCsv.toString());

        System.out.println(
            "\nBenchmarks done, saved to 'results/times.csv' and 'results/counts.csv'."
        );

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

package benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coleta e calcula métricas de desempenho.
 */
public class BenchmarkResults {

  private final String name;
  private final List<Long> latencies;
  private final AtomicLong successCount;
  private final AtomicLong errorCount;
  private long startTimeNanos;
  private long endTimeNanos;

  public BenchmarkResults(String name) {
    this.name = name;
    this.latencies = Collections.synchronizedList(new ArrayList<>());
    this.successCount = new AtomicLong(0);
    this.errorCount = new AtomicLong(0);
  }

  public void start() {
    this.startTimeNanos = System.nanoTime();
  }

  public void stop() {
    this.endTimeNanos = System.nanoTime();
  }

  public void recordLatency(long latencyNanos) {
    latencies.add(latencyNanos);
    successCount.incrementAndGet();
  }

  public void recordError() {
    errorCount.incrementAndGet();
  }

  public long getTotalOperations() {
    return successCount.get() + errorCount.get();
  }

  public long getSuccessCount() {
    return successCount.get();
  }

  public long getErrorCount() {
    return errorCount.get();
  }

  public double getElapsedSeconds() {
    return (endTimeNanos - startTimeNanos) / 1_000_000_000.0;
  }

  public double getThroughput() {
    double elapsed = getElapsedSeconds();
    if (elapsed <= 0) return 0;
    return successCount.get() / elapsed;
  }

  public double getAverageLatencyMs() {
    if (latencies.isEmpty()) return 0;
    long sum = 0;
    for (Long l : latencies) {
      sum += l;
    }
    return (sum / (double) latencies.size()) / 1_000_000.0;
  }

  public double getMinLatencyMs() {
    if (latencies.isEmpty()) return 0;
    long min = Long.MAX_VALUE;
    for (Long l : latencies) {
      if (l < min) min = l;
    }
    return min / 1_000_000.0;
  }

  public double getMaxLatencyMs() {
    if (latencies.isEmpty()) return 0;
    long max = 0;
    for (Long l : latencies) {
      if (l > max) max = l;
    }
    return max / 1_000_000.0;
  }

  public double getPercentileLatencyMs(double percentile) {
    if (latencies.isEmpty()) return 0;
    List<Long> sorted = new ArrayList<>(latencies);
    Collections.sort(sorted);
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    if (index < 0) index = 0;
    if (index >= sorted.size()) index = sorted.size() - 1;
    return sorted.get(index) / 1_000_000.0;
  }

  public double getP50LatencyMs() {
    return getPercentileLatencyMs(50);
  }

  public double getP95LatencyMs() {
    return getPercentileLatencyMs(95);
  }

  public double getP99LatencyMs() {
    return getPercentileLatencyMs(99);
  }

  public double getErrorRate() {
    long total = getTotalOperations();
    if (total == 0) return 0;
    return (errorCount.get() * 100.0) / total;
  }

  public void printReport() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.printf("║  BENCHMARK: %-50s║%n", name);
    System.out.println("╠══════════════════════════════════════════════════════════════╣");
    System.out.printf("║  Total Operations:     %-39d║%n", getTotalOperations());
    System.out.printf("║  Successful:           %-39d║%n", getSuccessCount());
    System.out.printf("║  Errors:               %-39d║%n", getErrorCount());
    System.out.printf("║  Error Rate:           %-38.2f%%║%n", getErrorRate());
    System.out.println("╠══════════════════════════════════════════════════════════════╣");
    System.out.printf("║  Elapsed Time:         %-36.2f s ║%n", getElapsedSeconds());
    System.out.printf("║  Throughput:           %-34.2f ops/s ║%n", getThroughput());
    System.out.println("╠══════════════════════════════════════════════════════════════╣");
    System.out.println("║  Latency (ms):                                               ║");
    System.out.printf("║    Average:            %-39.3f║%n", getAverageLatencyMs());
    System.out.printf("║    Min:                %-39.3f║%n", getMinLatencyMs());
    System.out.printf("║    Max:                %-39.3f║%n", getMaxLatencyMs());
    System.out.printf("║    P50 (median):       %-39.3f║%n", getP50LatencyMs());
    System.out.printf("║    P95:                %-39.3f║%n", getP95LatencyMs());
    System.out.printf("║    P99:                %-39.3f║%n", getP99LatencyMs());
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
  }

  public String getName() {
    return name;
  }

  /** Formato CSV para tabelas no relatório */
  public String toCSV() {
    return String.format(
        "%s,%.2f,%.2f,%.3f,%.3f,%.3f,%.2f",
        name,
        getThroughput(),
        getElapsedSeconds(),
        getAverageLatencyMs(),
        getP95LatencyMs(),
        getP99LatencyMs(),
        getErrorRate());
  }

  public static String csvHeader() {
    return "Name,Throughput(ops/s),Elapsed(s),AvgLatency(ms),P95(ms),P99(ms),ErrorRate(%)";
  }
}

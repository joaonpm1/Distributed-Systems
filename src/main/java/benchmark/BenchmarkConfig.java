package benchmark;

/**
 * Configuração para os benchmarks.
 */
public class BenchmarkConfig {

  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 8080;

  private String host;
  private int port;
  private int numClients;
  private int operationsPerClient;
  private int warmupOperations;

  public BenchmarkConfig() {
    this.host = DEFAULT_HOST;
    this.port = DEFAULT_PORT;
    this.numClients = 16;
    this.operationsPerClient = 1000;
    this.warmupOperations = 100;
  }

  public BenchmarkConfig host(String host) {
    this.host = host;
    return this;
  }

  public BenchmarkConfig port(int port) {
    this.port = port;
    return this;
  }

  public BenchmarkConfig numClients(int numClients) {
    this.numClients = numClients;
    return this;
  }

  public BenchmarkConfig operationsPerClient(int ops) {
    this.operationsPerClient = ops;
    return this;
  }

  public BenchmarkConfig warmupOperations(int warmup) {
    this.warmupOperations = warmup;
    return this;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public int getNumClients() {
    return numClients;
  }

  public int getOperationsPerClient() {
    return operationsPerClient;
  }

  public int getWarmupOperations() {
    return warmupOperations;
  }

  @Override
  public String toString() {
    return String.format(
        "BenchmarkConfig{host=%s, port=%d, clients=%d, ops/client=%d, warmup=%d}",
        host, port, numClients, operationsPerClient, warmupOperations);
  }
}

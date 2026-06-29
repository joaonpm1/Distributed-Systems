package benchmark;

import client.ClientLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Benchmark de escalabilidade.
 * 
 * Testa o desempenho do sistema com número crescente de clientes:
 * 1, 2, 4, 8, 16, 32, 64 clientes concorrentes.
 */
public class ScalabilityBenchmark {

  private static final String[] PRODUCTS = {
      "Laptop", "Mouse", "Teclado", "Monitor", "Webcam",
      "Headset", "SSD", "RAM", "GPU", "CPU"
  };

  private static final int[] CLIENT_COUNTS = {1, 2, 4, 8, 16, 32, 64, 128};

  private final BenchmarkConfig baseConfig;

  public ScalabilityBenchmark(BenchmarkConfig baseConfig) {
    this.baseConfig = baseConfig;
  }

  public void run() throws Exception {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("         SCALABILITY BENCHMARK - Escalabilidade com clientes");
    System.out.println("=".repeat(70));

    // Setup
    setupData();

    List<BenchmarkResults> allResults = new ArrayList<>();

    for (int numClients : CLIENT_COUNTS) {
      BenchmarkResults result = runWithClients(numClients);
      allResults.add(result);
    }

    // Sumário em formato tabela
    System.out.println("\n" + "=".repeat(70));
    System.out.println("                    RESULTADOS DE ESCALABILIDADE");
    System.out.println("=".repeat(70));
    System.out.println();
    System.out.printf("%-10s %-15s %-15s %-15s %-12s%n", 
        "Clientes", "Throughput", "Avg Lat (ms)", "P99 Lat (ms)", "Erros (%)");
    System.out.println("-".repeat(70));

    for (BenchmarkResults r : allResults) {
      String clientsStr = r.getName().replace(" clientes", "");
      System.out.printf("%-10s %-15.2f %-15.3f %-15.3f %-12.2f%n",
          clientsStr,
          r.getThroughput(),
          r.getAverageLatencyMs(),
          r.getP99LatencyMs(),
          r.getErrorRate());
    }

    // CSV para gráficos
    System.out.println("\n[CSV para gráficos]");
    System.out.println("Clients,Throughput,AvgLatency,P99Latency,ErrorRate");
    for (BenchmarkResults r : allResults) {
      String clients = r.getName().replace(" clientes", "");
      System.out.printf("%s,%.2f,%.3f,%.3f,%.2f%n",
          clients,
          r.getThroughput(),
          r.getAverageLatencyMs(),
          r.getP99LatencyMs(),
          r.getErrorRate());
    }
  }

  private void setupData() throws Exception {
    System.out.println("\n[Setup] Preparando dados...");
    
    try (ClientLibrary client = new ClientLibrary(baseConfig.getHost(), baseConfig.getPort())) {
      String user = "scale_setup_" + System.currentTimeMillis();
      client.register(user, "password");
      client.login(user, "password");

      Random rand = new Random();
      for (int i = 0; i < 200; i++) {
        String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];
        client.addEvent(product, rand.nextInt(100) + 1, rand.nextDouble() * 1000);
      }
      client.newDay();
      System.out.println("[Setup] Dados preparados.");
    }
  }

  private BenchmarkResults runWithClients(int numClients) throws Exception {
    System.out.println("\n[Scalability] Testando com " + numClients + " cliente(s)...");

    BenchmarkResults results = new BenchmarkResults(numClients + " clientes");
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numClients);

    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < numClients; i++) {
      final int clientId = i;
      Thread t = new Thread(() -> {
        try {
          runClient(clientId, results, startLatch);
        } catch (Exception e) {
          // Ignore
        } finally {
          doneLatch.countDown();
        }
      });
      threads.add(t);
      t.start();
    }

    Thread.sleep(200);
    results.start();
    startLatch.countDown();

    doneLatch.await();
    results.stop();

    System.out.printf("  -> Throughput: %.2f ops/s, Latência média: %.3f ms%n",
        results.getThroughput(), results.getAverageLatencyMs());

    return results;
  }

  private void runClient(int clientId, BenchmarkResults results, CountDownLatch startLatch)
      throws Exception {

    try (ClientLibrary client = new ClientLibrary(baseConfig.getHost(), baseConfig.getPort())) {
      String user = "scale_" + clientId + "_" + System.currentTimeMillis();
      client.register(user, "pass");
      client.login(user, "pass");

      Random rand = new Random(clientId);

      // Warmup
      for (int i = 0; i < baseConfig.getWarmupOperations(); i++) {
        doMixedOperation(client, rand);
      }

      startLatch.await();

      // Benchmark
      for (int i = 0; i < baseConfig.getOperationsPerClient(); i++) {
        long start = System.nanoTime();
        try {
          doMixedOperation(client, rand);
          long elapsed = System.nanoTime() - start;
          results.recordLatency(elapsed);
        } catch (Exception e) {
          results.recordError();
        }
      }
    }
  }

  private void doMixedOperation(ClientLibrary client, Random rand) throws Exception {
    String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];

    if (rand.nextBoolean()) {
      client.addEvent(product, rand.nextInt(100) + 1, rand.nextDouble() * 1000);
    } else {
      int op = rand.nextInt(4);
      switch (op) {
        case 0 -> client.getQuantity(product, 1);
        case 1 -> client.getVolume(product, 1);
        case 2 -> client.getAveragePrice(product, 1);
        case 3 -> client.getMaxPrice(product, 1);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    BenchmarkConfig config = new BenchmarkConfig()
        .operationsPerClient(500)
        .warmupOperations(50);

    new ScalabilityBenchmark(config).run();
  }
}

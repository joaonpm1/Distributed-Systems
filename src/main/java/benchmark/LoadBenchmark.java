package benchmark;

import client.ClientLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Benchmark de carga com diferentes tipos de workload.
 * 
 * Testa três cenários:
 * - Write-heavy: 90% addEvent, 10% agregações
 * - Read-heavy: 10% addEvent, 90% agregações
 * - Mixed: 50% writes, 50% reads
 */
public class LoadBenchmark {

  private static final String[] PRODUCTS = {
      "Laptop", "Mouse", "Teclado", "Monitor", "Webcam",
      "Headset", "SSD", "RAM", "GPU", "CPU"
  };

  private final BenchmarkConfig config;

  public LoadBenchmark(BenchmarkConfig config) {
    this.config = config;
  }

  public void runAll() throws Exception {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("           LOAD BENCHMARK - Diferentes tipos de carga");
    System.out.println("=".repeat(70));
    System.out.println(config);

    // Setup inicial: criar dados para leitura
    setupData();

    // Executar os três cenários
    BenchmarkResults writeHeavy = runWorkload("Write-Heavy (90% writes)", 0.9);
    BenchmarkResults readHeavy = runWorkload("Read-Heavy (10% writes)", 0.1);
    BenchmarkResults mixed = runWorkload("Mixed (50% writes)", 0.5);

    // Sumário
    System.out.println("\n" + "=".repeat(70));
    System.out.println("                         SUMÁRIO");
    System.out.println("=".repeat(70));
    System.out.println(BenchmarkResults.csvHeader());
    System.out.println(writeHeavy.toCSV());
    System.out.println(readHeavy.toCSV());
    System.out.println(mixed.toCSV());
  }

  private void setupData() throws Exception {
    System.out.println("\n[Setup] Preparando dados iniciais...");
    
    try (ClientLibrary client = new ClientLibrary(config.getHost(), config.getPort())) {
      // Registar e autenticar
      String user = "bench_" + System.currentTimeMillis();
      client.register(user, "password");
      client.login(user, "password");

      // Adicionar eventos iniciais
      Random rand = new Random();
      for (int i = 0; i < 100; i++) {
        String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];
        client.addEvent(product, rand.nextInt(100) + 1, rand.nextDouble() * 1000);
      }

      // Avançar dia para permitir agregações
      client.newDay();

      System.out.println("[Setup] Dados preparados. Dia avançado.");
    }
  }

  private BenchmarkResults runWorkload(String name, double writeRatio) throws Exception {
    System.out.println("\n[" + name + "] Iniciando...");
    
    BenchmarkResults results = new BenchmarkResults(name);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(config.getNumClients());

    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < config.getNumClients(); i++) {
      final int clientId = i;
      Thread t = new Thread(() -> {
        try {
          runClientWorkload(clientId, writeRatio, results, startLatch);
        } catch (Exception e) {
          System.err.println("Cliente " + clientId + " erro: " + e.getMessage());
        } finally {
          doneLatch.countDown();
        }
      });
      threads.add(t);
      t.start();
    }

    // Warmup
    System.out.println("[" + name + "] Warmup com " + config.getWarmupOperations() + " ops...");
    Thread.sleep(500);

    // Iniciar benchmark
    results.start();
    startLatch.countDown();

    // Aguardar conclusão
    doneLatch.await();
    results.stop();

    results.printReport();
    return results;
  }

  private void runClientWorkload(
      int clientId, double writeRatio, BenchmarkResults results, CountDownLatch startLatch)
      throws Exception {

    try (ClientLibrary client = new ClientLibrary(config.getHost(), config.getPort())) {
      // Autenticar
      String user = "client_" + clientId + "_" + System.currentTimeMillis();
      client.register(user, "pass");
      client.login(user, "pass");

      Random rand = new Random(clientId);

      // Warmup
      for (int i = 0; i < config.getWarmupOperations(); i++) {
        doOperation(client, rand, writeRatio);
      }

      startLatch.await();

      // Benchmark
      for (int i = 0; i < config.getOperationsPerClient(); i++) {
        long start = System.nanoTime();
        try {
          doOperation(client, rand, writeRatio);
          long elapsed = System.nanoTime() - start;
          results.recordLatency(elapsed);
        } catch (Exception e) {
          results.recordError();
        }
      }
    }
  }

  private void doOperation(ClientLibrary client, Random rand, double writeRatio) throws Exception {
    if (rand.nextDouble() < writeRatio) {
      // Write operation
      String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];
      client.addEvent(product, rand.nextInt(100) + 1, rand.nextDouble() * 1000);
    } else {
      // Read operation - escolher aleatoriamente
      String product = PRODUCTS[rand.nextInt(PRODUCTS.length)];
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
        .numClients(16)
        .operationsPerClient(1000)
        .warmupOperations(100);

    new LoadBenchmark(config).runAll();
  }
}

package benchmark;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Runner principal para todos os benchmarks.
 * 
 * Uso: java benchmark.BenchmarkRunner [opções]
 * 
 * Opções:
 *   --all         Executar todos os benchmarks
 *   --load        Executar apenas benchmark de carga
 *   --scale       Executar apenas benchmark de escalabilidade  
 *   --robust      Executar apenas benchmark de robustez
 *   --host HOST   Host do servidor (default: localhost)
 *   --port PORT   Porta do servidor (default: 8080)
 */
public class BenchmarkRunner {

  public static void main(String[] args) throws Exception {
    BenchmarkConfig config = parseArgs(args);
    
    boolean runLoad = false;
    boolean runScale = false;
    boolean runRobust = false;
    boolean interactive = true;

    for (String arg : args) {
      switch (arg) {
        case "--all" -> { runLoad = true; runScale = true; runRobust = true; interactive = false; }
        case "--load" -> { runLoad = true; interactive = false; }
        case "--scale" -> { runScale = true; interactive = false; }
        case "--robust" -> { runRobust = true; interactive = false; }
      }
    }

    printHeader();

    if (interactive) {
      runInteractive(config);
    } else {
      if (runLoad) new LoadBenchmark(config).runAll();
      if (runScale) new ScalabilityBenchmark(config).run();
      if (runRobust) new RobustnessBenchmark(config).run();
    }

    System.out.println("\nBenchmarks concluídos!");
  }

  private static void runInteractive(BenchmarkConfig config) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    
    while (true) {
      System.out.println("\n┌────────────────────────────────────────────┐");
      System.out.println("│            BENCHMARK MENU                  │");
      System.out.println("├────────────────────────────────────────────┤");
      System.out.println("│  1. Load Benchmark (diferentes workloads)  │");
      System.out.println("│  2. Scalability Benchmark (1-64 clientes)  │");
      System.out.println("│  3. Robustness Benchmark (slow consumers)  │");
      System.out.println("│  4. Executar TODOS                         │");
      System.out.println("│  5. Configurar                             │");
      System.out.println("│  0. Sair                                   │");
      System.out.println("└────────────────────────────────────────────┘");
      System.out.print("Escolha: ");

      String choice = reader.readLine();
      if (choice == null) break;

      try {
        switch (choice.trim()) {
          case "1" -> new LoadBenchmark(config).runAll();
          case "2" -> new ScalabilityBenchmark(config).run();
          case "3" -> new RobustnessBenchmark(config).run();
          case "4" -> {
            new LoadBenchmark(config).runAll();
            new ScalabilityBenchmark(config).run();
            new RobustnessBenchmark(config).run();
          }
          case "5" -> config = configure(reader, config);
          case "0", "q", "exit" -> { return; }
          default -> System.out.println("Opção inválida.");
        }
      } catch (Exception e) {
        System.out.println("\nErro: " + e.getMessage());
        System.out.println("Verifique se o servidor está a correr em " 
            + config.getHost() + ":" + config.getPort());
      }
    }
  }

  private static BenchmarkConfig configure(BufferedReader reader, BenchmarkConfig current) 
      throws Exception {
    System.out.println("\nConfiguração atual: " + current);
    System.out.println();

    System.out.print("Host [" + current.getHost() + "]: ");
    String host = reader.readLine().trim();
    if (host.isEmpty()) host = current.getHost();

    System.out.print("Port [" + current.getPort() + "]: ");
    String portStr = reader.readLine().trim();
    int port = portStr.isEmpty() ? current.getPort() : Integer.parseInt(portStr);

    System.out.print("Clientes [" + current.getNumClients() + "]: ");
    String clientsStr = reader.readLine().trim();
    int clients = clientsStr.isEmpty() ? current.getNumClients() : Integer.parseInt(clientsStr);

    System.out.print("Ops por cliente [" + current.getOperationsPerClient() + "]: ");
    String opsStr = reader.readLine().trim();
    int ops = opsStr.isEmpty() ? current.getOperationsPerClient() : Integer.parseInt(opsStr);

    BenchmarkConfig newConfig = new BenchmarkConfig()
        .host(host)
        .port(port)
        .numClients(clients)
        .operationsPerClient(ops);

    System.out.println("\nNova configuração: " + newConfig);
    return newConfig;
  }

  private static BenchmarkConfig parseArgs(String[] args) {
    BenchmarkConfig config = new BenchmarkConfig();
    
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--host" -> {
          if (i + 1 < args.length) config.host(args[++i]);
        }
        case "--port" -> {
          if (i + 1 < args.length) config.port(Integer.parseInt(args[++i]));
        }
        case "--clients" -> {
          if (i + 1 < args.length) config.numClients(Integer.parseInt(args[++i]));
        }
        case "--ops" -> {
          if (i + 1 < args.length) config.operationsPerClient(Integer.parseInt(args[++i]));
        }
      }
    }
    
    return config;
  }

  private static void printHeader() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║                                                                  ║");
    System.out.println("║     ████████ ██ ███    ███ ███████ ███████ ███████ ██████  ██    ║");
    System.out.println("║        ██    ██ ████  ████ ██      ██      ██      ██   ██ ██    ║");
    System.out.println("║        ██    ██ ██ ████ ██ █████   ███████ █████   ██████  ██    ║");
    System.out.println("║        ██    ██ ██  ██  ██ ██           ██ ██      ██   ██       ║");
    System.out.println("║        ██    ██ ██      ██ ███████ ███████ ███████ ██   ██ ██    ║");
    System.out.println("║                                                                  ║");
    System.out.println("║           PERFORMANCE BENCHMARK SUITE - SD 2025/26               ║");
    System.out.println("║                                                                  ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝");
  }
}

package client;

import common.model.Event;
import common.protocol.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClientUI {

  private ClientLibrary client;
  private BufferedReader reader;
  private boolean running;

  public ClientUI() {
    this.reader = new BufferedReader(new InputStreamReader(System.in));
    this.running = true;
  }

  public void start() {
    System.out.println("=== Cliente de Séries Temporais ===");
    System.out.println("Comandos: connect, register, login, add, newday,");
    System.out.println("          qty, vol, avg, max, filter,");
    System.out.println("          simul, consec, help, quit");
    System.out.println();

    while (running) {
      try {
        System.out.print("> ");
        String line = reader.readLine();
        if (line == null) break;

        line = line.trim();
        if (line.isEmpty()) continue;

        processCommand(line);

      } catch (IOException e) {
        System.err.println("Erro de I/O: " + e.getMessage());
      } catch (InterruptedException e) {
        System.err.println("Operação interrompida");
        Thread.currentThread().interrupt();
      }
    }

    if (client != null) {
      try {
        client.close();
      } catch (IOException ignored) {
      }
    }
  }

  private void processCommand(String line) throws IOException, InterruptedException {
    String[] parts = line.split("\\s+");
    String cmd = parts[0].toLowerCase();

    switch (cmd) {
      case "connect":
        handleConnect(parts);
        break;
      case "register":
        handleRegister(parts);
        break;
      case "login":
        handleLogin(parts);
        break;
      case "add":
        handleAdd(parts);
        break;
      case "newday":
        handleNewDay();
        break;
      case "qty":
      case "quantity":
        handleQuantity(parts);
        break;
      case "vol":
      case "volume":
        handleVolume(parts);
        break;
      case "avg":
      case "average":
        handleAverage(parts);
        break;
      case "max":
        handleMax(parts);
        break;
      case "filter":
        handleFilter(parts);
        break;
      case "simul":
      case "simultaneous":
        handleSimultaneous(parts);
        break;
      case "consec":
      case "consecutive":
        handleConsecutive(parts);
        break;
      case "help":
        showHelp();
        break;
      case "quit":
      case "exit":
        running = false;
        System.out.println("Adeus!");
        break;
      default:
        System.out.println("Comando desconhecido. Use 'help' para ver comandos.");
    }
  }

  private void handleConnect(String[] parts) throws IOException {
    String host = parts.length > 1 ? parts[1] : "localhost";
    int port = parts.length > 2 ? Integer.parseInt(parts[2]) : Protocol.DEFAULT_PORT;

    if (client != null) {
      client.close();
    }

    try {
      client = new ClientLibrary(host, port);
      System.out.println("Conectado a " + host + ":" + port);
    } catch (IOException e) {
      System.err.println("Erro ao conectar: " + e.getMessage());
      client = null;
    }
  }

  private void handleRegister(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: register <username> <password>");
      return;
    }

    boolean success = client.register(parts[1], parts[2]);
    System.out.println(success ? "Registo efetuado!" : "Utilizador já existe.");
  }

  private void handleLogin(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: login <username> <password>");
      return;
    }

    boolean success = client.login(parts[1], parts[2]);
    System.out.println(success ? "Login efetuado!" : "Credenciais inválidas.");
  }

  private void handleAdd(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 4) {
      System.out.println("Uso: add <produto> <quantidade> <preço>");
      return;
    }

    String product = parts[1];
    int quantity = Integer.parseInt(parts[2]);
    double price = Double.parseDouble(parts[3]);

    boolean success = client.addEvent(product, quantity, price);
    System.out.println(success ? "Evento adicionado!" : "Erro ao adicionar evento.");
  }

  private void handleNewDay() throws IOException, InterruptedException {
    if (!checkConnected()) return;

    int day = client.newDay();
    System.out.println(day > 0 ? "Novo dia: " + day : "Erro ao avançar dia.");
  }

  private void handleQuantity(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: qty <produto> <dias>");
      return;
    }

    int qty = client.getQuantity(parts[1], Integer.parseInt(parts[2]));
    System.out.println(qty >= 0 ? "Quantidade: " + qty : "Erro ou dados inválidos.");
  }

  private void handleVolume(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: vol <produto> <dias>");
      return;
    }

    double vol = client.getVolume(parts[1], Integer.parseInt(parts[2]));
    System.out.println(vol >= 0 ? "Volume: " + vol : "Erro ou dados inválidos.");
  }

  private void handleAverage(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: avg <produto> <dias>");
      return;
    }

    double avg = client.getAveragePrice(parts[1], Integer.parseInt(parts[2]));
    System.out.println(avg >= 0 ? "Preço médio: " + avg : "Erro ou dados inválidos.");
  }

  private void handleMax(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: max <produto> <dias>");
      return;
    }

    double max = client.getMaxPrice(parts[1], Integer.parseInt(parts[2]));
    System.out.println(max >= -1 ? "Preço máximo: " + max : "Erro ou dados inválidos.");
  }

  private void handleFilter(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: filter <diasAtrás> <produto1> [produto2] ...");
      return;
    }

    int daysAgo = Integer.parseInt(parts[1]);
    Set<String> products = new HashSet<>();
    for (int i = 2; i < parts.length; i++) {
      products.add(parts[i]);
    }

    List<Event> events = client.filterEvents(daysAgo, products);
    if (events != null) {
      System.out.println("Eventos encontrados: " + events.size());
      for (Event e : events) {
        System.out.println("  " + e);
      }
    } else {
      System.out.println("Erro ou dia inválido.");
    }
  }

  private void handleSimultaneous(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 3) {
      System.out.println("Uso: simul <produto1> <produto2>");
      System.out.println("  (BLOQUEANTE - espera até ambos serem vendidos ou dia terminar)");
      return;
    }

    String p1 = parts[1];
    String p2 = parts[2];

    System.out.println("A aguardar vendas de '" + p1 + "' e '" + p2 + "'...");
    System.out.println("(BLOQUEANTE - use outro terminal para adicionar eventos)");

    boolean result = client.waitForSimultaneous(p1, p2);

    if (result) {
      System.out.println("SUCESSO: Ambos os produtos foram vendidos!");
    } else {
      System.out.println("Dia terminou sem ambos os produtos serem vendidos.");
    }
  }

  private void handleConsecutive(String[] parts) throws IOException, InterruptedException {
    if (!checkConnected()) return;
    if (parts.length < 2) {
      System.out.println("Uso: consec <n>");
      System.out.println("  (BLOQUEANTE - espera até N vendas consecutivas ou dia terminar)");
      return;
    }

    int n = Integer.parseInt(parts[1]);

    System.out.println("A aguardar " + n + " vendas consecutivas do mesmo produto...");
    System.out.println("(BLOQUEANTE - use outro terminal para adicionar eventos)");

    String product = client.waitForConsecutive(n);

    if (product != null) {
      System.out.println("SUCESSO: " + n + " vendas consecutivas de '" + product + "'!");
    } else {
      System.out.println("Dia terminou sem " + n + " vendas consecutivas.");
    }
  }

  private void showHelp() {
    System.out.println("Comandos disponíveis:");
    System.out.println("  connect [host] [port]    - Conectar ao servidor");
    System.out.println("  register <user> <pass>   - Registar utilizador");
    System.out.println("  login <user> <pass>      - Autenticar");
    System.out.println("  add <prod> <qty> <price> - Adicionar evento de venda");
    System.out.println("  newday                   - Avançar para novo dia");
    System.out.println();
    System.out.println("  Agregações (dias anteriores):");
    System.out.println("  qty <prod> <d>           - Quantidade vendida nos últimos d dias");
    System.out.println("  vol <prod> <d>           - Volume de vendas nos últimos d dias");
    System.out.println("  avg <prod> <d>           - Preço médio nos últimos d dias");
    System.out.println("  max <prod> <d>           - Preço máximo nos últimos d dias");
    System.out.println("  filter <d> <p1> [p2]     - Eventos do dia d com produtos p1, p2...");
    System.out.println();
    System.out.println("  Notificações (dia corrente, BLOQUEANTES):");
    System.out.println("  simul <p1> <p2>          - Espera até p1 e p2 serem vendidos");
    System.out.println("  consec <n>               - Espera até N vendas consecutivas");
    System.out.println();
    System.out.println("  help                     - Mostrar esta ajuda");
    System.out.println("  quit                     - Sair");
  }

  private boolean checkConnected() {
    if (client == null) {
      System.out.println("Não conectado. Use 'connect' primeiro.");
      return false;
    }
    return true;
  }

  public static void main(String[] args) {
    ClientUI ui = new ClientUI();
    ui.start();
  }
}

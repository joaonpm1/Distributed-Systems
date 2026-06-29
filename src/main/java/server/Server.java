package server;

import common.Logger;
import common.protocol.Protocol;
import common.protocol.TaggedConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import server.data.TimeSeriesDB;
import server.data.UserManager;
import server.persistence.PersistenceManager;

public class Server {

  private int port = Protocol.DEFAULT_PORT;
  private int D = 30;
  private int S = 10;
  private String dataPath = "data";
  private boolean recover = false;

  private ServerSocket serverSocket;
  private boolean running;

  private UserManager userManager;
  private TimeSeriesDB database;

  public static void main(String[] args) {
    new Server().parseArgs(args).start();
  }

  public Server parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-p":
        case "--port":
          if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
          break;
        case "-D":
        case "--days":
          if (i + 1 < args.length) D = Integer.parseInt(args[++i]);
          break;
        case "-S":
        case "--memory":
          if (i + 1 < args.length) S = Integer.parseInt(args[++i]);
          break;
        case "-d":
        case "--data":
          if (i + 1 < args.length) dataPath = args[++i];
          break;
        case "-r":
        case "--recover":
          recover = true;
          break;
        case "-h":
        case "--help":
          printHelp();
          System.exit(0);
          break;
      }
    }
    return this;
  }

  private void printHelp() {
    System.out.println("Uso: java server.Server [opções]");
    System.out.println();
    System.out.println("Opções:");
    System.out.println(
        "  -p, --port <port>     Porta TCP (default: " + Protocol.DEFAULT_PORT + ")");
    System.out.println("  -D, --days <n>        Dias de histórico (default: 30)");
    System.out.println("  -S, --memory <n>      Séries em memória (default: 10)");
    System.out.println("  -d, --data <path>     Diretório de dados (default: data)");
    System.out.println("  -r, --recover         Recuperar estado do disco");
    System.out.println("  -h, --help            Mostrar esta ajuda");
  }

  public void start() {
    running = true;

    try {

      PersistenceManager persistence = null;
      if (dataPath != null && !dataPath.isEmpty()) {
        persistence = new PersistenceManager(dataPath);
        Logger.log("Server", "Persistência ativa em: " + dataPath);
      } else {
        Logger.log("Server", "Persistência desativada");
      }

      userManager = new UserManager(persistence);
      database = new TimeSeriesDB(D, S, persistence, recover);

      serverSocket = new ServerSocket();
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new java.net.InetSocketAddress(port));
      
      Logger.log("Server", "Servidor iniciado na porta " + port);
      Logger.log("Server", "Dias de histórico (D): " + D);
      Logger.log("Server", "Limite memória (S): " + S);

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    Logger.log("Server", "\nA guardar estado antes de terminar...");
                    try {
                      if (database != null) database.saveState();
                      Logger.log("Server", "Estado guardado em disco");
                    } catch (IOException e) {
                      Logger.error("Server", "Erro ao guardar estado: " + e.getMessage());
                    }
                  }));

      while (running) {
        try {

          Socket socket = serverSocket.accept();
          Logger.log("Server", "Nova conexão: " + socket.getRemoteSocketAddress());

          TaggedConnection connection = new TaggedConnection(socket);

          ClientHandler handler = new ClientHandler(connection, userManager, database);

          Thread clientThread = new Thread(handler);
          clientThread.start();

        } catch (IOException e) {
          if (running) {
            Logger.error("Server", "Erro ao aceitar conexão: " + e.getMessage());
          }
        }
      }

    } catch (Exception e) {
      if (running) {
        Logger.error("Server", "Erro ao iniciar servidor: " + e.getMessage());
        e.printStackTrace();
      }
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void stop() {
    running = false;
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

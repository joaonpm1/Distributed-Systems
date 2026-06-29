package server;

import common.Logger;
import common.model.Event;
import common.model.User;
import common.protocol.Protocol;
import common.protocol.TaggedConnection;
import common.protocol.TaggedConnection.Frame;
import common.serialization.Serializer;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import server.data.TimeSeriesDB;
import server.data.UserManager;

public class ClientHandler implements Runnable {

  private static final int WORKERS_PER_CONNECTION = 3;

  private final TaggedConnection connection;
  private final UserManager userManager;
  private final TimeSeriesDB database;

  private volatile User authenticatedUser;

  public ClientHandler(
      TaggedConnection connection, UserManager userManager, TimeSeriesDB database) {
    this.connection = connection;
    this.userManager = userManager;
    this.database = database;
    this.authenticatedUser = null;
  }

  @Override
  public void run() {
    try (connection) {
      Thread[] workers = new Thread[WORKERS_PER_CONNECTION];

      for (int i = 0; i < WORKERS_PER_CONNECTION; i++) {
        workers[i] = new Thread(this::workerLoop);
        workers[i].start();
      }

      for (Thread worker : workers) {
        try {
          worker.join();
        } catch (InterruptedException e) {

          worker.interrupt();
        }
      }

    } catch (Exception e) {

      Logger.error("ClientHandler", "Erro na conexão: " + e.getMessage());
    } finally {
      try {
        connection.close();
      } catch (IOException ignored) {
      }
      Logger.log(
          "ClientHandler",
          "Cliente desconectado: "
              + (authenticatedUser != null ? authenticatedUser.getUsername() : "não autenticado")
              + " ("
              + connection.getSocket().getRemoteSocketAddress()
              + ")");
    }
  }

  private void workerLoop() {
    try {
      while (true) {

        Frame frame = connection.receive();
        processFrame(frame);
      }
    } catch (EOFException e) {

    } catch (IOException e) {

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processFrame(Frame frame) throws IOException {
    int tag = frame.tag;
    byte[] data = frame.data;

    DataInputStream in = Serializer.createInput(data);
    int msgType = in.readByte();

    if (msgType == Protocol.MSG_REGISTER) {
      handleRegister(tag, in);
      return;
    }

    if (msgType == Protocol.MSG_LOGIN) {
      handleLogin(tag, in);
      return;
    }

    if (authenticatedUser == null) {
      sendError(tag, Protocol.ERROR_NOT_AUTH, "Não autenticado");
      return;
    }

    switch (msgType) {
      case Protocol.MSG_ADD_EVENT:
        handleAddEvent(tag, in);
        break;
      case Protocol.MSG_NEW_DAY:
        handleNewDay(tag);
        break;
      case Protocol.MSG_QUANTITY:
        handleQuantity(tag, in);
        break;
      case Protocol.MSG_VOLUME:
        handleVolume(tag, in);
        break;
      case Protocol.MSG_AVG_PRICE:
        handleAveragePrice(tag, in);
        break;
      case Protocol.MSG_MAX_PRICE:
        handleMaxPrice(tag, in);
        break;
      case Protocol.MSG_FILTER_EVENTS:
        handleFilterEvents(tag, in);
        break;
      case Protocol.MSG_SIMULTANEOUS:
        handleSimultaneous(tag, in);
        break;
      case Protocol.MSG_CONSECUTIVE:
        handleConsecutive(tag, in);
        break;
      default:
        sendError(tag, Protocol.ERROR, "Tipo de mensagem desconhecido: " + msgType);
    }
  }

  private void handleRegister(int tag, DataInputStream in) throws IOException {
    String username = in.readUTF();
    String password = in.readUTF();

    boolean success = userManager.register(username, password);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (success) {
      out.writeInt(Protocol.OK);
      out.writeUTF("Registo efetuado com sucesso");
    } else {
      out.writeInt(Protocol.ERROR_USER_EXISTS);
      out.writeUTF("Utilizador já existe");
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleLogin(int tag, DataInputStream in) throws IOException {
    String username = in.readUTF();
    String password = in.readUTF();

    User user = userManager.authenticate(username, password);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (user != null) {
      authenticatedUser = user;
      out.writeInt(Protocol.OK);
      out.writeUTF("Login efetuado com sucesso");
      Logger.log("ClientHandler", "Utilizador autenticado: " + username);
    } else {
      out.writeInt(Protocol.ERROR_AUTH);
      out.writeUTF("Credenciais inválidas");
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleAddEvent(int tag, DataInputStream in) throws IOException {
    String product = in.readUTF();
    int quantity = in.readInt();
    double price = in.readDouble();

    boolean success = database.addEvent(product, quantity, price);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (success) {
      out.writeInt(Protocol.OK);
    } else {
      out.writeInt(Protocol.ERROR);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleNewDay(int tag) throws IOException {
    database.newDay();
    int newDayNumber = database.getCurrentDayNumber();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeInt(Protocol.OK);
    out.writeInt(newDayNumber);
    out.flush();
    connection.send(tag, baos.toByteArray());

    Logger.log("ClientHandler", "Novo dia iniciado: " + newDayNumber);
  }

  private void handleQuantity(int tag, DataInputStream in) throws IOException {
    String product = in.readUTF();
    int d = in.readInt();

    int quantity = database.getQuantity(product, d);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (quantity >= 0) {
      out.writeInt(Protocol.OK);
      out.writeInt(quantity);
    } else {
      out.writeInt(Protocol.ERROR_INVALID_DAY);
      out.writeInt(0);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleVolume(int tag, DataInputStream in) throws IOException {
    String product = in.readUTF();
    int d = in.readInt();

    double volume = database.getVolume(product, d);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (volume >= 0) {
      out.writeInt(Protocol.OK);
      out.writeDouble(volume);
    } else {
      out.writeInt(Protocol.ERROR_INVALID_DAY);
      out.writeDouble(0);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleAveragePrice(int tag, DataInputStream in) throws IOException {
    String product = in.readUTF();
    int d = in.readInt();

    double avg = database.getAveragePrice(product, d);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (avg >= 0) {
      out.writeInt(Protocol.OK);
      out.writeDouble(avg);
    } else {
      out.writeInt(Protocol.ERROR_INVALID_DAY);
      out.writeDouble(0);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleMaxPrice(int tag, DataInputStream in) throws IOException {
    String product = in.readUTF();
    int d = in.readInt();

    double max = database.getMaxPrice(product, d);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (max >= -1) {
      out.writeInt(Protocol.OK);
      out.writeDouble(max);
    } else {
      out.writeInt(Protocol.ERROR_INVALID_DAY);
      out.writeDouble(0);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleFilterEvents(int tag, DataInputStream in) throws IOException {
    int daysAgo = in.readInt();
    Set<String> products = Serializer.readStringSet(in);

    List<Event> events = database.filterEvents(daysAgo, products);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    if (events != null) {
      out.writeInt(Protocol.OK);

      serializeEventsCompact(out, events);
    } else {
      out.writeInt(Protocol.ERROR_INVALID_DAY);
      out.writeInt(0);
    }
    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleSimultaneous(int tag, DataInputStream in) throws IOException {
    String p1 = in.readUTF();
    String p2 = in.readUTF();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    try {

      boolean result = database.waitForSimultaneous(p1, p2);

      out.writeInt(Protocol.OK);
      out.writeBoolean(result);

    } catch (InterruptedException e) {

      out.writeInt(Protocol.ERROR);
      out.writeBoolean(false);
    }

    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void handleConsecutive(int tag, DataInputStream in) throws IOException {
    int n = in.readInt();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);

    try {

      String product = database.waitForConsecutive(n);

      out.writeInt(Protocol.OK);
      if (product != null) {
        out.writeBoolean(true);
        out.writeUTF(product);
      } else {
        out.writeBoolean(false);
      }

    } catch (InterruptedException e) {

      out.writeInt(Protocol.ERROR);
      out.writeBoolean(false);
    }

    out.flush();
    connection.send(tag, baos.toByteArray());
  }

  private void serializeEventsCompact(DataOutputStream out, List<Event> events) throws IOException {

    java.util.Map<String, Integer> productIndex = new java.util.HashMap<>();
    java.util.List<String> productList = new java.util.ArrayList<>();

    for (Event e : events) {
      if (!productIndex.containsKey(e.getProduct())) {
        productIndex.put(e.getProduct(), productList.size());
        productList.add(e.getProduct());
      }
    }

    out.writeInt(productList.size());
    for (String p : productList) {
      out.writeUTF(p);
    }

    out.writeInt(events.size());
    for (Event e : events) {
      int idx = productIndex.get(e.getProduct());
      out.writeInt(idx);
      out.writeInt(e.getQuantity());
      out.writeDouble(e.getPrice());
    }
  }

  private void sendError(int tag, int errorCode, String message) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeInt(errorCode);
    out.writeUTF(message);
    out.flush();
    connection.send(tag, baos.toByteArray());
  }
}

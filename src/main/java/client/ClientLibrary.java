package client;

import common.model.Event;
import common.protocol.Protocol;
import common.protocol.TaggedConnection;
import common.serialization.Serializer;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClientLibrary implements AutoCloseable {

  private final Demultiplexer demux;

  private int tagGenerator = 1;
  private final Lock tagLock = new ReentrantLock();

  private boolean authenticated = false;

  public ClientLibrary(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    TaggedConnection connection = new TaggedConnection(socket);
    this.demux = new Demultiplexer(connection);
    this.demux.start();
  }

  public ClientLibrary(String host) throws IOException {
    this(host, Protocol.DEFAULT_PORT);
  }

  private int newTag() {
    tagLock.lock();
    try {
      return tagGenerator++;
    } finally {
      tagLock.unlock();
    }
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public boolean register(String username, String password)
      throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_REGISTER);
    out.writeUTF(username);
    out.writeUTF(password);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    return code == Protocol.OK;
  }

  public boolean login(String username, String password) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_LOGIN);
    out.writeUTF(username);
    out.writeUTF(password);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      authenticated = true;
      return true;
    }
    return false;
  }

  public boolean addEvent(String product, int quantity, double price)
      throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_ADD_EVENT);
    out.writeUTF(product);
    out.writeInt(quantity);
    out.writeDouble(price);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    return code == Protocol.OK;
  }

  public int newDay() throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_NEW_DAY);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readInt();
    }
    return -1;
  }

  public int getQuantity(String product, int d) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_QUANTITY);
    out.writeUTF(product);
    out.writeInt(d);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readInt();
    }
    return -1;
  }

  public double getVolume(String product, int d) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_VOLUME);
    out.writeUTF(product);
    out.writeInt(d);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readDouble();
    }
    return -1;
  }

  public double getAveragePrice(String product, int d) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_AVG_PRICE);
    out.writeUTF(product);
    out.writeInt(d);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readDouble();
    }
    return -1;
  }

  public double getMaxPrice(String product, int d) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_MAX_PRICE);
    out.writeUTF(product);
    out.writeInt(d);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readDouble();
    }
    return -1;
  }

  public List<Event> filterEvents(int daysAgo, Set<String> products)
      throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_FILTER_EVENTS);
    out.writeInt(daysAgo);
    Serializer.writeStringSet(out, products);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return deserializeEventsCompact(in);
    }
    return null;
  }

  private List<Event> deserializeEventsCompact(DataInputStream in) throws IOException {
    int nProducts = in.readInt();
    String[] productList = new String[nProducts];
    for (int i = 0; i < nProducts; i++) {
      productList[i] = in.readUTF();
    }

    int nEvents = in.readInt();
    List<Event> events = new ArrayList<>(nEvents);
    for (int i = 0; i < nEvents; i++) {
      int idx = in.readInt();
      int quantity = in.readInt();
      double price = in.readDouble();
      events.add(new Event(productList[idx], quantity, price));
    }

    return events;
  }

  public boolean waitForSimultaneous(String p1, String p2)
      throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_SIMULTANEOUS);
    out.writeUTF(p1);
    out.writeUTF(p2);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      return in.readBoolean();
    }
    return false;
  }

  public String waitForConsecutive(int n) throws IOException, InterruptedException {
    int tag = newTag();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream out = Serializer.createOutput(baos);
    out.writeByte(Protocol.MSG_CONSECUTIVE);
    out.writeInt(n);
    out.flush();

    demux.send(tag, baos.toByteArray());

    byte[] response = demux.receive(tag);
    DataInputStream in = Serializer.createInput(response);
    int code = in.readInt();

    if (code == Protocol.OK) {
      boolean hasProduct = in.readBoolean();
      if (hasProduct) {
        return in.readUTF();
      }
    }
    return null;
  }

  @Override
  public void close() throws IOException {
    demux.close();
  }
}

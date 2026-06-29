package common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable {

  public static class Frame {
    public final int tag;
    public final byte[] data;

    public Frame(int tag, byte[] data) {
      this.tag = tag;
      this.data = data;
    }
  }

  private final Socket socket;
  private final DataInputStream in;
  private final DataOutputStream out;

  private final Lock readLock = new ReentrantLock();
  private final Lock writeLock = new ReentrantLock();

  public TaggedConnection(Socket socket) throws IOException {
    this.socket = socket;
    this.in = new DataInputStream(socket.getInputStream());
    this.out = new DataOutputStream(socket.getOutputStream());
  }

  public Socket getSocket() {
    return socket;
  }

  public void send(Frame frame) throws IOException {
    send(frame.tag, frame.data);
  }

  public void send(int tag, byte[] data) throws IOException {
    writeLock.lock();
    try {

      out.writeInt(4 + data.length);
      out.writeInt(tag);
      out.write(data);
      out.flush();
    } finally {
      writeLock.unlock();
    }
  }

  public Frame receive() throws IOException {
    readLock.lock();
    try {
      int length = in.readInt();
      int tag = in.readInt();
      byte[] data = new byte[length - 4];
      in.readFully(data);
      return new Frame(tag, data);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}

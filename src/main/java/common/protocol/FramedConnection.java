package common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FramedConnection implements AutoCloseable {

  private final Socket socket;
  private final DataInputStream in;
  private final DataOutputStream out;

  private final Lock readLock = new ReentrantLock();
  private final Lock writeLock = new ReentrantLock();

  public FramedConnection(Socket socket) throws IOException {
    this.socket = socket;
    this.in = new DataInputStream(socket.getInputStream());
    this.out = new DataOutputStream(socket.getOutputStream());
  }

  public void send(byte[] data) throws IOException {
    writeLock.lock();
    try {
      out.writeInt(data.length);
      out.write(data);
      out.flush();
    } finally {
      writeLock.unlock();
    }
  }

  public byte[] receive() throws IOException {
    readLock.lock();
    try {
      int length = in.readInt();
      byte[] data = new byte[length];
      in.readFully(data);
      return data;
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}

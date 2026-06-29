package client;

import common.protocol.TaggedConnection;
import common.protocol.TaggedConnection.Frame;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable {

  private final TaggedConnection connection;

  private final Map<Integer, TagEntry> tagEntries = new HashMap<>();

  private final Lock lock = new ReentrantLock();

  private Thread readerThread;
  private volatile boolean running;

  private IOException readerException;

  private static class TagEntry {
    final Condition condition;
    final Queue<byte[]> queue = new ArrayDeque<>();

    TagEntry(Condition condition) {
      this.condition = condition;
    }
  }

  public Demultiplexer(TaggedConnection connection) {
    this.connection = connection;
    this.running = false;
    this.readerException = null;
  }

  public void start() {
    running = true;
    readerThread = new Thread(this::readerLoop);
    readerThread.setDaemon(true);
    readerThread.start();
  }

  private void readerLoop() {
    try {
      while (running) {

        Frame frame = connection.receive();

        lock.lock();
        try {

          TagEntry entry = tagEntries.get(frame.tag);
          if (entry == null) {
            entry = new TagEntry(lock.newCondition());
            tagEntries.put(frame.tag, entry);
          }

          entry.queue.add(frame.data);

          entry.condition.signal();

        } finally {
          lock.unlock();
        }
      }
    } catch (IOException e) {

      lock.lock();
      try {
        readerException = e;
        running = false;

        for (TagEntry entry : tagEntries.values()) {
          entry.condition.signalAll();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  public void send(Frame frame) throws IOException {
    connection.send(frame);
  }

  public void send(int tag, byte[] data) throws IOException {
    connection.send(tag, data);
  }

  public byte[] receive(int tag) throws IOException, InterruptedException {
    lock.lock();
    try {

      TagEntry entry = tagEntries.get(tag);
      if (entry == null) {
        entry = new TagEntry(lock.newCondition());
        tagEntries.put(tag, entry);
      }

      while (entry.queue.isEmpty() && running && readerException == null) {
        entry.condition.await();
      }

      if (readerException != null) {
        throw readerException;
      }

      if (!running && entry.queue.isEmpty()) {
        throw new IOException("Conexão fechada");
      }

      return entry.queue.poll();

    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    running = false;

    if (readerThread != null) {
      readerThread.interrupt();
    }

    connection.close();

    lock.lock();
    try {
      for (TagEntry entry : tagEntries.values()) {
        entry.condition.signalAll();
      }
    } finally {
      lock.unlock();
    }
  }
}

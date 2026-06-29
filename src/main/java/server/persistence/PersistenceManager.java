package server.persistence;

import common.model.Event;
import common.model.User;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import server.data.DaySeries;

public class PersistenceManager {

  private final File dataDir;
  private final File seriesDir;
  private final File usersFile;
  private final File stateFile;

  private final Lock lock = new ReentrantLock();

  public PersistenceManager(String dataPath) throws IOException {
    this.dataDir = new File(dataPath);
    this.seriesDir = new File(dataDir, "series");
    this.usersFile = new File(dataDir, "users.dat");
    this.stateFile = new File(dataDir, "state.dat");

    if (!seriesDir.exists()) {
      seriesDir.mkdirs();
    }
  }

  public PersistenceManager() throws IOException {
    this("data");
  }

  public void saveDaySeries(DaySeries day) throws IOException {
    lock.lock();
    try {
      File file = new File(seriesDir, "day_" + day.getDayNumber() + ".dat");

      try (DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {

        out.writeInt(day.getDayNumber());

        out.writeBoolean(day.isClosed());

        List<Event> events = day.getEvents();
        out.writeInt(events.size());
        for (Event e : events) {
          out.writeUTF(e.getProduct());
          out.writeInt(e.getQuantity());
          out.writeDouble(e.getPrice());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  public DaySeries loadDaySeries(int dayNumber) throws IOException {
    lock.lock();
    try {
      File file = new File(seriesDir, "day_" + dayNumber + ".dat");

      if (!file.exists()) {
        return null;
      }

      try (DataInputStream in =
          new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

        int readDayNumber = in.readInt();

        DaySeries day = new DaySeries(readDayNumber);

        boolean closed = in.readBoolean();

        int nEvents = in.readInt();
        for (int i = 0; i < nEvents; i++) {
          String product = in.readUTF();
          int quantity = in.readInt();
          double price = in.readDouble();
          day.addEvent(new Event(product, quantity, price));
        }

        if (closed) {
          day.close();
        }

        return day;
      }
    } finally {
      lock.unlock();
    }
  }

  public void streamEvents(int dayNumber, Consumer<Event> processor) throws IOException {
    lock.lock();
    try {
      File file = new File(seriesDir, "day_" + dayNumber + ".dat");

      if (!file.exists()) {
        return;
      }

      try (DataInputStream in =
          new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {

        in.readInt();
        in.readBoolean();

        int nEvents = in.readInt();

        for (int i = 0; i < nEvents; i++) {
          String product = in.readUTF();
          int quantity = in.readInt();
          double price = in.readDouble();

          processor.accept(new Event(product, quantity, price));
        }
      }
    } finally {
      lock.unlock();
    }
  }

  public void deleteDaySeries(int dayNumber) throws IOException {
    lock.lock();
    try {
      File file = new File(seriesDir, "day_" + dayNumber + ".dat");
      if (file.exists()) {
        file.delete();
      }
    } finally {
      lock.unlock();
    }
  }

  public boolean daySeriesExists(int dayNumber) {
    File file = new File(seriesDir, "day_" + dayNumber + ".dat");
    return file.exists();
  }

  public List<Integer> listSavedDays() throws IOException {
    lock.lock();
    try {
      List<Integer> days = new ArrayList<>();

      File[] files =
          seriesDir.listFiles((dir, name) -> name.startsWith("day_") && name.endsWith(".dat"));

      if (files != null) {
        for (File file : files) {
          String name = file.getName();

          String numStr = name.substring(4, name.length() - 4);
          try {
            days.add(Integer.parseInt(numStr));
          } catch (NumberFormatException ignored) {
          }
        }
      }

      Collections.sort(days);
      return days;
    } finally {
      lock.unlock();
    }
  }

  public void saveUsers(Map<String, User> users) throws IOException {
    lock.lock();
    try {
      try (DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(usersFile)))) {

        out.writeInt(users.size());
        for (User user : users.values()) {
          out.writeUTF(user.getUsername());
          out.writeUTF(user.getPassword());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  public Map<String, User> loadUsers() throws IOException {
    lock.lock();
    try {
      Map<String, User> users = new HashMap<>();

      if (!usersFile.exists()) {
        return users;
      }

      try (DataInputStream in =
          new DataInputStream(new BufferedInputStream(new FileInputStream(usersFile)))) {

        int nUsers = in.readInt();
        for (int i = 0; i < nUsers; i++) {
          String username = in.readUTF();
          String password = in.readUTF();
          users.put(username, new User(username, password));
        }
      }

      return users;
    } finally {
      lock.unlock();
    }
  }

  public static class ServerState {
    public int currentDayNumber;
    public int D;
    public int S;

    public ServerState() {
      this.currentDayNumber = 1;
      this.D = 7;
      this.S = 5;
    }

    public ServerState(int currentDayNumber, int D, int S) {
      this.currentDayNumber = currentDayNumber;
      this.D = D;
      this.S = S;
    }
  }

  public void saveState(ServerState state) throws IOException {
    lock.lock();
    try {
      try (DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)))) {

        out.writeInt(state.currentDayNumber);
        out.writeInt(state.D);
        out.writeInt(state.S);
      }
    } finally {
      lock.unlock();
    }
  }

  public ServerState loadState() throws IOException {
    lock.lock();
    try {
      if (!stateFile.exists()) {
        return new ServerState();
      }

      try (DataInputStream in =
          new DataInputStream(new BufferedInputStream(new FileInputStream(stateFile)))) {

        int currentDayNumber = in.readInt();
        int D = in.readInt();
        int S = in.readInt();

        return new ServerState(currentDayNumber, D, S);
      }
    } finally {
      lock.unlock();
    }
  }

  public void clearAll() throws IOException {
    lock.lock();
    try {

      File[] files = seriesDir.listFiles();
      if (files != null) {
        for (File file : files) {
          file.delete();
        }
      }

      if (usersFile.exists()) usersFile.delete();
      if (stateFile.exists()) stateFile.delete();
    } finally {
      lock.unlock();
    }
  }
}

package server.data;

import common.Logger;
import common.model.User;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.persistence.PersistenceManager;

public class UserManager {

  private final Map<String, User> users;

  private final PersistenceManager persistence;

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Lock readLock = rwLock.readLock();
  private final Lock writeLock = rwLock.writeLock();

  public UserManager(PersistenceManager persistence) {
    this.persistence = persistence;
    this.users = new HashMap<>();
  }

  public UserManager(PersistenceManager persistence, boolean recover) throws IOException {
    this.persistence = persistence;
    if (recover && persistence != null) {
      this.users = new HashMap<>(persistence.loadUsers());
      Logger.log("UserManager", "Recuperados " + users.size() + " utilizadores do disco");
    } else {
      this.users = new HashMap<>();
    }
  }

  public boolean register(String username, String password) throws IOException {
    writeLock.lock();
    try {

      if (users.containsKey(username)) {
        return false;
      }

      User user = new User(username, password);
      users.put(username, user);

      if (persistence != null) {
        persistence.saveUsers(new HashMap<>(users));
      }

      return true;
    } finally {
      writeLock.unlock();
    }
  }

  public User authenticate(String username, String password) {
    readLock.lock();
    try {
      User user = users.get(username);
      if (user == null) {
        return null;
      }

      if (user.checkPassword(password)) {
        return user;
      }
      return null;
    } finally {
      readLock.unlock();
    }
  }

  public boolean exists(String username) {
    readLock.lock();
    try {
      return users.containsKey(username);
    } finally {
      readLock.unlock();
    }
  }

  public int size() {
    readLock.lock();
    try {
      return users.size();
    } finally {
      readLock.unlock();
    }
  }

  public void save() throws IOException {
    if (persistence == null) return;

    readLock.lock();
    try {
      persistence.saveUsers(users);
    } finally {
      readLock.unlock();
    }
  }
}

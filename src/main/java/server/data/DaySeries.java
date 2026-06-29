package server.data;

import common.model.Event;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DaySeries {

  private final int dayNumber;
  private final List<Event> events;
  private final Set<String> productsSold;
  private boolean closed;

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Lock readLock = rwLock.readLock();
  private final Lock writeLock = rwLock.writeLock();

  public DaySeries(int dayNumber) {
    this.dayNumber = dayNumber;
    this.events = new ArrayList<>();
    this.productsSold = new HashSet<>();
    this.closed = false;
  }

  public int getDayNumber() {
    return dayNumber;
  }

  public boolean addEvent(Event event) {
    writeLock.lock();
    try {
      if (closed) {
        return false;
      }
      events.add(event);
      productsSold.add(event.getProduct());
      return true;
    } finally {
      writeLock.unlock();
    }
  }

  public void close() {
    writeLock.lock();
    try {
      closed = true;
    } finally {
      writeLock.unlock();
    }
  }

  public boolean isClosed() {
    readLock.lock();
    try {
      return closed;
    } finally {
      readLock.unlock();
    }
  }

  public int size() {
    readLock.lock();
    try {
      return events.size();
    } finally {
      readLock.unlock();
    }
  }

  public List<Event> getEvents() {
    readLock.lock();
    try {
      return new ArrayList<>(events);
    } finally {
      readLock.unlock();
    }
  }

  public List<Event> getEventsByProducts(Set<String> products) {
    readLock.lock();
    try {
      List<Event> filtered = new ArrayList<>();
      for (Event e : events) {
        if (products.contains(e.getProduct())) {
          filtered.add(e);
        }
      }
      return filtered;
    } finally {
      readLock.unlock();
    }
  }

  public boolean hasProduct(String product) {
    readLock.lock();
    try {
      return productsSold.contains(product);
    } finally {
      readLock.unlock();
    }
  }

  public Set<String> getProductsSold() {
    readLock.lock();
    try {
      return new HashSet<>(productsSold);
    } finally {
      readLock.unlock();
    }
  }

  public int getTotalQuantity(String product) {
    readLock.lock();
    try {
      int total = 0;
      for (Event e : events) {
        if (e.getProduct().equals(product)) {
          total += e.getQuantity();
        }
      }
      return total;
    } finally {
      readLock.unlock();
    }
  }

  public double getTotalVolume(String product) {
    readLock.lock();
    try {
      double total = 0;
      for (Event e : events) {
        if (e.getProduct().equals(product)) {
          total += e.getValue();
        }
      }
      return total;
    } finally {
      readLock.unlock();
    }
  }

  public double getMaxPrice(String product) {
    readLock.lock();
    try {
      double max = -1;
      for (Event e : events) {
        if (e.getProduct().equals(product)) {
          if (e.getPrice() > max) {
            max = e.getPrice();
          }
        }
      }
      return max;
    } finally {
      readLock.unlock();
    }
  }

  public int getEventCount(String product) {
    readLock.lock();
    try {
      int count = 0;
      for (Event e : events) {
        if (e.getProduct().equals(product)) {
          count++;
        }
      }
      return count;
    } finally {
      readLock.unlock();
    }
  }
}

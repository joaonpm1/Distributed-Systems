package server.data;

import common.model.Event;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CurrentDaySeries {

  private final int dayNumber;
  private final List<Event> events;
  private final Set<String> productsSold;
  private boolean closed;

  private String lastProduct;
  private int consecutiveCount;

  private final Lock lock = new ReentrantLock();

  private final Condition newEventCondition = lock.newCondition();

  private final Condition dayEndedCondition = lock.newCondition();

  private final Map<String, Condition> productConditions = new HashMap<>();

  public CurrentDaySeries(int dayNumber) {
    this.dayNumber = dayNumber;
    this.events = new ArrayList<>();
    this.productsSold = new HashSet<>();
    this.closed = false;
    this.lastProduct = null;
    this.consecutiveCount = 0;
  }

  public int getDayNumber() {
    return dayNumber;
  }

  public boolean addEvent(Event event) {
    lock.lock();
    try {
      if (closed) {
        return false;
      }

      events.add(event);
      String product = event.getProduct();
      productsSold.add(product);

      if (product.equals(lastProduct)) {
        consecutiveCount++;
      } else {
        lastProduct = product;
        consecutiveCount = 1;
      }

      Condition productCond = productConditions.get(product);
      if (productCond != null) {
        productCond.signalAll();
      }

      newEventCondition.signalAll();

      return true;
    } finally {
      lock.unlock();
    }
  }

  public void close() {
    lock.lock();
    try {
      closed = true;

      newEventCondition.signalAll();
      dayEndedCondition.signalAll();

      for (Condition c : productConditions.values()) {
        c.signalAll();
      }
    } finally {
      lock.unlock();
    }
  }

  public boolean isClosed() {
    lock.lock();
    try {
      return closed;
    } finally {
      lock.unlock();
    }
  }

  public int size() {
    lock.lock();
    try {
      return events.size();
    } finally {
      lock.unlock();
    }
  }

  public List<Event> getEvents() {
    lock.lock();
    try {
      return new ArrayList<>(events);
    } finally {
      lock.unlock();
    }
  }

  public boolean hasProduct(String product) {
    lock.lock();
    try {
      return productsSold.contains(product);
    } finally {
      lock.unlock();
    }
  }

  public Set<String> getProductsSold() {
    lock.lock();
    try {
      return new HashSet<>(productsSold);
    } finally {
      lock.unlock();
    }
  }

  public boolean waitForSimultaneous(String p1, String p2) throws InterruptedException {
    lock.lock();
    try {

      if (!productConditions.containsKey(p1)) {
        productConditions.put(p1, lock.newCondition());
      }
      if (!productConditions.containsKey(p2)) {
        productConditions.put(p2, lock.newCondition());
      }

      while (!closed && !(productsSold.contains(p1) && productsSold.contains(p2))) {

        if (!productsSold.contains(p1)) {
          productConditions.get(p1).await();
        } else {
          productConditions.get(p2).await();
        }
      }

      return productsSold.contains(p1) && productsSold.contains(p2);
    } finally {
      lock.unlock();
    }
  }

  public String waitForConsecutive(int n) throws InterruptedException {
    lock.lock();
    try {

      while (!closed && consecutiveCount < n) {
        newEventCondition.await();
      }

      if (consecutiveCount >= n) {
        return lastProduct;
      }
      return null;
    } finally {
      lock.unlock();
    }
  }

  public String getLastProduct() {
    lock.lock();
    try {
      return lastProduct;
    } finally {
      lock.unlock();
    }
  }

  public int getConsecutiveCount() {
    lock.lock();
    try {
      return consecutiveCount;
    } finally {
      lock.unlock();
    }
  }
}

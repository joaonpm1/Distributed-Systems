package server.data;

import common.Logger;
import common.model.Aggregation;
import common.model.Event;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.persistence.PersistenceManager;

public class TimeSeriesDB {

  private final int D;
  private final int S;

  private final PersistenceManager persistence;

  private CurrentDaySeries currentDay;
  private int currentDayNumber;

  private final Map<Integer, CacheEntry> memoryCache;
  private CacheEntry first;
  private CacheEntry last;

  private final Map<String, Aggregation> aggregationCache = new HashMap<>();

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Lock readLock = rwLock.readLock();

  private final Lock writeLock = rwLock.writeLock();

  private final Lock cacheLock = new ReentrantLock();

  private static class CacheEntry {
    int key;
    DaySeries value;
    CacheEntry prev;
    CacheEntry next;

    CacheEntry(int key, DaySeries value) {
      this.key = key;
      this.value = value;
    }
  }

  public TimeSeriesDB(int D, int S, PersistenceManager persistence, boolean recover)
      throws IOException {
    this.D = D;
    this.S = S;
    this.persistence = persistence;

    this.memoryCache = new HashMap<>();

    if (recover && persistence != null) {
      PersistenceManager.ServerState state = persistence.loadState();
      if (state != null) {
        this.currentDayNumber = state.currentDayNumber;
        Logger.log("DB", "Estado recuperado: Dia " + currentDayNumber);
      } else {
        this.currentDayNumber = 1;
      }
    } else {
      this.currentDayNumber = 1;
    }

    this.currentDay = new CurrentDaySeries(currentDayNumber);
  }

  public TimeSeriesDB(int D) {
    try {
      this.D = D;
      this.S = D;
      this.persistence = null;
      this.memoryCache = new HashMap<>();
      this.currentDayNumber = 1;
      this.currentDay = new CurrentDaySeries(currentDayNumber);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int getCurrentDayNumber() {
    readLock.lock();
    try {
      return currentDayNumber;
    } finally {
      readLock.unlock();
    }
  }

  private void addToStart(CacheEntry entry) {
    entry.next = first;
    entry.prev = null;
    if (first != null) {
      first.prev = entry;
    }
    first = entry;
    if (last == null) {
      last = first;
    }
  }

  private void removeEntry(CacheEntry entry) {
    if (entry.prev != null) {
      entry.prev.next = entry.next;
    } else {
      first = entry.next;
    }

    if (entry.next != null) {
      entry.next.prev = entry.prev;
    } else {
      last = entry.prev;
    }
  }

  private void moveToStart(CacheEntry entry) {
    removeEntry(entry);
    addToStart(entry);
  }

  private void putInCache(int key, DaySeries value) {
    CacheEntry entry = memoryCache.get(key);
    if (entry != null) {
      entry.value = value;
      moveToStart(entry);
    } else {
      CacheEntry newEntry = new CacheEntry(key, value);
      memoryCache.put(key, newEntry);
      addToStart(newEntry);
    }
  }

  private DaySeries getFromCache(int key) {
    CacheEntry entry = memoryCache.get(key);
    if (entry != null) {
      moveToStart(entry);
      return entry.value;
    }
    return null;
  }

  private void removeFromCache(int key) {
    CacheEntry entry = memoryCache.remove(key);
    if (entry != null) {
      removeEntry(entry);
    }
  }

  private void ensureMemoryLimit() throws IOException {
    if (persistence == null) return;

    while (true) {
      DaySeries dayToSave = null;
      int keyToRemove = -1;

      cacheLock.lock();
      try {
        if (memoryCache.size() < S || last == null) {
          return;
        }

        CacheEntry oldest = last;
        keyToRemove = oldest.key;
        dayToSave = oldest.value;

        removeFromCache(keyToRemove);
      } finally {
        cacheLock.unlock();
      }

      if (dayToSave != null) {
        persistence.saveDaySeries(dayToSave);
        Logger.log("DB", "Evicted day " + keyToRemove + " para disco (LRU manual)");
      }
    }
  }

  private DaySeries getOrLoadFromHistory(int dayNumber) throws IOException {

    cacheLock.lock();
    try {
      DaySeries day = getFromCache(dayNumber);
      if (day != null) {
        return day;
      }
    } finally {
      cacheLock.unlock();
    }

    if (persistence == null) return null;

    DaySeries day = persistence.loadDaySeries(dayNumber);

    if (day != null) {

      ensureMemoryLimit();

      cacheLock.lock();
      try {

        if (memoryCache.containsKey(dayNumber)) {

          return memoryCache.get(dayNumber).value;
        }
        putInCache(dayNumber, day);
      } finally {
        cacheLock.unlock();
      }
    }

    return day;
  }

  public void saveState() throws IOException {
    if (persistence == null) return;

    writeLock.lock();
    try {
      persistence.saveState(new PersistenceManager.ServerState(currentDayNumber, D, S));
    } finally {
      writeLock.unlock();
    }
  }

  public void newDay() throws IOException {
    writeLock.lock();
    try {
      currentDay.close();
      DaySeries closedDay = convertToDaySeries(currentDay);

      if (persistence != null) {
        ensureMemoryLimit();

        cacheLock.lock();
        try {
          putInCache(currentDayNumber, closedDay);
        } finally {
          cacheLock.unlock();
        }

        persistence.saveDaySeries(closedDay);

        int oldDay = currentDayNumber - D;
        if (oldDay > 0) {
          cacheLock.lock();
          try {
            removeFromCache(oldDay);
            cleanCacheForDay((oldDay));
          } finally {
            cacheLock.unlock();
          }
          persistence.deleteDaySeries(oldDay);
        }

        persistence.saveState(new PersistenceManager.ServerState(currentDayNumber + 1, D, S));
      } else {
        cacheLock.lock();
        try {
          putInCache(currentDayNumber, closedDay);
          int oldDay = currentDayNumber - D;
          if (oldDay > 0) {
            removeFromCache(oldDay);
            cleanCacheForDay(oldDay);
          }
        } finally {
          cacheLock.unlock();
        }
      }

      currentDayNumber++;
      currentDay = new CurrentDaySeries(currentDayNumber);

    } finally {
      writeLock.unlock();
    }
  }

  private DaySeries convertToDaySeries(CurrentDaySeries current) {
    DaySeries day = new DaySeries(current.getDayNumber());
    for (Event e : current.getEvents()) {
      day.addEvent(e);
    }
    day.close();
    return day;
  }

  public boolean addEvent(Event event) {

    if (event.getQuantity() <= 0 || event.getPrice() < 0) {
      Logger.error("DB", "Evento rejeitado (dados inválidos): " + event);
      return false;
    }

    readLock.lock();
    CurrentDaySeries day;
    try {
      day = currentDay;
    } finally {
      readLock.unlock();
    }
    return day.addEvent(event);
  }

  public boolean addEvent(String product, int quantity, double price) {
    return addEvent(new Event(product, quantity, price));
  }

  private Aggregation getAggregationForDay(String product, int dayNumber) throws IOException {
    String cacheKey = product + ":" + dayNumber;

    cacheLock.lock();
    try {
      Aggregation cached = aggregationCache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
    } finally {
      cacheLock.unlock();
    }

    DaySeries day = getOrLoadFromHistory(dayNumber);
    if (day == null) {
      return Aggregation.empty(product, dayNumber);
    }

    Aggregation agg =
        new Aggregation(
            product,
            dayNumber,
            day.getTotalQuantity(product),
            day.getTotalVolume(product),
            day.getMaxPrice(product),
            day.getEventCount(product));

    cacheLock.lock();
    try {
      aggregationCache.put(cacheKey, agg);
    } finally {
      cacheLock.unlock();
    }
    return agg;
  }

  private void cleanCacheForDay(int dayNumber) {
    String suffix = ":" + dayNumber;

    cacheLock.lock();
    try {
      aggregationCache.entrySet().removeIf(e -> e.getKey().endsWith(suffix));
    } finally {
      cacheLock.unlock();
    }
  }

  public int getQuantity(String product, int d) throws IOException {
    readLock.lock();
    try {
      if (d < 1 || d > D) return -1;

      int total = 0;
      for (int i = 1; i <= d; i++) {
        int dayNum = currentDayNumber - i;
        if (dayNum >= 1) {

          DaySeries day = getFromCache(dayNum);

          if (day != null) {
            Aggregation agg = getAggregationForDay(product, dayNum);
            total += agg.getTotalQuantity();
          } else if (persistence != null) {

            final int[] dayTotal = {0};
            persistence.streamEvents(
                dayNum,
                e -> {
                  if (e.getProduct().equals(product)) {
                    dayTotal[0] += e.getQuantity();
                  }
                });
            total += dayTotal[0];
          }
        }
      }
      return total;
    } finally {
      readLock.unlock();
    }
  }

  public double getVolume(String product, int d) throws IOException {
    readLock.lock();
    try {
      if (d < 1 || d > D) return -1;

      double total = 0;
      for (int i = 1; i <= d; i++) {
        int dayNum = currentDayNumber - i;
        if (dayNum >= 1) {

          DaySeries day = getFromCache(dayNum);

          if (day != null) {
            Aggregation agg = getAggregationForDay(product, dayNum);
            total += agg.getTotalVolume();
          } else if (persistence != null) {

            final double[] dayVol = {0};
            persistence.streamEvents(
                dayNum,
                e -> {
                  if (e.getProduct().equals(product)) {
                    dayVol[0] += (e.getQuantity() * e.getPrice());
                  }
                });
            total += dayVol[0];
          }
        }
      }
      return total;
    } finally {
      readLock.unlock();
    }
  }

  public double getAveragePrice(String product, int d) throws IOException {
    readLock.lock();
    try {
      if (d < 1 || d > D) return -1;

      double totalVolume = 0;
      int totalQuantity = 0;

      for (int i = 1; i <= d; i++) {
        int dayNum = currentDayNumber - i;
        if (dayNum >= 1) {
          DaySeries day = getFromCache(dayNum);

          if (day != null) {
            Aggregation agg = getAggregationForDay(product, dayNum);
            totalVolume += agg.getTotalVolume();
            totalQuantity += agg.getTotalQuantity();
          } else if (persistence != null) {

            final double[] dayVol = {0};
            final int[] dayQty = {0};
            persistence.streamEvents(
                dayNum,
                e -> {
                  if (e.getProduct().equals(product)) {
                    dayQty[0] += e.getQuantity();
                    dayVol[0] += (e.getQuantity() * e.getPrice());
                  }
                });
            totalVolume += dayVol[0];
            totalQuantity += dayQty[0];
          }
        }
      }

      return totalQuantity == 0 ? 0 : totalVolume / totalQuantity;
    } finally {
      readLock.unlock();
    }
  }

  public double getMaxPrice(String product, int d) throws IOException {
    readLock.lock();
    try {
      if (d < 1 || d > D) return -1;

      double max = -1;
      for (int i = 1; i <= d; i++) {
        int dayNum = currentDayNumber - i;
        if (dayNum >= 1) {
          DaySeries day = getFromCache(dayNum);

          if (day != null) {
            Aggregation agg = getAggregationForDay(product, dayNum);
            if (agg.getMaxPrice() > max) {
              max = agg.getMaxPrice();
            }
          } else if (persistence != null) {

            final double[] dayMax = {-1};
            persistence.streamEvents(
                dayNum,
                e -> {
                  if (e.getProduct().equals(product)) {
                    if (e.getPrice() > dayMax[0]) {
                      dayMax[0] = e.getPrice();
                    }
                  }
                });
            if (dayMax[0] > max) max = dayMax[0];
          }
        }
      }
      return max;
    } finally {
      readLock.unlock();
    }
  }

  public List<Event> filterEvents(int daysAgo, Set<String> products) throws IOException {
    readLock.lock();
    try {
      if (daysAgo < 1 || daysAgo > D) return null;

      int dayNum = currentDayNumber - daysAgo;
      if (dayNum < 1) return null;

      DaySeries day = getFromCache(dayNum);

      if (day != null) {
        return day.getEventsByProducts(products);
      }

      if (persistence == null) return null;

      boolean exists = persistence.daySeriesExists(dayNum);
      if (!exists) return null;

      java.util.List<Event> filtered = new java.util.ArrayList<>();
      persistence.streamEvents(
          dayNum,
          e -> {
            if (products.contains(e.getProduct())) {
              filtered.add(e);
            }
          });
      return filtered;
    } finally {
      readLock.unlock();
    }
  }

  public boolean waitForSimultaneous(String p1, String p2) throws InterruptedException {
    readLock.lock();
    CurrentDaySeries day;
    try {
      day = currentDay;
    } finally {
      readLock.unlock();
    }
    return day.waitForSimultaneous(p1, p2);
  }

  public String waitForConsecutive(int n) throws InterruptedException {
    readLock.lock();
    CurrentDaySeries day;
    try {
      day = currentDay;
    } finally {
      readLock.unlock();
    }
    return day.waitForConsecutive(n);
  }

  public int getMemoryCacheSize() {
    cacheLock.lock();
    try {

      return memoryCache.size();
    } finally {
      cacheLock.unlock();
    }
  }

  public int getAggregationCacheSize() {
    cacheLock.lock();
    try {

      return aggregationCache.size();
    } finally {
      cacheLock.unlock();
    }
  }

  public int getCurrentDayEventCount() {
    readLock.lock();
    try {
      return currentDay.size();
    } finally {
      readLock.unlock();
    }
  }
}

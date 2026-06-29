package common.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Aggregation {

  private final String product;
  private final int dayNumber;
  private final int totalQuantity;
  private final double totalVolume;
  private final double maxPrice;
  private final int eventCount;

  public Aggregation(
      String product,
      int dayNumber,
      int totalQuantity,
      double totalVolume,
      double maxPrice,
      int eventCount) {
        
    this.product = product;
    this.dayNumber = dayNumber;
    this.totalQuantity = totalQuantity;
    this.totalVolume = totalVolume;
    this.maxPrice = maxPrice;
    this.eventCount = eventCount;
  }

  public String getProduct() {
    return product;
  }

  public int getDayNumber() {
    return dayNumber;
  }

  public int getTotalQuantity() {
    return totalQuantity;
  }

  public double getTotalVolume() {
    return totalVolume;
  }

  public double getMaxPrice() {
    return maxPrice;
  }

  public int getEventCount() {
    return eventCount;
  }

  public double getAveragePrice() {
    if (eventCount == 0) {
      return 0;
    }
    return totalVolume / totalQuantity;
  }

  public Aggregation combine(Aggregation other) {
    if (!this.product.equals(other.product)) {
      throw new IllegalArgumentException("Produtos diferentes");
    }

    return new Aggregation(
        product,
        -1,
        this.totalQuantity + other.totalQuantity,
        this.totalVolume + other.totalVolume,
        Math.max(this.maxPrice, other.maxPrice),
        this.eventCount + other.eventCount);
  }

  public static Aggregation empty(String product, int dayNumber) {
    return new Aggregation(product, dayNumber, 0, 0, -1, 0);
  }

  public void serialize(DataOutputStream out) throws IOException {
    out.writeUTF(product);
    out.writeInt(dayNumber);
    out.writeInt(totalQuantity);
    out.writeDouble(totalVolume);
    out.writeDouble(maxPrice);
    out.writeInt(eventCount);
  }

  public static Aggregation deserialize(DataInputStream in) throws IOException {
    String product = in.readUTF();
    int dayNumber = in.readInt();
    int totalQuantity = in.readInt();
    double totalVolume = in.readDouble();
    double maxPrice = in.readDouble();
    int eventCount = in.readInt();
    return new Aggregation(product, dayNumber, totalQuantity, totalVolume, maxPrice, eventCount);
  }

  @Override
  public String toString() {
    return "Aggregation{"
        + "product='"
        + product
        + '\''
        + ", day="
        + dayNumber
        + ", qty="
        + totalQuantity
        + ", vol="
        + totalVolume
        + ", maxPrice="
        + maxPrice
        + ", events="
        + eventCount
        + '}';
  }
}

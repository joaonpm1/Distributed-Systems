package common.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Event {

  private final String product;
  private final int quantity;
  private final double price;

  public Event(String product, int quantity, double price) {
    this.product = product;
    this.quantity = quantity;
    this.price = price;
  }

  public String getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public double getPrice() {
    return price;
  }

  public double getValue() {
    return quantity * price;
  }

  public void serialize(DataOutputStream out) throws IOException {
    out.writeUTF(product);
    out.writeInt(quantity);
    out.writeDouble(price);
  }

  public static Event deserialize(DataInputStream in) throws IOException {
    String product = in.readUTF();
    int quantity = in.readInt();
    double price = in.readDouble();
    return new Event(product, quantity, price);
  }

  @Override
  public String toString() {
    return "Event{"
        + "product='"
        + product
        + '\''
        + ", quantity="
        + quantity
        + ", price="
        + price
        + '}';
  }
}

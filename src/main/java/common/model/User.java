package common.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class User {

  private final String username;
  private final String password;

  public User(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public boolean checkPassword(String password) {
    return this.password.equals(password);
  }

  public void serialize(DataOutputStream out) throws IOException {
    out.writeUTF(username);
    out.writeUTF(password);
  }

  public static User deserialize(DataInputStream in) throws IOException {
    String username = in.readUTF();
    String password = in.readUTF();
    return new User(username, password);
  }

  @Override
  public String toString() {
    return "User{username='" + username + "'}";
  }
}

package common.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Serializer {

  public static DataOutputStream createOutput(ByteArrayOutputStream baos) {
    return new DataOutputStream(baos);
  }

  public static DataInputStream createInput(byte[] data) {
    return new DataInputStream(new ByteArrayInputStream(data));
  }

  public static void writeString(DataOutputStream out, String s) throws IOException {
    if (s == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      out.writeUTF(s);
    }
  }

  public static String readString(DataInputStream in) throws IOException {
    if (in.readBoolean()) {
      return in.readUTF();
    }
    return null;
  }

  public static void writeStringList(DataOutputStream out, List<String> list) throws IOException {
    out.writeInt(list.size());
    for (String s : list) {
      out.writeUTF(s);
    }
  }

  public static List<String> readStringList(DataInputStream in) throws IOException {
    int size = in.readInt();
    List<String> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(in.readUTF());
    }
    return list;
  }

  public static void writeStringSet(DataOutputStream out, Set<String> set) throws IOException {
    out.writeInt(set.size());
    for (String s : set) {
      out.writeUTF(s);
    }
  }

  public static Set<String> readStringSet(DataInputStream in) throws IOException {
    int size = in.readInt();
    Set<String> set = new HashSet<>(size);
    for (int i = 0; i < size; i++) {
      set.add(in.readUTF());
    }
    return set;
  }

  private Serializer() {}
}

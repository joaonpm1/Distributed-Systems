package common.protocol;

public class Protocol {

  public static final int MSG_REGISTER = 1;

  public static final int MSG_LOGIN = 2;

  public static final int MSG_ADD_EVENT = 10;

  public static final int MSG_NEW_DAY = 11;

  public static final int MSG_QUANTITY = 20;

  public static final int MSG_VOLUME = 21;

  public static final int MSG_AVG_PRICE = 22;

  public static final int MSG_MAX_PRICE = 23;

  public static final int MSG_FILTER_EVENTS = 30;

  public static final int MSG_SIMULTANEOUS = 40;

  public static final int MSG_CONSECUTIVE = 41;

  public static final int OK = 0;

  public static final int ERROR = 1;

  public static final int ERROR_AUTH = 2;

  public static final int ERROR_USER_EXISTS = 3;

  public static final int ERROR_NOT_AUTH = 4;

  public static final int ERROR_INVALID_DAY = 5;

  public static final int ERROR_NO_DATA = 6;

  public static final int DEFAULT_PORT = 8080;

  private Protocol() {}
}

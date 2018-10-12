package net.consensys.pantheon.ethereum.p2p.wire.messages;

public final class PingMessage extends EmptyMessage {

  private static final PingMessage INSTANCE = new PingMessage();

  public static PingMessage get() {
    return INSTANCE;
  }

  private PingMessage() {}

  @Override
  public int getCode() {
    return WireMessageCodes.PING;
  }

  @Override
  public String toString() {
    return "PingMessage{data=''}";
  }
}

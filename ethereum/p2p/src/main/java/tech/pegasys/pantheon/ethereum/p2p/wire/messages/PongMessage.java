package net.consensys.pantheon.ethereum.p2p.wire.messages;

public final class PongMessage extends EmptyMessage {

  private static final PongMessage INSTANCE = new PongMessage();

  public static PongMessage get() {
    return INSTANCE;
  }

  private PongMessage() {}

  @Override
  public int getCode() {
    return WireMessageCodes.PONG;
  }
}

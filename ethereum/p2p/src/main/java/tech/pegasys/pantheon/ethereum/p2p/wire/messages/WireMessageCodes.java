package net.consensys.pantheon.ethereum.p2p.wire.messages;

public final class WireMessageCodes {
  public static final int HELLO = 0x00;
  public static final int DISCONNECT = 0x01;
  public static final int PING = 0x02;
  public static final int PONG = 0x03;

  private WireMessageCodes() {}
}

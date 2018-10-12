package tech.pegasys.pantheon.ethereum.eth.messages;

public final class EthPV62 {

  public static final int STATUS = 0x00;

  public static final int NEW_BLOCK_HASHES = 0x01;

  public static final int TRANSACTIONS = 0x02;

  public static final int GET_BLOCK_HEADERS = 0x03;

  public static final int BLOCK_HEADERS = 0x04;

  public static final int GET_BLOCK_BODIES = 0x05;

  public static final int BLOCK_BODIES = 0x06;

  public static final int NEW_BLOCK = 0X07;

  private EthPV62() {
    // Holder for constants only
  }
}

package tech.pegasys.pantheon.ethereum.eth.messages;

public final class EthPV63 {

  // Eth63 includes all message types from Eth62 (so see EthPV62 for where the live)

  // Plus some new message types
  public static final int GET_NODE_DATA = 0x0D;

  public static final int NODE_DATA = 0x0E;

  public static final int GET_RECEIPTS = 0x0F;

  public static final int RECEIPTS = 0x10;

  private EthPV63() {
    // Holder for constants only
  }
}

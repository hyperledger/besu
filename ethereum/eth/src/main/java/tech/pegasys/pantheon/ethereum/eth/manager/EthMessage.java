package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

public class EthMessage {

  private final EthPeer peer;
  private final MessageData data;

  public EthMessage(final EthPeer peer, final MessageData data) {
    this.peer = peer;
    this.data = data;
  }

  public EthPeer getPeer() {
    return peer;
  }

  public MessageData getData() {
    return data;
  }
}

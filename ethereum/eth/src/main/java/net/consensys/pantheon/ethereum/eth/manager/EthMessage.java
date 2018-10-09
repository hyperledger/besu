package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;

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

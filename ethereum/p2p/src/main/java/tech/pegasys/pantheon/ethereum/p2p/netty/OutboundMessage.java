package tech.pegasys.pantheon.ethereum.p2p.netty;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

final class OutboundMessage {

  private final Capability capability;

  private final MessageData messageData;

  OutboundMessage(final Capability capability, final MessageData data) {
    this.capability = capability;
    this.messageData = data;
  }

  public MessageData getData() {
    return messageData;
  }

  public Capability getCapability() {
    return capability;
  }
}

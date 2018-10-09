package net.consensys.pantheon.ethereum.p2p.wire;

import net.consensys.pantheon.ethereum.p2p.api.Message;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;

/**
 * Simple implementation of {@link Message} that associates a {@link MessageData} instance with a
 * {@link PeerConnection}.
 */
public final class DefaultMessage implements Message {

  private final MessageData data;

  private final PeerConnection connection;

  public DefaultMessage(final PeerConnection channel, final MessageData data) {
    this.connection = channel;
    this.data = data;
  }

  @Override
  public PeerConnection getConnection() {
    return connection;
  }

  @Override
  public MessageData getData() {
    return data;
  }
}

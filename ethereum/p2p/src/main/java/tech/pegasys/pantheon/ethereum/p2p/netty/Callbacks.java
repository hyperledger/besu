package tech.pegasys.pantheon.ethereum.p2p.netty;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Callbacks {
  private static final Logger LOG = LogManager.getLogger();
  private static final Subscribers<Consumer<Message>> NO_SUBSCRIBERS = new Subscribers<>();

  private final Map<Capability, Subscribers<Consumer<Message>>> callbacks;

  private final Subscribers<DisconnectCallback> disconnectCallbacks;

  Callbacks(
      final Map<Capability, Subscribers<Consumer<Message>>> callbacks,
      final Subscribers<DisconnectCallback> disconnectCallbacks) {
    this.callbacks = callbacks;
    this.disconnectCallbacks = disconnectCallbacks;
  }

  public void invokeDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initatedByPeer) {
    disconnectCallbacks.forEach(
        consumer -> consumer.onDisconnect(connection, reason, initatedByPeer));
  }

  public void invokeSubProtocol(
      final PeerConnection connection, final Capability capability, final MessageData message) {
    final Message fullMessage = new DefaultMessage(connection, message);
    callbacks
        .getOrDefault(capability, NO_SUBSCRIBERS)
        .forEach(
            consumer -> {
              try {
                consumer.accept(fullMessage);
              } catch (final Throwable t) {
                LOG.error("Error in callback:", t);
              }
            });
  }
}

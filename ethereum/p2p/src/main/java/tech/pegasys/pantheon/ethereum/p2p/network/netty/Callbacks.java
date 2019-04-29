/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.network.netty;

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

  public Callbacks(
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

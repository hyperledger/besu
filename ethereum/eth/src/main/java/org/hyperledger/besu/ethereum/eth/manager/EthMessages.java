/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.util.Subscribers;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthMessages {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<Integer, Subscribers<MessageCallback>> listenersByCode =
      new ConcurrentHashMap<>();
  private final Map<Integer, MessageResponseConstructor> messageResponseConstructorsByCode =
      new ConcurrentHashMap<>();

  void dispatch(final EthMessage message) {
    final int code = message.getData().getCode();
    Optional.ofNullable(listenersByCode.get(code))
        .ifPresent(
            listeners -> listeners.forEach(messageCallback -> messageCallback.exec(message)));

    try {
      Optional.ofNullable(messageResponseConstructorsByCode.get(code))
          .map(messageResponseConstructor -> messageResponseConstructor.response(message.getData()))
          .ifPresent(
              messageData -> {
                try {
                  message.getPeer().send(messageData);
                } catch (final PeerConnection.PeerNotConnected __) {
                  // Peer disconnected before we could respond - nothing to do
                }
              });

    } catch (final RLPException e) {
      LOG.debug(
          "Received malformed message {} , disconnecting: {}",
          message.getData().getData(),
          message.getPeer(),
          e);
      message.getPeer().disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
    }
  }

  public void subscribe(final int messageCode, final MessageCallback callback) {
    listenersByCode.computeIfAbsent(messageCode, key -> Subscribers.create()).subscribe(callback);
  }

  public void registerResponseConstructor(
      final int messageCode, final MessageResponseConstructor messageResponseConstructor) {
    messageResponseConstructorsByCode.put(messageCode, messageResponseConstructor);
  }

  @FunctionalInterface
  public interface MessageCallback {
    void exec(EthMessage message);
  }

  @FunctionalInterface
  public interface MessageResponseConstructor {
    MessageData response(MessageData message);
  }
}

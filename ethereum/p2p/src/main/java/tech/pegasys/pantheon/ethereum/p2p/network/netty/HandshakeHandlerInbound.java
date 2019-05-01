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

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.Handshaker;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;

public final class HandshakeHandlerInbound extends AbstractHandshakeHandler {

  public HandshakeHandlerInbound(
      final SECP256K1.KeyPair kp,
      final List<SubProtocol> subProtocols,
      final PeerInfo ourInfo,
      final CompletableFuture<PeerConnection> connectionFuture,
      final Callbacks callbacks,
      final PeerConnectionRegistry peerConnectionRegistry,
      final LabelledMetric<Counter> outboundMessagesCounter) {
    super(
        subProtocols,
        ourInfo,
        Optional.empty(),
        connectionFuture,
        callbacks,
        peerConnectionRegistry,
        outboundMessagesCounter);
    handshaker.prepareResponder(kp);
  }

  @Override
  protected Optional<ByteBuf> nextHandshakeMessage(final ByteBuf msg) {
    final Optional<ByteBuf> nextMsg;
    if (handshaker.getStatus() == Handshaker.HandshakeStatus.IN_PROGRESS) {
      nextMsg = handshaker.handleMessage(msg);
    } else {
      nextMsg = Optional.empty();
    }
    return nextMsg;
  }
}

/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.plain;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeException;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeSecrets;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlainHandshaker implements Handshaker {

  private static final Logger LOG = LoggerFactory.getLogger(PlainHandshaker.class);

  private final AtomicReference<Handshaker.HandshakeStatus> status =
      new AtomicReference<>(Handshaker.HandshakeStatus.UNINITIALIZED);

  private boolean initiator;
  private NodeKey nodeKey;
  private SECPPublicKey partyPubKey;
  private Bytes initiatorMsg;
  private Bytes responderMsg;

  @Override
  public void prepareInitiator(final NodeKey nodeKey, final SECPPublicKey theirPubKey) {
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.UNINITIALIZED, Handshaker.HandshakeStatus.PREPARED),
        "handshake was already prepared");

    this.initiator = true;
    this.nodeKey = nodeKey;
    this.partyPubKey = theirPubKey;
    LOG.trace(
        "Prepared plain handshake with node {}... under INITIATOR role",
        theirPubKey.getEncodedBytes().slice(0, 16));
  }

  @Override
  public void prepareResponder(final NodeKey nodeKey) {
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.UNINITIALIZED, Handshaker.HandshakeStatus.IN_PROGRESS),
        "handshake was already prepared");

    this.initiator = false;
    this.nodeKey = nodeKey;
    LOG.trace("Prepared plain handshake under RESPONDER role");
  }

  @Override
  public ByteBuf firstMessage() throws HandshakeException {
    checkState(initiator, "illegal invocation of firstMessage on non-initiator end of handshake");
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.PREPARED, Handshaker.HandshakeStatus.IN_PROGRESS),
        "illegal invocation of firstMessage, handshake had already started");
    initiatorMsg =
        MessageHandler.buildMessage(MessageType.PING, nodeKey.getPublicKey().getEncoded());

    LOG.trace("First plain handshake message under INITIATOR role");

    return Unpooled.wrappedBuffer(initiatorMsg.toArray());
  }

  @Override
  public Optional<ByteBuf> handleMessage(final ByteBuf buf) throws HandshakeException {
    checkState(
        status.get() == Handshaker.HandshakeStatus.IN_PROGRESS,
        "illegal invocation of onMessage on handshake that is not in progress");

    PlainMessage message = MessageHandler.parseMessage(buf);

    Optional<Bytes> nextMsg = Optional.empty();
    if (initiator) {
      checkState(
          responderMsg == null,
          "unexpected message: responder message had " + "already been received");

      checkState(
          message.getMessageType().equals(MessageType.PONG),
          "unexpected message: needs to be a pong");
      responderMsg = message.getData();

      LOG.trace(
          "Received responder's plain handshake message from node {}...: {}",
          partyPubKey.getEncodedBytes().slice(0, 16),
          responderMsg);

    } else {
      checkState(
          initiatorMsg == null,
          "unexpected message: initiator message " + "had already been received");
      checkState(
          message.getMessageType().equals(MessageType.PING),
          "unexpected message: needs to be a ping");

      initiatorMsg = message.getData();
      LOG.trace(
          "[{}] Received initiator's plain handshake message: {}",
          nodeKey.getPublicKey().getEncodedBytes(),
          initiatorMsg);

      partyPubKey = SignatureAlgorithmFactory.getInstance().createPublicKey(message.getData());

      responderMsg =
          MessageHandler.buildMessage(MessageType.PONG, nodeKey.getPublicKey().getEncoded());

      LOG.trace(
          "Generated responder's plain handshake message against peer {}...: {}",
          partyPubKey.getEncodedBytes().slice(0, 16),
          responderMsg);

      nextMsg = Optional.of(Bytes.wrap(responderMsg.toArray()));
    }

    status.set(Handshaker.HandshakeStatus.SUCCESS);
    LOG.trace("Handshake status set to {}", status.get());
    return nextMsg.map(bv -> Unpooled.wrappedBuffer(bv.toArray()));
  }

  void computeSecrets() {}

  @Override
  public HandshakeStatus getStatus() {
    return status.get();
  }

  @Override
  public HandshakeSecrets secrets() {
    return null;
  }

  @Override
  public SECPPublicKey partyPubKey() {
    return partyPubKey;
  }
}

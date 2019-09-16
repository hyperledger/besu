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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake.ecies;

import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.util.bytes.Bytes32s.xor;
import static org.hyperledger.besu.util.bytes.BytesValues.concatenate;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeException;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.HandshakeSecrets;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.Handshaker;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.InvalidCipherTextException;

/**
 * An Elliptic Curve Integrated Encryption Scheme implementation, following the handshake ceremony
 * of Ethereum.
 *
 * @see <a href="https://github.com/ethereum/devp2p/blob/master/rlpx.md#encrypted-handshake">RLPx
 *     encrypted handshake</a>
 */
public class ECIESHandshaker implements Handshaker {
  private static final Logger LOG = LogManager.getLogger();
  private static final SecureRandom RANDOM = SecureRandomProvider.publicSecureRandom();

  static final int SIGNATURE_LENGTH = 65;
  static final int HASH_EPH_PUBKEY_LENGTH = 32;
  static final int PUBKEY_LENGTH = 64;
  static final int NONCE_LENGTH = 32;
  static final int TOKEN_FLAG_LENGTH = 1;

  // Keypairs under our control.
  private SECP256K1.KeyPair identityKeyPair;
  private SECP256K1.KeyPair ephKeyPair;

  // Party's material, only public keys.
  private PublicKey partyPubKey;
  private PublicKey partyEphPubKey;

  // Messages, for later MAC calculation.
  private InitiatorHandshakeMessage initiatorMsg;
  private ResponderHandshakeMessage responderMsg;
  private BytesValue initiatorMsgEnc;
  private BytesValue responderMsgEnc;

  // Nonces.
  private Bytes32 initiatorNonce;
  private Bytes32 responderNonce;

  // Whether we are the party who initiated this handshake or not.
  private boolean initiator;

  // See Javadoc on #secrets() to understand the state machine.
  private final AtomicReference<Handshaker.HandshakeStatus> status =
      new AtomicReference<>(Handshaker.HandshakeStatus.UNINITIALIZED);
  private HandshakeSecrets secrets;

  private boolean version4 = true;

  @Override
  public void prepareInitiator(final SECP256K1.KeyPair ourKeypair, final PublicKey theirPubKey) {
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.UNINITIALIZED, Handshaker.HandshakeStatus.PREPARED),
        "handshake was already prepared");

    this.initiator = true;
    this.identityKeyPair = ourKeypair;
    this.ephKeyPair = SECP256K1.KeyPair.generate();
    this.partyPubKey = theirPubKey;
    this.initiatorNonce = Bytes32.wrap(random(32), 0);
    LOG.trace(
        "Prepared ECIES handshake with node {}... under INITIATOR role",
        theirPubKey.getEncodedBytes().slice(0, 16));
  }

  @Override
  public void prepareResponder(final SECP256K1.KeyPair ourKeypair) {
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.UNINITIALIZED, Handshaker.HandshakeStatus.IN_PROGRESS),
        "handshake was already prepared");

    this.initiator = false;
    this.identityKeyPair = ourKeypair;
    this.ephKeyPair = SECP256K1.KeyPair.generate();
    this.responderNonce = Bytes32.wrap(random(32), 0);
    LOG.trace("Prepared ECIES handshake under RESPONDER role");
  }

  @Override
  public ByteBuf firstMessage() throws HandshakeException {
    checkState(initiator, "illegal invocation of firstMessage on non-initiator end of handshake");
    checkState(
        status.compareAndSet(
            Handshaker.HandshakeStatus.PREPARED, Handshaker.HandshakeStatus.IN_PROGRESS),
        "illegal invocation of firstMessage, handshake had already started");

    final Bytes32 staticSharedSecret =
        SECP256K1.calculateKeyAgreement(identityKeyPair.getPrivateKey(), partyPubKey);
    if (version4) {
      initiatorMsg =
          InitiatorHandshakeMessageV4.create(
              identityKeyPair.getPublicKey(), ephKeyPair, staticSharedSecret, initiatorNonce);
    } else {
      initiatorMsg =
          InitiatorHandshakeMessageV1.create(
              identityKeyPair.getPublicKey(),
              ephKeyPair,
              staticSharedSecret,
              initiatorNonce,
              false);
    }
    try {
      if (version4) {
        initiatorMsgEnc = EncryptedMessage.encryptMsgEip8(initiatorMsg.encode(), partyPubKey);
      } else {
        initiatorMsgEnc = EncryptedMessage.encryptMsg(initiatorMsg.encode(), partyPubKey);
      }
    } catch (final InvalidCipherTextException e) {
      status.set(Handshaker.HandshakeStatus.FAILED);
      throw new HandshakeException("Encrypting the first handshake message failed", e);
    }

    LOG.trace("First ECIES handshake message under INITIATOR role: {}", initiatorMsg);

    return Unpooled.wrappedBuffer(initiatorMsgEnc.extractArray());
  }

  @Override
  public Optional<ByteBuf> handleMessage(final ByteBuf buf) throws HandshakeException {
    checkState(
        status.get() == Handshaker.HandshakeStatus.IN_PROGRESS,
        "illegal invocation of onMessage on handshake that is not in progress");

    // Take as many bytes as expected in the next message.
    int expectedLength = ECIESEncryptionEngine.ENCRYPTION_OVERHEAD;
    expectedLength +=
        initiator
            ? ResponderHandshakeMessageV1.MESSAGE_LENGTH
            : InitiatorHandshakeMessageV1.MESSAGE_LENGTH;

    if (buf.readableBytes() < expectedLength) {
      buf.markReaderIndex();
      final int size = buf.readUnsignedShort();
      if (size > buf.readableBytes() + 2) {
        buf.resetReaderIndex();
        return Optional.empty();
      }
      expectedLength = size;
      buf.resetReaderIndex();
    }

    buf.markReaderIndex();
    final ByteBuf bufferedBytes = buf.readSlice(expectedLength);
    final byte[] encryptedBytes = new byte[bufferedBytes.readableBytes()];
    bufferedBytes.getBytes(0, encryptedBytes);
    BytesValue bytes = BytesValue.wrap(encryptedBytes);

    BytesValue encryptedMsg = bytes;
    try {
      // Decrypt the message with our private key.
      try {
        bytes = EncryptedMessage.decryptMsg(bytes, identityKeyPair.getPrivateKey());
        version4 = false;
      } catch (final Exception ex) {
        // Assume new format
        final int size = bufferedBytes.readUnsignedShort();
        if (buf.writerIndex() >= size) {
          bufferedBytes.readerIndex(0);
          final byte[] fullMessage = new byte[size + 2];
          bufferedBytes.readBytes(fullMessage, 0, expectedLength);
          buf.readBytes(fullMessage, expectedLength, size - expectedLength + 2);
          encryptedMsg = BytesValue.wrap(fullMessage);
          bytes = EncryptedMessage.decryptMsgEIP8(encryptedMsg, identityKeyPair.getPrivateKey());
          version4 = true;
        } else {
          throw new HandshakeException("Failed to decrypt handshake message", ex);
        }
      }
    } catch (final InvalidCipherTextException e) {
      status.set(Handshaker.HandshakeStatus.FAILED);
      throw new HandshakeException("Decrypting an incoming handshake message failed", e);
    }

    Optional<BytesValue> nextMsg = Optional.empty();
    if (initiator) {
      // If we are the initiator, we have already sent our request and we're waiting for the
      // responder's ack;
      // when we receive it, we can build the handshake secret material and declare a SUCCESS.
      checkState(
          responderMsg == null,
          "unexpected message: responder message had " + "already been received");

      // Store the message, as we need it to generating our ingress and egress MACs.
      responderMsgEnc = encryptedMsg;
      if (version4) {
        responderMsg = ResponderHandshakeMessageV4.decode(bytes);
      } else {
        responderMsg = ResponderHandshakeMessageV1.decode(bytes);
      }

      // Extract the responder's nonce and ephemeral pubkey, which will be used to generate the
      // shared secrets.
      responderNonce = responderMsg.getNonce();
      partyEphPubKey = responderMsg.getEphPublicKey();

      LOG.trace(
          "Received responder's ECIES handshake message from node {}...: {}",
          partyPubKey.getEncodedBytes().slice(0, 16),
          responderMsg);

    } else {
      // If we are the responder, we are waiting for an initiator message; after we generate our
      // message and
      // we can build the handshake secret material and declare a SUCCESS.
      checkState(
          initiatorMsg == null,
          "unexpected message: initiator message " + "had already been received");

      // Store the message, as we need it to generating our ingress and egress MACs.
      initiatorMsgEnc = encryptedMsg;
      if (version4) {
        initiatorMsg = InitiatorHandshakeMessageV4.decode(bytes, identityKeyPair);
      } else {
        initiatorMsg = InitiatorHandshakeMessageV1.decode(bytes, identityKeyPair);
      }

      LOG.trace(
          "[{}] Received initiator's ECIES handshake message: {}",
          identityKeyPair.getPublicKey().getEncodedBytes(),
          initiatorMsg);

      // Extract the initiator's data.
      initiatorNonce = initiatorMsg.getNonce();
      partyPubKey = initiatorMsg.getPubKey();
      partyEphPubKey = initiatorMsg.getEphPubKey();

      checkState(
          keccak256(partyEphPubKey.getEncodedBytes()).equals(initiatorMsg.getEphPubKeyHash()),
          "keccak hash of recovered ephemeral pubkey does not match announced hash");

      // Build the response message.
      if (version4) {
        responderMsg =
            ResponderHandshakeMessageV4.create(ephKeyPair.getPublicKey(), responderNonce);
      } else {
        responderMsg =
            ResponderHandshakeMessageV1.create(ephKeyPair.getPublicKey(), responderNonce, false);
      }

      LOG.trace(
          "Generated responder's ECIES handshake message against peer {}...: {}",
          partyPubKey.getEncodedBytes().slice(0, 16),
          responderMsg);

      try {
        if (version4) {
          responderMsgEnc = EncryptedMessage.encryptMsgEip8(responderMsg.encode(), partyPubKey);
        } else {
          responderMsgEnc = EncryptedMessage.encryptMsg(responderMsg.encode(), partyPubKey);
        }
      } catch (final InvalidCipherTextException e) {
        status.set(Handshaker.HandshakeStatus.FAILED);
        throw new HandshakeException("Encrypting the next handshake message failed", e);
      }
      nextMsg = Optional.of(responderMsgEnc);

      // Compute the secrets and declare this handshake as successful.
    }
    computeSecrets();

    status.set(Handshaker.HandshakeStatus.SUCCESS);
    LOG.trace("Handshake status set to {}", status.get());
    return nextMsg.map(bv -> Unpooled.wrappedBuffer(bv.extractArray()));
  }

  /**
   * Returns the current status of this handshake.
   *
   * <p>Starts {@link Handshaker.HandshakeStatus#UNINITIALIZED} and moves to {@link
   * Handshaker.HandshakeStatus#PREPARED} when a prepared* method is called, or to {@link
   * Handshaker.HandshakeStatus#IN_PROGRESS} if we're the responder part and have nothing to prepare
   * since we're awaiting the initiator's message.
   *
   * <p>As soon as we receive the expected message, the status transitions to {@link
   * Handshaker.HandshakeStatus#SUCCESS} if the message is well formed and we're able to generate
   * the resulting secrets.
   *
   * @return Returns the current status of this handshake.
   */
  @Override
  public Handshaker.HandshakeStatus getStatus() {
    return status.get();
  }

  @Override
  public HandshakeSecrets secrets() {
    checkState(
        status.get() == Handshaker.HandshakeStatus.SUCCESS,
        "cannot obtain secrets from an unsuccessful handshake");
    return secrets;
  }

  @Override
  public PublicKey partyPubKey() {
    checkState(
        initiator || status.get() == Handshaker.HandshakeStatus.SUCCESS,
        "under the role of responder, cannot return the party's public "
            + "key until the handshake has completed");
    return partyPubKey;
  }

  /** Computes the secrets from the two exchanged messages. */
  void computeSecrets() {
    final BytesValue agreedSecret =
        SECP256K1.calculateKeyAgreement(ephKeyPair.getPrivateKey(), partyEphPubKey);
    final BytesValue sharedSecret =
        keccak256(
            concatenate(agreedSecret, keccak256(concatenate(responderNonce, initiatorNonce))));

    final Bytes32 aesSecret = keccak256(concatenate(agreedSecret, sharedSecret));
    final Bytes32 macSecret = keccak256(concatenate(agreedSecret, aesSecret));
    final Bytes32 token = keccak256(sharedSecret);

    final HandshakeSecrets secrets =
        new HandshakeSecrets(
            aesSecret.extractArray(), macSecret.extractArray(), token.extractArray());

    final BytesValue initiatorMac = concatenate(xor(macSecret, responderNonce), initiatorMsgEnc);
    final BytesValue responderMac = concatenate(xor(macSecret, initiatorNonce), responderMsgEnc);

    if (initiator) {
      secrets.updateEgress(initiatorMac.extractArray());
      secrets.updateIngress(responderMac.extractArray());
    } else {
      secrets.updateIngress(initiatorMac.extractArray());
      secrets.updateEgress(responderMac.extractArray());
    }

    this.secrets = secrets;
  }

  static BytesValue random(final int size) {
    final byte[] iv = new byte[size];
    RANDOM.nextBytes(iv);
    return BytesValue.wrap(iv);
  }

  // ---------------------------------------------
  //  The methods below are for testing purposes.
  // ---------------------------------------------

  @VisibleForTesting
  SECP256K1.KeyPair getIdentityKeyPair() {
    return identityKeyPair;
  }

  @VisibleForTesting
  SECP256K1.KeyPair getEphKeyPair() {
    return ephKeyPair;
  }

  @VisibleForTesting
  void setEphKeyPair(final SECP256K1.KeyPair ephKeyPair) {
    this.ephKeyPair = ephKeyPair;
  }

  @VisibleForTesting
  PublicKey getPartyEphPubKey() {
    return partyEphPubKey;
  }

  @VisibleForTesting
  Bytes32 getInitiatorNonce() {
    return initiatorNonce;
  }

  @VisibleForTesting
  void setInitiatorNonce(final Bytes32 initiatorNonce) {
    this.initiatorNonce = initiatorNonce;
  }

  @VisibleForTesting
  Bytes32 getResponderNonce() {
    return responderNonce;
  }

  @VisibleForTesting
  void setResponderNonce(final Bytes32 responderNonce) {
    this.responderNonce = responderNonce;
  }

  @VisibleForTesting
  void setInitiatorMsgEnc(final BytesValue initiatorMsgEnc) {
    this.initiatorMsgEnc = initiatorMsgEnc;
  }

  @VisibleForTesting
  void setResponderMsgEnc(final BytesValue responderMsgEnc) {
    this.responderMsgEnc = responderMsgEnc;
  }
}

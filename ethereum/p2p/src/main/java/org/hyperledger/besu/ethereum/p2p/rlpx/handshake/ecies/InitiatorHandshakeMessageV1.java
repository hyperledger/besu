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
import static org.hyperledger.besu.crypto.SECP256K1.calculateKeyAgreement;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.Bytes32s;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.MutableBytes32;
import org.hyperledger.besu.util.bytes.MutableBytesValue;

/**
 * The initiator's handshake message.
 *
 * <p>This message must be sent by the party that initiates the RLPX connection, as the first
 * message in the handshake protocol.
 *
 * <h3>Message structure</h3>
 *
 * The following describes the message structure:
 *
 * <pre>
 *   authInitiator -&gt; E(remote-pubk,
 *                      S(ephemeral-privk, static-shared-secret ^ nonce)
 *                       || H(ephemeral-pubk)
 *                      || pubk
 *                      || nonce
 *                      || 0x0)
 * </pre>
 *
 * @see <a href=
 *     "https://github.com/ethereum/devp2p/blob/master/rlpx.md#encrypted-handshake">Structure of the
 *     initiator request</a>
 */
public final class InitiatorHandshakeMessageV1 implements InitiatorHandshakeMessage {
  public static final int MESSAGE_LENGTH =
      ECIESHandshaker.SIGNATURE_LENGTH
          + ECIESHandshaker.HASH_EPH_PUBKEY_LENGTH
          + ECIESHandshaker.PUBKEY_LENGTH
          + ECIESHandshaker.NONCE_LENGTH
          + ECIESHandshaker.TOKEN_FLAG_LENGTH;

  private final SECP256K1.PublicKey pubKey;
  private final SECP256K1.Signature signature;
  private final SECP256K1.PublicKey ephPubKey;
  private final Bytes32 ephPubKeyHash;
  private final Bytes32 nonce;
  private final boolean token;

  protected InitiatorHandshakeMessageV1(
      final SECP256K1.PublicKey pubKey,
      final SECP256K1.Signature signature,
      final SECP256K1.PublicKey ephPubKey,
      final Bytes32 ephPubKeyHash,
      final Bytes32 nonce,
      final boolean token) {
    this.pubKey = pubKey;
    this.signature = signature;
    this.ephPubKey = ephPubKey;
    this.ephPubKeyHash = ephPubKeyHash;
    this.nonce = nonce;
    this.token = token;
  }

  public static InitiatorHandshakeMessageV1 create(
      final SECP256K1.PublicKey ourPubKey,
      final SECP256K1.KeyPair ephKeyPair,
      final Bytes32 staticSharedSecret,
      final Bytes32 nonce,
      final boolean token) {
    final Bytes32 ephPubKeyHash = Hash.keccak256(ephKeyPair.getPublicKey().getEncodedBytes());

    // XOR of the static shared secret and the generated nonce.
    final MutableBytes32 toSign = MutableBytes32.create();
    Bytes32s.xor(staticSharedSecret, nonce, toSign);
    final SECP256K1.Signature signature = SECP256K1.sign(toSign, ephKeyPair);
    return new InitiatorHandshakeMessageV1(
        ourPubKey, signature, ephKeyPair.getPublicKey(), ephPubKeyHash, nonce, token);
  }

  /**
   * Decodes this message.
   *
   * @param bytes The raw bytes.
   * @param keyPair Our keypair to calculat ECDH key agreements.
   * @return The decoded message.
   */
  public static InitiatorHandshakeMessageV1 decode(
      final BytesValue bytes, final SECP256K1.KeyPair keyPair) {
    checkState(bytes.size() == MESSAGE_LENGTH);

    int offset = 0;
    final SECP256K1.Signature signature =
        SECP256K1.Signature.decode(bytes.slice(offset, ECIESHandshaker.SIGNATURE_LENGTH));
    final Bytes32 ephPubKeyHash =
        Bytes32.wrap(
            bytes.slice(
                offset += ECIESHandshaker.SIGNATURE_LENGTH, ECIESHandshaker.HASH_EPH_PUBKEY_LENGTH),
            0);
    final SECP256K1.PublicKey pubKey =
        SECP256K1.PublicKey.create(
            bytes.slice(
                offset += ECIESHandshaker.HASH_EPH_PUBKEY_LENGTH, ECIESHandshaker.PUBKEY_LENGTH));
    final Bytes32 nonce =
        Bytes32.wrap(
            bytes.slice(offset += ECIESHandshaker.PUBKEY_LENGTH, ECIESHandshaker.NONCE_LENGTH), 0);
    final boolean token = bytes.get(offset) == 0x01;

    final Bytes32 staticSharedSecret = calculateKeyAgreement(keyPair.getPrivateKey(), pubKey);
    final Bytes32 toSign = Bytes32s.xor(staticSharedSecret, nonce);
    final SECP256K1.PublicKey ephPubKey =
        SECP256K1.PublicKey.recoverFromSignature(toSign, signature)
            .orElseThrow(() -> new RuntimeException("Could not recover public key from signature"));

    return new InitiatorHandshakeMessageV1(
        pubKey, signature, ephPubKey, ephPubKeyHash, nonce, token);
  }

  @Override
  public BytesValue encode() {
    final MutableBytesValue bytes = MutableBytesValue.create(MESSAGE_LENGTH);
    signature.encodedBytes().copyTo(bytes, 0);
    ephPubKeyHash.copyTo(bytes, ECIESHandshaker.SIGNATURE_LENGTH);
    pubKey
        .getEncodedBytes()
        .copyTo(bytes, ECIESHandshaker.SIGNATURE_LENGTH + ECIESHandshaker.HASH_EPH_PUBKEY_LENGTH);
    nonce.copyTo(
        bytes,
        ECIESHandshaker.SIGNATURE_LENGTH
            + ECIESHandshaker.HASH_EPH_PUBKEY_LENGTH
            + ECIESHandshaker.PUBKEY_LENGTH);
    bytes.set(MESSAGE_LENGTH - 1, (byte) (token ? 0x01 : 0x00));
    return bytes;
  }

  @Override
  public SECP256K1.PublicKey getPubKey() {
    return pubKey;
  }

  @Override
  public Bytes32 getEphPubKeyHash() {
    return ephPubKeyHash;
  }

  @Override
  public Bytes32 getNonce() {
    return nonce;
  }

  @Override
  public SECP256K1.PublicKey getEphPubKey() {
    return ephPubKey;
  }

  @Override
  public String toString() {
    return "InitiatorHandshakeMessage{"
        + "pubKey="
        + pubKey
        + ", signature="
        + signature
        + ", ephPubKey="
        + ephPubKey
        + ", ephPubKeyHash="
        + ephPubKeyHash
        + ", nonce="
        + nonce
        + ", token="
        + token
        + '}';
  }
}

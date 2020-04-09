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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake.ecies;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public final class InitiatorHandshakeMessageV4 implements InitiatorHandshakeMessage {

  public static final int VERSION = 4;

  private final SECP256K1.PublicKey pubKey;
  private final SECP256K1.Signature signature;
  private final SECP256K1.PublicKey ephPubKey;
  private final Bytes32 ephPubKeyHash;
  private final Bytes32 nonce;

  public static InitiatorHandshakeMessageV4 create(
      final SECP256K1.PublicKey ourPubKey,
      final SECP256K1.KeyPair ephKeyPair,
      final Bytes32 staticSharedSecret,
      final Bytes32 nonce) {
    return new InitiatorHandshakeMessageV4(
        ourPubKey,
        SECP256K1.sign(staticSharedSecret.xor(nonce), ephKeyPair),
        ephKeyPair.getPublicKey(),
        nonce);
  }

  /**
   * Decodes this message.
   *
   * @param bytes The raw bytes.
   * @param nodeKey Abstraction of the local nodes keys and associated cryptographic operations
   * @return The decoded message.
   */
  public static InitiatorHandshakeMessageV4 decode(final Bytes bytes, final NodeKey nodeKey) {
    final RLPInput input = new BytesValueRLPInput(bytes, true);
    input.enterList();
    final SECP256K1.Signature signature = SECP256K1.Signature.decode(input.readBytes());
    final SECP256K1.PublicKey pubKey = SECP256K1.PublicKey.create(input.readBytes());
    final Bytes32 nonce = input.readBytes32();
    final Bytes32 staticSharedSecret = nodeKey.calculateECDHKeyAgreement(pubKey);
    final SECP256K1.PublicKey ephPubKey =
        SECP256K1.PublicKey.recoverFromSignature(staticSharedSecret.xor(nonce), signature)
            .orElseThrow(() -> new RuntimeException("Could not recover public key from signature"));

    return new InitiatorHandshakeMessageV4(pubKey, signature, ephPubKey, nonce);
  }

  private InitiatorHandshakeMessageV4(
      final SECP256K1.PublicKey pubKey,
      final SECP256K1.Signature signature,
      final SECP256K1.PublicKey ephPubKey,
      final Bytes32 nonce) {
    this.pubKey = pubKey;
    this.signature = signature;
    this.ephPubKey = ephPubKey;
    this.ephPubKeyHash = Hash.keccak256(ephPubKey.getEncodedBytes());
    this.nonce = nonce;
  }

  @Override
  public Bytes encode() {
    final BytesValueRLPOutput temp = new BytesValueRLPOutput();
    temp.startList();
    temp.writeBytes(signature.encodedBytes());
    temp.writeBytes(pubKey.getEncodedBytes());
    temp.writeBytes(nonce);
    temp.writeIntScalar(VERSION);
    temp.endList();
    return temp.encoded();
  }

  @Override
  public Bytes32 getNonce() {
    return nonce;
  }

  @Override
  public SECP256K1.PublicKey getPubKey() {
    return pubKey;
  }

  @Override
  public SECP256K1.PublicKey getEphPubKey() {
    return ephPubKey;
  }

  @Override
  public Bytes32 getEphPubKeyHash() {
    return ephPubKeyHash;
  }
}

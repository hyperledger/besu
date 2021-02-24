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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * The responder's handshake message.
 *
 * <p>This message must be sent by the party who responded to the RLPX connection, in response to
 * the initiator message.
 *
 * <h3>Message structure</h3>
 *
 * The following describes the message structure:
 *
 * <pre>
 *   authRecipient -&gt; E(remote-pubk,
 *                      remote-ephemeral-pubk
 *                      || nonce
 *                      || 0x0)
 * </pre>
 *
 * @see <a href=
 *     "https://github.com/ethereum/devp2p/blob/master/rlpx.md#encrypted-handshake">Structure of the
 *     responder response</a>
 */
public final class ResponderHandshakeMessageV1 implements ResponderHandshakeMessage {
  public static final int MESSAGE_LENGTH =
      ECIESHandshaker.PUBKEY_LENGTH
          + ECIESHandshaker.NONCE_LENGTH
          + ECIESHandshaker.TOKEN_FLAG_LENGTH;

  private final SECPPublicKey ephPublicKey; // 64 bytes - uncompressed and no type byte
  private final Bytes32 nonce; // 32 bytes
  private final boolean token; // 1 byte - 0x00 or 0x01

  private ResponderHandshakeMessageV1(
      final SECPPublicKey ephPublicKey, final Bytes32 nonce, final boolean token) {
    this.ephPublicKey = ephPublicKey;
    this.nonce = nonce;
    this.token = token;
  }

  public static ResponderHandshakeMessageV1 create(
      final SECPPublicKey ephPublicKey, final Bytes32 nonce, final boolean token) {
    return new ResponderHandshakeMessageV1(ephPublicKey, nonce, token);
  }

  public static ResponderHandshakeMessageV1 decode(final Bytes bytes) {
    checkArgument(bytes.size() == MESSAGE_LENGTH);

    final Bytes pubk = bytes.slice(0, ECIESHandshaker.PUBKEY_LENGTH);
    final SECPPublicKey ephPubKey = SignatureAlgorithmFactory.getInstance().createPublicKey(pubk);
    final Bytes32 nonce =
        Bytes32.wrap(bytes.slice(ECIESHandshaker.PUBKEY_LENGTH, ECIESHandshaker.NONCE_LENGTH), 0);
    final boolean token =
        bytes.get(ECIESHandshaker.PUBKEY_LENGTH + ECIESHandshaker.NONCE_LENGTH) == 0x01;
    return new ResponderHandshakeMessageV1(ephPubKey, nonce, token);
  }

  @Override
  public Bytes encode() {
    final MutableBytes bytes = MutableBytes.create(MESSAGE_LENGTH);
    ephPublicKey.getEncodedBytes().copyTo(bytes, 0);
    nonce.copyTo(bytes, ECIESHandshaker.PUBKEY_LENGTH);
    Bytes.of((byte) (token ? 0x01 : 0x00))
        .copyTo(bytes, ECIESHandshaker.PUBKEY_LENGTH + ECIESHandshaker.NONCE_LENGTH);
    return bytes;
  }

  @Override
  public SECPPublicKey getEphPublicKey() {
    return ephPublicKey;
  }

  @Override
  public Bytes32 getNonce() {
    return nonce;
  }

  @Override
  public String toString() {
    return "ResponderHandshakeMessage{"
        + "ephPublicKey="
        + ephPublicKey
        + ", nonce="
        + nonce
        + ", token="
        + token
        + '}';
  }
}

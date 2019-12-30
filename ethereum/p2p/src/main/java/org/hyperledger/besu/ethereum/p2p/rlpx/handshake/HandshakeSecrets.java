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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.KeccakDigest;

/**
 * Encapsulates the secrets generated during the RLPx crypto handshake, and offers a facility for
 * updating some values as messages are exchanged during the lifetime of the connection.
 *
 * <p>The following secret materials are modelled:
 *
 * <ul>
 *   <li><strong>AES secret:</strong> shared secret used to cipher and decipher message payloads.
 *   <li><strong>MAC secret:</strong> shared secret used to update ingress and egress MACs as
 *       messages are exchanged.
 *   <li><strong>Token:</strong> identifies this session, currently unused.
 *   <li><strong>Ingress MAC:</strong> continuously-updating MAC for received bytes.
 *   <li><strong>Egress MAC:</strong> continuously-updating MAC for sent bytes.
 * </ul>
 *
 * @see <a href="https://github.com/ethereum/devp2p/blob/master/rlpx.md#encrypted-handshake">RLPx
 *     Encrypted Handshake</a>
 */
public class HandshakeSecrets {
  private final byte[] aesSecret;
  private final byte[] macSecret;
  private final byte[] token;
  private final KeccakDigest egressMac = new KeccakDigest(Bytes32.SIZE * 8);
  private final KeccakDigest ingressMac = new KeccakDigest(Bytes32.SIZE * 8);

  /**
   * Creates an instance with empty MACs.
   *
   * @param aesSecret The AES shared secret.
   * @param macSecret The MAC shared secret.
   * @param token The session token.
   */
  public HandshakeSecrets(final byte[] aesSecret, final byte[] macSecret, final byte[] token) {
    checkArgument(aesSecret.length == Bytes32.SIZE, "aes secret must be exactly 32 bytes long");
    checkArgument(macSecret.length == Bytes32.SIZE, "mac secret must be exactly 32 bytes long");
    checkArgument(token.length == Bytes32.SIZE, "token must be exactly 32 bytes long");

    this.aesSecret = aesSecret;
    this.macSecret = macSecret;
    this.token = token;
  }

  /**
   * Updates the egress mac with the provided bytes.
   *
   * @param bytes The bytes of the outgoing message.
   * @return Returns this instance for fluent chaining.
   */
  public HandshakeSecrets updateEgress(final byte[] bytes) {
    egressMac.update(bytes, 0, bytes.length);
    return this;
  }

  /**
   * Updates the ingress mac with the provided bytes.
   *
   * @param bytes The bytes of the incoming message.
   * @return Returns this instance for fluent chaining.
   */
  public HandshakeSecrets updateIngress(final byte[] bytes) {
    ingressMac.update(bytes, 0, bytes.length);
    return this;
  }

  /**
   * Returns the AES shared secret.
   *
   * @return The AES shared secret.
   */
  public byte[] getAesSecret() {
    return aesSecret;
  }

  /**
   * Returns the MAC shared secret.
   *
   * @return The MAC shared secret.
   */
  public byte[] getMacSecret() {
    return macSecret;
  }

  /**
   * Returns the token that identifies a session (unused).
   *
   * @return The token.
   */
  public byte[] getToken() {
    return token;
  }

  /**
   * Returns a snapshot of the current egress MAC, without finalising the underlying digest.
   *
   * @return Snapshot of the current egress MAC.
   */
  public byte[] getEgressMac() {
    return snapshot(egressMac);
  }

  /**
   * Returns a snapshot of the current ingress MAC, without finalising the underlying digest.
   *
   * @return Snapshot of the current ingress MAC.
   */
  public byte[] getIngressMac() {
    return snapshot(ingressMac);
  }

  /**
   * TODO: It's not wise to print secrets. Maybe print only the first and last 8 bytes (ellipsize
   * the middle). That might be enough for testing.
   */
  @Override
  public String toString() {
    return "HandshakeSecrets{"
        + "aesSecret="
        + Bytes.wrap(aesSecret)
        + ", macSecret="
        + Bytes.wrap(macSecret)
        + ", token="
        + Bytes.wrap(token)
        + ", egressMac="
        + Bytes.wrap(snapshot(egressMac))
        + ", ingressMac="
        + Bytes.wrap(snapshot(ingressMac))
        + '}';
  }

  private static byte[] snapshot(final KeccakDigest digest) {
    final byte[] out = new byte[Bytes32.SIZE];
    new KeccakDigest(digest).doFinal(out, 0);
    return out;
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass") // checked in delegated method
  @Override
  public boolean equals(final Object obj) {
    return equals(obj, false);
  }

  /**
   * Performs an equals comparison with the ability to flip the MAC comparison, catering for
   * scenarios where we want to compare the handshake secrets on opposing ends of a channel.
   *
   * @param o The object whose equality to test with this.
   * @param flipMacs Whether the egress MAC should be compared against the ingress MAC, and
   *     viceversa.
   * @return Whether both objects are equal or not.
   */
  public boolean equals(final Object o, final boolean flipMacs) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HandshakeSecrets that = (HandshakeSecrets) o;
    final KeccakDigest vsEgress = flipMacs ? that.ingressMac : that.egressMac;
    final KeccakDigest vsIngress = flipMacs ? that.egressMac : that.ingressMac;
    return Arrays.equals(aesSecret, that.aesSecret)
        && Arrays.equals(macSecret, that.macSecret)
        && Arrays.equals(token, that.token)
        && Arrays.equals(snapshot(egressMac), snapshot(vsEgress))
        && Arrays.equals(snapshot(ingressMac), snapshot(vsIngress));
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        Arrays.hashCode(aesSecret),
        Arrays.hashCode(macSecret),
        Arrays.hashCode(token),
        egressMac,
        ingressMac);
  }
}

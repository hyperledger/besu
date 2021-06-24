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
package org.hyperledger.besu.ethereum.p2p.peers;

import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.plugin.data.EnodeURL;

import org.apache.tuweni.bytes.Bytes;

public interface Peer extends PeerId {

  /**
   * ENode URL of this peer.
   *
   * @return The enode representing the location of this peer.
   */
  EnodeURL getEnodeURL();

  /**
   * Generates a random peer ID in a secure manner.
   *
   * @return The generated peer ID.
   */
  static Bytes randomId() {
    final byte[] id = new byte[EnodeURLImpl.NODE_ID_SIZE];
    SecureRandomProvider.publicSecureRandom().nextBytes(id);
    return Bytes.wrap(id);
  }

  /**
   * Returns this peer's enode URL.
   *
   * @return The enode URL as a String.
   */
  default String getEnodeURLString() {
    return this.getEnodeURL().toString();
  }
}

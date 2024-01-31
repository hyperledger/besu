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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface PeerId {
  /**
   * The ID of the peer, equivalent to its public key. In public Ethereum, the public key is derived
   * from the signatures the peer attaches to certain messages.
   *
   * @return The peer's ID.
   */
  Bytes getId();

  /**
   * The Keccak-256 hash value of the peer's ID. The value may be memoized to avoid recomputation
   * overhead.
   *
   * @return The Keccak-256 hash of the peer's ID.
   */
  Bytes32 keccak256();

  String getLoggableId();
}

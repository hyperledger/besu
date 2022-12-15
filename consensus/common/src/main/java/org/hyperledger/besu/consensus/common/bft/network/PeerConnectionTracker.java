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
package org.hyperledger.besu.consensus.common.bft.network;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;

/** The interface Peer connection tracker. */
public interface PeerConnectionTracker {

  /**
   * Add.
   *
   * @param newConnection the new connection
   */
  void add(final PeerConnection newConnection);

  /**
   * Remove.
   *
   * @param removedConnection the removed connection
   */
  void remove(final PeerConnection removedConnection);
}

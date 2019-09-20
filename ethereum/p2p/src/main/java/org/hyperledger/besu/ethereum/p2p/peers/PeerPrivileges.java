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

public interface PeerPrivileges {

  /**
   * If true, the given peer can connect or remain connected even if the max connection limit or the
   * maximum remote connection limit has been reached or exceeded.
   *
   * @param peer The peer to be checked.
   * @return {@code true} if the peer should be allowed to connect regardless of connection limits.
   */
  boolean canExceedConnectionLimits(final Peer peer);
}

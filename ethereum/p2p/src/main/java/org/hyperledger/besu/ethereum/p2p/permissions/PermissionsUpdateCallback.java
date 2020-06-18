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
package org.hyperledger.besu.ethereum.p2p.permissions;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;

import java.util.List;
import java.util.Optional;

public interface PermissionsUpdateCallback {

  /**
   * onUpdate callback.
   *
   * @param permissionsRestricted True if permissions were narrowed in any way, meaning that
   *     previously permitted peers may no longer be permitted. False indicates that permissions
   *     were made less restrictive, meaning peers that were previously restricted may now be
   *     permitted.
   * @param affectedPeers If non-empty, contains the entire set of peers affected by this
   *     permissions update. If permissions were restricted, this is the list of peers that are no
   *     longer permitted. If permissions were broadened, this is the list of peers that are now
   *     permitted.
   */
  void onUpdate(final boolean permissionsRestricted, final Optional<List<Peer>> affectedPeers);
}

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
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;

public class PeerPermissionsException extends RuntimeException {
  private final Peer peer;
  private final Action action;

  public PeerPermissionsException(final Peer peer, final Action action) {
    super(
        String.format(
            "Permission for action %s denied to peer %s", action.name(), peer.getEnodeURL()));
    this.peer = peer;
    this.action = action;
  }

  public Peer getPeer() {
    return peer;
  }

  public Action getAction() {
    return action;
  }
}

/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.plugin.services.p2p;

import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.p2p.Capability;
import org.hyperledger.besu.plugin.data.p2p.Message;
import org.hyperledger.besu.plugin.data.p2p.Peer;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.BesuService;

import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;

/** Service to enable and disable P2P service. */
@Unstable
public interface P2PService extends BesuService {

  /** Enables P2P discovery. */
  void enableDiscovery();

  /** Disables P2P discovery. */
  void disableDiscovery();

  /**
   * Returns the current number of connected peers.
   *
   * @return the number of connected peers
   */
  int getPeerCount();
}

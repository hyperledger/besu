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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.plugin.services.p2p.P2PService;

/** Service to enable and disable P2P discovery. */
public class P2PServiceImpl implements P2PService {

  private final P2PNetwork p2PNetwork;

  /**
   * Creates a new P2PServiceImpl.
   *
   * @param p2PNetwork the P2P network to enable and disable.
   */
  public P2PServiceImpl(final P2PNetwork p2PNetwork) {
    this.p2PNetwork = p2PNetwork;
  }

  /** Enables P2P discovery. */
  @Override
  public void enableDiscovery() {
    p2PNetwork.start();
  }

  @Override
  public void disableDiscovery() {
    p2PNetwork.stop();
  }
}

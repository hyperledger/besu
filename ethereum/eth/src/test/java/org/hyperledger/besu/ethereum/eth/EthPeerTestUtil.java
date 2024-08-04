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
package org.hyperledger.besu.ethereum.eth;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;

import org.apache.tuweni.bytes.Bytes;

public class EthPeerTestUtil {

  public static EnodeURLImpl.Builder enodeBuilder() {
    return EnodeURLImpl.builder().ipAddress("127.0.0.1").useDefaultPorts().nodeId(Peer.randomId());
  }

  public static Peer createPeer(final Bytes nodeId) {
    return DefaultPeer.fromEnodeURL(enodeBuilder().nodeId(nodeId).build());
  }
}

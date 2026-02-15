/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery;

import java.net.InetAddress;
import java.util.Optional;

public interface NodeIdentifier {
  InetAddress getInetAddress();

  Optional<Integer> getTcpListeningPort();

  Optional<Integer> getUdpDiscoveryPort();

  static boolean isSameListeningEndpoint(final NodeIdentifier node1, final NodeIdentifier node2) {
    return node1 != null
        && node2 != null
        && node1.getInetAddress().equals(node2.getInetAddress())
        && node1.getTcpListeningPort().equals(node2.getTcpListeningPort());
  }
}

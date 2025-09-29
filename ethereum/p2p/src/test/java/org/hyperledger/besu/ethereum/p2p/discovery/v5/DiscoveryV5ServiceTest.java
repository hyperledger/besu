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
package org.hyperledger.besu.ethereum.p2p.discovery.v5;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.Test;

final class DiscoveryV5ServiceTest {

  @Test
  void startsAndExposesLocalEnr() {
    // Generate a Besu keypair
    SignatureAlgorithm alg = SignatureAlgorithmFactory.getInstance();
    KeyPair besuKey = alg.generateKeyPair();

    // Build config (bind to loopback on an ephemeral port)
    DiscoveryV5Config cfg =
        DiscoveryV5Config.builder()
            .nodeKey(besuKey)
            .udpBindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            .bootEnrs(List.of())
            .build();

    // Start service and assert ENR/nodeId present
    DiscoveryV5Service svc = new DiscoveryV5Service(cfg);
    try {
      svc.start();
      assertThat(svc.localEnr()).isPresent();
      assertThat(svc.localNodeId()).isPresent();
    } finally {
      svc.close();
    }
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class P2PServiceImplTest {

  @Mock private P2PNetwork p2PNetwork;

  private P2PServiceImpl p2PService;

  @BeforeEach
  void setUp() {
    p2PService = new P2PServiceImpl(p2PNetwork);
  }

  @Test
  void shouldReturnZeroWhenNoPeersConnected() {
    when(p2PNetwork.getPeerCount()).thenReturn(0);

    int peerCount = p2PService.getPeerCount();
    assertThat(peerCount).isEqualTo(0);
  }

  @Test
  void shouldReturnCorrectPeerCountWhenPeersConnected() {
    when(p2PNetwork.getPeerCount()).thenReturn(3);

    int peerCount = p2PService.getPeerCount();
    assertThat(peerCount).isEqualTo(3);
  }

  @Test
  void shouldReturnOneWhenSinglePeerConnected() {
    when(p2PNetwork.getPeerCount()).thenReturn(1);

    int peerCount = p2PService.getPeerCount();
    assertThat(peerCount).isEqualTo(1);
  }

  @Test
  void shouldDelegateToP2PNetwork() {
    when(p2PNetwork.getPeerCount()).thenReturn(2);

    int peerCount = p2PService.getPeerCount();
    assertThat(peerCount).isEqualTo(2);
  }

  @Test
  void shouldReturnCorrectCountAfterPeerChanges() {
    // First call - 2 peers
    when(p2PNetwork.getPeerCount()).thenReturn(2);
    int peerCount1 = p2PService.getPeerCount();
    assertThat(peerCount1).isEqualTo(2);

    // Second call - 1 peer
    when(p2PNetwork.getPeerCount()).thenReturn(1);
    int peerCount2 = p2PService.getPeerCount();
    assertThat(peerCount2).isEqualTo(1);

    // Third call - 3 peers
    when(p2PNetwork.getPeerCount()).thenReturn(3);
    int peerCount3 = p2PService.getPeerCount();
    assertThat(peerCount3).isEqualTo(3);
  }
}

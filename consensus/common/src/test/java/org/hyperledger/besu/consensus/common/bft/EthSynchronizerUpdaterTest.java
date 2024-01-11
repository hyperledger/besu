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
package org.hyperledger.besu.consensus.common.bft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthSynchronizerUpdaterTest {

  @Mock private EthPeers ethPeers;
  @Mock private EthPeer ethPeer;

  @Mock private ChainState chainState;

  @Test
  public void ethPeerIsMissingResultInNoUpdate() {
    when(ethPeers.peer(any(PeerConnection.class))).thenReturn(null);

    final EthSynchronizerUpdater updater = new EthSynchronizerUpdater(ethPeers);

    updater.updatePeerChainState(1, mock(PeerConnection.class));

    verifyNoInteractions(ethPeer);
  }

  @Test
  public void chainStateUpdateIsAttemptedIfEthPeerExists() {
    when(ethPeers.peer(any(PeerConnection.class))).thenReturn(ethPeer);
    when(ethPeer.chainState()).thenReturn(chainState);

    final EthSynchronizerUpdater updater = new EthSynchronizerUpdater(ethPeers);

    final long suppliedChainHeight = 6L;
    updater.updatePeerChainState(suppliedChainHeight, mock(PeerConnection.class));
    verify(chainState, times(1)).updateHeightEstimate(eq(suppliedChainHeight));
  }
}

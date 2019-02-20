/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;

import java.net.SocketAddress;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthSynchronizerUpdaterTest {

  @Mock private EthPeers ethPeers;
  @Mock private EthPeer ethPeer;

  @Mock private ChainState chainState;

  @Test
  public void ethPeerIsMissingResultInNoUpdate() {
    when(ethPeers.peer(any())).thenReturn(null);

    final EthSynchronizerUpdater updater = new EthSynchronizerUpdater(ethPeers);

    updater.updatePeerChainState(1, createAnonymousPeerConnection());

    verifyZeroInteractions(ethPeer);
  }

  @Test
  public void chainStateUpdateIsAttemptedIfEthPeerExists() {
    when(ethPeers.peer(any())).thenReturn(ethPeer);
    when(ethPeer.chainState()).thenReturn(chainState);

    final EthSynchronizerUpdater updater = new EthSynchronizerUpdater(ethPeers);

    final long suppliedChainHeight = 6L;
    updater.updatePeerChainState(suppliedChainHeight, createAnonymousPeerConnection());
    verify(chainState, times(1)).updateHeightEstimate(eq(suppliedChainHeight));
  }

  private PeerConnection createAnonymousPeerConnection() {
    return new PeerConnection() {
      @Override
      public void send(final Capability capability, final MessageData message)
          throws PeerNotConnected {}

      @Override
      public Set<Capability> getAgreedCapabilities() {
        return null;
      }

      @Override
      public PeerInfo getPeer() {
        return new PeerInfo(0, null, null, 0, null);
      }

      @Override
      public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {}

      @Override
      public void disconnect(final DisconnectReason reason) {}

      @Override
      public SocketAddress getLocalAddress() {
        return null;
      }

      @Override
      public SocketAddress getRemoteAddress() {
        return null;
      }
    };
  }
}

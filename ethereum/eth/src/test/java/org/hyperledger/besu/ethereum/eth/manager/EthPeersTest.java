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
package org.hyperledger.besu.ethereum.eth.manager;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.NoAvailablePeersException;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.PeerDisconnectedException;
import org.hyperledger.besu.ethereum.eth.messages.NodeDataMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

public class EthPeersTest {

  private EthProtocolManager ethProtocolManager;
  private EthPeers ethPeers;
  private final PeerRequest peerRequest = mock(PeerRequest.class);
  private final RequestManager.ResponseStream responseStream =
      mock(RequestManager.ResponseStream.class);

  @Before
  public void setup() throws Exception {
    when(peerRequest.sendRequest(any())).thenReturn(responseStream);
    ethProtocolManager = EthProtocolManagerTestUtil.create();
    ethPeers = ethProtocolManager.ethContext().getEthPeers();
  }

  @Test
  public void comparesPeersWithHeightAndTd() {
    // Set peerA with better height, lower td
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(50), 20)
            .getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(100), 10)
            .getEthPeer();

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isLessThan(0);

    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerA, peerB)).isLessThan(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerB, peerA)).isGreaterThan(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);

    assertThat(ethProtocolManager.ethContext().getEthPeers().bestPeer()).contains(peerB);
    assertThat(ethProtocolManager.ethContext().getEthPeers().bestPeerWithHeightEstimate())
        .contains(peerB);
  }

  @Test
  public void comparesPeersWithTdAndNoHeight() {
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(
                ethProtocolManager, Difficulty.of(100), OptionalLong.empty())
            .getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(
                ethProtocolManager, Difficulty.of(50), OptionalLong.empty())
            .getEthPeer();

    // Sanity check
    assertThat(peerA.chainState().getEstimatedHeight()).isEqualTo(0);
    assertThat(peerB.chainState().getEstimatedHeight()).isEqualTo(0);

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isEqualTo(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isGreaterThan(0);

    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerB, peerA)).isLessThan(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.HEAVIEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);

    assertThat(ethProtocolManager.ethContext().getEthPeers().bestPeer()).contains(peerA);
    assertThat(ethProtocolManager.ethContext().getEthPeers().bestPeerWithHeightEstimate())
        .isEmpty();
  }

  @Test
  public void shouldExecutePeerRequestImmediatelyWhenPeerIsAvailable() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastBusyPeerForRequest() throws Exception {
    final RespondingEthPeer idlePeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer workingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useRequestSlot(workingPeer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(idlePeer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldUseLeastRecentlyUsedPeerWhenBothHaveSameNumberOfOutstandingRequests()
      throws Exception {
    final RespondingEthPeer mostRecentlyUsedPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer leastRecentlyUsedPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useRequestSlot(mostRecentlyUsedPeer.getEthPeer());
    freeUpCapacity(mostRecentlyUsedPeer.getEthPeer());

    assertThat(leastRecentlyUsedPeer.getEthPeer().outstandingRequests())
        .isEqualTo(mostRecentlyUsedPeer.getEthPeer().outstandingRequests());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verify(peerRequest).sendRequest(leastRecentlyUsedPeer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldFailWithNoAvailablePeersWhenNoPeersConnected() {
    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 10, Optional.empty());

    verifyNoInteractions(peerRequest);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWhenNoPeerWithSufficientHeight() {
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 100);
    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 200, Optional.empty());

    verifyNoInteractions(peerRequest);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWhenAllPeersWithSufficientHeightHaveDisconnected() throws Exception {
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 100);
    final RespondingEthPeer suitablePeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useAllAvailableCapacity(suitablePeer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 200, Optional.empty());

    verifyNoInteractions(peerRequest);
    assertNotDone(pendingRequest);

    suitablePeer.disconnect(DisconnectReason.TOO_MANY_PEERS);
    assertRequestFailure(pendingRequest, NoAvailablePeersException.class);
  }

  @Test
  public void shouldFailWithPeerNotConnectedIfPeerRequestThrows() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    when(peerRequest.sendRequest(peer.getEthPeer())).thenThrow(new PeerNotConnected("Oh dear"));
    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 100, Optional.empty());

    assertRequestFailure(pendingRequest, PeerDisconnectedException.class);
  }

  @Test
  public void shouldDelayExecutionUntilPeerHasCapacity() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useAllAvailableCapacity(peer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verifyNoInteractions(peerRequest);

    freeUpCapacity(peer.getEthPeer());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldDelayExecutionUntilPeerWithSufficientHeightHasCapacity() throws Exception {
    // Create a peer that has available capacity but not the required height
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 10);

    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useAllAvailableCapacity(peer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verifyNoInteractions(peerRequest);

    freeUpCapacity(peer.getEthPeer());

    verify(peerRequest).sendRequest(peer.getEthPeer());
    assertRequestSuccessful(pendingRequest);
  }

  @Test
  public void shouldNotExecuteAbortedRequest() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    useAllAvailableCapacity(peer.getEthPeer());

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 100, Optional.empty());
    verifyNoInteractions(peerRequest);

    pendingRequest.abort();

    freeUpCapacity(peer.getEthPeer());

    verifyNoInteractions(peerRequest);
    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  // We had a bug where if a peer was busy when it was disconnected, pending peer requests that were
  // *explicitly* assigned to that peer would never be attempted and thus never completed
  @Test
  public void shouldFailRequestWithBusyDisconnectedAssignedPeer() throws Exception {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final EthPeer ethPeer = peer.getEthPeer();
    useAllAvailableCapacity(ethPeer);

    final PendingPeerRequest pendingRequest =
        ethPeers.executePeerRequest(peerRequest, 100, Optional.of(ethPeer));

    ethPeer.disconnect(DisconnectReason.UNKNOWN);
    ethPeers.registerDisconnect(ethPeer.getConnection());

    assertRequestFailure(pendingRequest, CancellationException.class);
  }

  @Test
  public void toString_hasExpectedInfo() {
    assertThat(ethPeers.toString()).isEqualTo("0 EthPeers {}");

    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(50), 20)
            .getEthPeer();
    ethPeers.registerConnection(peerA.getConnection(), Collections.emptyList());
    assertThat(ethPeers.toString()).contains("1 EthPeers {");
    assertThat(ethPeers.toString()).contains(peerA.getShortNodeId());
  }

  private void freeUpCapacity(final EthPeer ethPeer) {
    ethPeers.dispatchMessage(ethPeer, new EthMessage(ethPeer, NodeDataMessage.create(emptyList())));
  }

  private void useAllAvailableCapacity(final EthPeer peer) throws PeerNotConnected {
    while (peer.hasAvailableRequestCapacity()) {
      useRequestSlot(peer);
    }
    assertThat(peer.hasAvailableRequestCapacity()).isFalse();
  }

  private void useRequestSlot(final EthPeer peer) throws PeerNotConnected {
    peer.getNodeData(singletonList(Hash.ZERO));
  }

  @SuppressWarnings("unchecked")
  private void assertRequestSuccessful(final PendingPeerRequest pendingRequest) {
    final Consumer<RequestManager.ResponseStream> onSuccess = mock(Consumer.class);
    pendingRequest.then(onSuccess, error -> fail("Request should have executed", error));
    verify(onSuccess).accept(any());
  }

  @SuppressWarnings("unchecked")
  private void assertRequestFailure(
      final PendingPeerRequest pendingRequest, final Class<? extends Throwable> reason) {
    final Consumer<Throwable> errorHandler = mock(Consumer.class);
    pendingRequest.then(responseStream -> fail("Should not have performed request"), errorHandler);

    verify(errorHandler).accept(any(reason));
  }

  @SuppressWarnings("unchecked")
  private void assertNotDone(final PendingPeerRequest pendingRequest) {
    final Consumer<RequestManager.ResponseStream> onSuccess = mock(Consumer.class);
    final Consumer<Throwable> onError = mock(Consumer.class);
    pendingRequest.then(onSuccess, onError);

    verifyNoInteractions(onSuccess);
    verifyNoInteractions(onError);
  }
}

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
package tech.pegasys.pantheon.ethereum.p2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InsufficientPeersPermissioningProviderTest {
  @Mock private P2PNetwork p2pNetwork;
  private final EnodeURL SELF_ENODE =
      EnodeURL.fromString(
          "enode://00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001@192.168.0.1:30303");
  private final EnodeURL ENODE_2 =
      EnodeURL.fromString(
          "enode://00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002@192.168.0.2:30303");
  private final EnodeURL ENODE_3 =
      EnodeURL.fromString(
          "enode://00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003@192.168.0.3:30303");
  private final EnodeURL ENODE_4 =
      EnodeURL.fromString(
          "enode://00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004@192.168.0.4:30303");
  private final EnodeURL ENODE_5 =
      EnodeURL.fromString(
          "enode://00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000005@192.168.0.5:30303");

  @Test
  public void noResultWhenNoBootnodes() {
    final Collection<EnodeURL> bootnodes = Collections.emptyList();

    when(p2pNetwork.getPeers()).thenReturn(Collections.emptyList());

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    assertThat(provider.isPermitted(SELF_ENODE, ENODE_2)).isEmpty();
  }

  @Test
  public void noResultWhenOtherConnections() {
    final PeerConnection neverMatchPeerConnection = mock(PeerConnection.class);
    when(neverMatchPeerConnection.isRemoteEnode(any())).thenReturn(false);
    when(p2pNetwork.getPeers()).thenReturn(Collections.singletonList(neverMatchPeerConnection));

    final Collection<EnodeURL> bootnodes = Collections.singletonList(ENODE_2);

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    assertThat(provider.isPermitted(SELF_ENODE, ENODE_3)).isEmpty();
    assertThat(provider.isPermitted(SELF_ENODE, ENODE_2)).isEmpty();
  }

  @Test
  public void allowsConnectionIfBootnodeAndNoConnections() {
    final Collection<EnodeURL> bootnodes = Collections.singletonList(ENODE_2);

    when(p2pNetwork.getPeers()).thenReturn(Collections.emptyList());

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    assertThat(provider.isPermitted(SELF_ENODE, ENODE_2)).contains(true);
    assertThat(provider.isPermitted(SELF_ENODE, ENODE_3)).isEmpty();
  }

  @Test
  public void allowsConnectionIfBootnodeAndOnlyBootnodesConnected() {
    final Collection<EnodeURL> bootnodes = Collections.singletonList(ENODE_2);

    final PeerConnection bootnodeMatchPeerConnection = mock(PeerConnection.class);
    when(bootnodeMatchPeerConnection.isRemoteEnode(ENODE_2)).thenReturn(true);
    when(p2pNetwork.getPeers()).thenReturn(Collections.singletonList(bootnodeMatchPeerConnection));

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    assertThat(provider.isPermitted(SELF_ENODE, ENODE_2)).contains(true);
    assertThat(provider.isPermitted(SELF_ENODE, ENODE_3)).isEmpty();
  }

  private PeerConnection peerConnectionMatching(final EnodeURL enode) {
    final PeerConnection pc = mock(PeerConnection.class);
    when(pc.isRemoteEnode(enode)).thenReturn(true);
    when(pc.isRemoteEnode(not(eq(enode)))).thenReturn(false);
    return pc;
  }

  @Test
  public void firesUpdateWhenDisconnectLastNonBootnode() {
    final Collection<EnodeURL> bootnodes = Collections.singletonList(ENODE_2);
    final Collection<PeerConnection> pcs =
        Arrays.asList(
            peerConnectionMatching(ENODE_2),
            peerConnectionMatching(ENODE_3),
            peerConnectionMatching(ENODE_4));
    when(p2pNetwork.getPeers()).thenReturn(pcs);

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    final ArgumentCaptor<DisconnectCallback> callbackCaptor =
        ArgumentCaptor.forClass(DisconnectCallback.class);
    verify(p2pNetwork).subscribeDisconnect(callbackCaptor.capture());
    final DisconnectCallback disconnectCallback = callbackCaptor.getValue();

    final Runnable updatePermsCallback = mock(Runnable.class);

    provider.subscribeToUpdates(updatePermsCallback);

    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_3), null, true);
    verify(updatePermsCallback, times(0)).run();
    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_4), null, true);
    verify(updatePermsCallback, times(1)).run();
    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_2), null, true);
    verify(updatePermsCallback, times(1)).run();
  }

  @Test
  public void firesUpdateWhenNonBootnodeConnects() {
    final Collection<EnodeURL> bootnodes = Arrays.asList(ENODE_2, ENODE_3);
    final Collection<PeerConnection> pcs = Collections.emptyList();

    when(p2pNetwork.getPeers()).thenReturn(pcs);

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<PeerConnection>> callbackCaptor =
        ArgumentCaptor.forClass(Consumer.class);

    verify(p2pNetwork).subscribeConnect(callbackCaptor.capture());
    final Consumer<PeerConnection> connectCallback = callbackCaptor.getValue();

    final Runnable updatePermsCallback = mock(Runnable.class);

    provider.subscribeToUpdates(updatePermsCallback);

    connectCallback.accept(peerConnectionMatching(ENODE_2));
    verify(updatePermsCallback, times(0)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_3));
    verify(updatePermsCallback, times(0)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_4));
    verify(updatePermsCallback, times(1)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_5));
    verify(updatePermsCallback, times(1)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_3));
    verify(updatePermsCallback, times(1)).run();
  }

  @Test
  public void firesUpdateWhenGettingAndLosingConnection() {
    final Collection<EnodeURL> bootnodes = Arrays.asList(ENODE_2, ENODE_3);
    final Collection<PeerConnection> pcs = Collections.emptyList();

    when(p2pNetwork.getPeers()).thenReturn(pcs);

    final InsufficientPeersPermissioningProvider provider =
        new InsufficientPeersPermissioningProvider(p2pNetwork, SELF_ENODE, bootnodes);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<PeerConnection>> connectCallbackCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(p2pNetwork).subscribeConnect(connectCallbackCaptor.capture());
    final Consumer<PeerConnection> connectCallback = connectCallbackCaptor.getValue();

    final ArgumentCaptor<DisconnectCallback> disconnectCallbackCaptor =
        ArgumentCaptor.forClass(DisconnectCallback.class);
    verify(p2pNetwork).subscribeDisconnect(disconnectCallbackCaptor.capture());
    final DisconnectCallback disconnectCallback = disconnectCallbackCaptor.getValue();

    final Runnable updatePermsCallback = mock(Runnable.class);

    provider.subscribeToUpdates(updatePermsCallback);

    connectCallback.accept(peerConnectionMatching(ENODE_2));
    verify(updatePermsCallback, times(0)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_3));
    verify(updatePermsCallback, times(0)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_4));
    verify(updatePermsCallback, times(1)).run();
    connectCallback.accept(peerConnectionMatching(ENODE_5));
    verify(updatePermsCallback, times(1)).run();
    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_2), null, true);
    verify(updatePermsCallback, times(1)).run();
    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_4), null, true);
    verify(updatePermsCallback, times(1)).run();
    disconnectCallback.onDisconnect(peerConnectionMatching(ENODE_5), null, true);
    verify(updatePermsCallback, times(2)).run();
  }
}

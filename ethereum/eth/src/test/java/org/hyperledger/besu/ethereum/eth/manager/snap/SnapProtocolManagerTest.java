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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection.PeerSendHandler;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public final class SnapProtocolManagerTest {

  private static final Hash START_HASH =
      Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000");
  private static final Hash END_HASH =
      Hash.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final BonsaiPersistedWorldState mutableWorldState = mock(BonsaiPersistedWorldState.class);
  private final BonsaiWorldStateKeyValueStorage worldStateStorage =
      mock(BonsaiWorldStateKeyValueStorage.class);
  private final EthMessages ethMessages = new EthMessages();

  @Before
  public void setUp() {
    new SnapServer(ethMessages, worldStateArchive);
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(mutableWorldState.getWorldStateStorage()).thenReturn(worldStateStorage);
  }

  public static SnapProtocolManager create(final WorldStateArchive worldStateArchive) {
    EthPeers peers = new EthPeers(EthProtocol.NAME, TestClock.fixed(), new NoOpMetricsSystem());
    EthMessages snapMessages = new EthMessages();
    return new SnapProtocolManager(Collections.emptyList(), peers, snapMessages, worldStateArchive);
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final SnapProtocolManager snapManager, final PeerSendHandler onSend) {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(SnapProtocol.SNAP1));
    final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
    snapManager.handleNewConnection(peer);
    return peer;
  }

  @Test
  public void disconnectOnUnsolicitedMessage() {
    try (final SnapProtocolManager snapManager = create(worldStateArchive)) {
      final MessageData messageData =
          AccountRangeMessage.create(new TreeMap<>(), new ArrayList<>())
              .wrapMessageData(BigInteger.ONE);
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(snapManager, (cap, msg, conn) -> {});
      assertThat(peer.isDisconnected()).isFalse();
      snapManager.processMessage(SnapProtocol.SNAP1, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }

  @Test
  public void respondToGetAccountRange() throws ExecutionException, InterruptedException {

    final BlockDataGenerator gen = new BlockDataGenerator();
    final DefaultWorldStateArchive inMemoryWorldStateArchive = createInMemoryWorldStateArchive();
    final List<Address> accounts = new ArrayList<>();
    accounts.add(Address.extract(START_HASH));
    accounts.add(Address.extract(END_HASH));
    final List<Block> blocks =
        gen.blockSequence(10, inMemoryWorldStateArchive, accounts, new ArrayList<>());

    doAnswer(
            invocation ->
                inMemoryWorldStateArchive
                    .getWorldStateStorage()
                    .getAccountStateTrieNode(
                        invocation.getArgument(0, Bytes.class),
                        invocation.getArgument(1, Bytes32.class))
                    .map(Bytes::wrap))
        .when(worldStateStorage)
        .getAccountStateTrieNode(any(), any());

    doAnswer(
            invocation ->
                inMemoryWorldStateArchive.getAccountProofRelatedNodes(
                    invocation.getArgument(0, Hash.class), invocation.getArgument(1, Hash.class)))
        .when(worldStateArchive)
        .getAccountProofRelatedNodes(any(), any());

    final Hash rootHash = blocks.get(blocks.size() - 1).getHeader().getStateRoot();

    final CompletableFuture<Void> done = new CompletableFuture<>();
    try (final SnapProtocolManager snapManager = create(worldStateArchive)) {
      final MessageData messageData =
          GetAccountRangeMessage.create(rootHash, START_HASH, END_HASH, BigInteger.valueOf(10000))
              .wrapMessageData(BigInteger.TEN);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            assertThat(message.getCode()).isEqualTo(SnapV1.ACCOUNT_RANGE);
            done.complete(null);
          };
      final PeerConnection peer = setupPeerWithoutStatusExchange(snapManager, onSend);
      snapManager.processMessage(SnapProtocol.SNAP1, new DefaultMessage(peer, messageData));
      done.get();
    }
  }
}

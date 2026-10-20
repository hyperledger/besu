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
package org.hyperledger.besu.ethereum.eth.sync.snap2.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocolVersion;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockAccessListsMessageData;
import org.hyperledger.besu.ethereum.eth.messages.EthProtocolMessages;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockAccessListsMessageData;
import org.hyperledger.besu.ethereum.eth.messages.snap.BlockAccessListsMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV2;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestFailedException;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestFailedException.Reason;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Protocol;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.AccountChanges;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BalanceChange;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BalFetcherTest {

  private final EthPeer snap2Eth71 =
      peer(SnapProtocol.SNAP2, EthProtocolVersion.V71.getCapability());
  private final EthPeer snap1Eth71 =
      peer(SnapProtocol.SNAP1, EthProtocolVersion.V71.getCapability());
  private final EthPeer snap2Eth68 =
      peer(SnapProtocol.SNAP2, EthProtocolVersion.V68.getCapability());
  private final FakeSender sender = new FakeSender();
  private final List<BlockHeader> headers = headersWithLists(12);
  private final List<Duration> pauses = new ArrayList<>();
  private final List<Runnable> paused = new ArrayList<>();

  @Test
  void fetchesTheListsFromSnap2Peers() {
    sender.withoutLists.add(snap1Eth71);

    assertThat(fetcher(5).fetch(headers)).isCompletedWithValue(lists(headers));
  }

  @Test
  void fetchesTheListsFromEth71Peers() {
    sender.withoutLists.addAll(List.of(snap2Eth71, snap2Eth68));

    assertThat(fetcher(5).fetch(headers)).isCompletedWithValue(lists(headers));
  }

  @Test
  void givesUpWhenNoPeerHasTheLists() {
    sender.withoutLists.addAll(sender.peers);

    final CompletableFuture<List<Bytes>> result = fetcher(1).fetch(headers);
    assertThat(pauses).containsExactly(Duration.ofSeconds(1));
    resume();

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join)
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no peer served the block access list of block 1");
  }

  @Test
  void asksThePeersThatLackedTheListsAgainAfterFiveEmptyRounds() {
    final List<BlockHeader> batch = headers.subList(0, 4);
    sender.withoutLists.addAll(sender.peers);

    final CompletableFuture<List<Bytes>> result = fetcher(10).fetch(batch);
    // each peer lacks the lists in turn, then no peer is left to ask
    for (int round = 1; round <= 4; round++) {
      resume();
    }
    sender.withoutLists.clear();
    assertThat(result).isNotDone();
    resume();

    assertThat(result).isCompletedWithValue(lists(batch));
    assertThat(pauses).extracting(Duration::toSeconds).containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void rejectsAListThatDoesNotMatchItsHeader() {
    final BlockHeader header = headers.getFirst();
    final BalFetcher.BalRequest request = new BalFetcher.BalRequest(List.of(header), 1 << 20);

    assertThat(request.decode(response(Protocol.ETH, List.of(Optional.of(bal(1)))), snap1Eth71))
        .containsEntry(header.getHash(), bal(1).encode());
    assertThatThrownBy(
            () -> request.decode(response(Protocol.ETH, List.of(Optional.of(bal(2)))), snap1Eth71))
        .isInstanceOf(SyncRequest.InvalidResponseException.class)
        .hasMessageContaining("does not match the header of block 1");
  }

  @Test
  void asksOnlyPeersWithSnap2OrEth71() {
    final BalFetcher.BalRequest request = new BalFetcher.BalRequest(headers, 1024);

    assertThat(request.canServe(snap2Eth68)).isTrue();
    assertThat(request.canServe(snap1Eth71)).isTrue();
    assertThat(request.canServe(peer(SnapProtocol.SNAP1, EthProtocolVersion.V68.getCapability())))
        .isFalse();
  }

  @Test
  void asksOverSnap2WhenThePeerHasIt() {
    final BalFetcher.BalRequest request = new BalFetcher.BalRequest(headers, 1024);

    assertThat(request.protocolFor(snap2Eth71)).isEqualTo(Protocol.SNAP2);
    assertThat(request.message(snap2Eth71).getCode()).isEqualTo(SnapV2.GET_BLOCK_ACCESS_LISTS);
    assertThat(request.protocolFor(snap1Eth71)).isEqualTo(Protocol.ETH);
    assertThat(request.message(snap1Eth71).getCode())
        .isEqualTo(EthProtocolMessages.GET_BLOCK_ACCESS_LISTS);
  }

  private BalFetcher fetcher(final int rounds) {
    return new BalFetcher(
        sender,
        4,
        1 << 20,
        rounds,
        pause -> {
          pauses.add(pause);
          return paused::add;
        });
  }

  // runs the rounds waiting for the end of their pause
  private void resume() {
    final List<Runnable> tasks = List.copyOf(paused);
    paused.clear();
    tasks.forEach(Runnable::run);
  }

  // a list with one balance change, a different one for each block
  private static BlockAccessList bal(final long block) {
    return new BlockAccessList(
        List.of(
            new AccountChanges(
                Address.fromHexString("0x01"),
                List.of(),
                List.of(),
                List.of(new BalanceChange(0, Wei.of(block))),
                List.of(),
                List.of())));
  }

  private static List<BlockHeader> headersWithLists(final int count) {
    final List<BlockHeader> headers = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      headers.add(
          new BlockHeaderTestFixture().number(i).balHash(Hash.hash(bal(i).encode())).buildHeader());
    }
    return headers;
  }

  private static List<Bytes> lists(final List<BlockHeader> headers) {
    return headers.stream().map(header -> bal(header.getNumber()).encode()).toList();
  }

  // a response as requests receive it: snap ones keep their request id, eth ones lose it
  private static MessageData response(
      final Protocol protocol, final List<Optional<BlockAccessList>> lists) {
    return protocol == Protocol.SNAP2
        ? BlockAccessListsMessage.create(lists).wrapMessageData(BigInteger.ONE)
        : new RawMessage(
            EthProtocolMessages.BLOCK_ACCESS_LISTS,
            BlockAccessListsMessageData.encodeEthResponse(lists));
  }

  private static EthPeer peer(final Capability snap, final Capability eth) {
    final EthPeer peer = mock(EthPeer.class);
    when(peer.getAgreedCapabilities()).thenReturn(Set.of(snap, eth));
    return peer;
  }

  /** Serves the lists of the test blocks on the calling thread, asking the peers in turn. */
  private final class FakeSender implements RequestSender {
    private final List<EthPeer> peers = List.of(snap2Eth71, snap1Eth71, snap2Eth68);
    private final Set<EthPeer> withoutLists = new HashSet<>();
    private int next;

    @Override
    public <T> CompletableFuture<Response<T>> submit(
        final SyncRequest<T> request, final Set<EthPeer> excluded) {
      for (int i = 0; i < peers.size(); i++) {
        final EthPeer peer = peers.get(next++ % peers.size());
        if (!excluded.contains(peer) && request.canServe(peer)) {
          return answer(request, peer);
        }
      }
      return CompletableFuture.failedFuture(
          new RequestFailedException(Reason.NO_PEER, null, "no peer"));
    }

    private <T> CompletableFuture<Response<T>> answer(
        final SyncRequest<T> request, final EthPeer peer) {
      final Protocol protocol = request.protocolFor(peer);
      final Bytes message = request.message(peer).getData();
      final List<Optional<BlockAccessList>> lists = new ArrayList<>();
      if (!withoutLists.contains(peer)) {
        (protocol == Protocol.SNAP2
                ? GetBlockAccessListsMessageData.decodeSnapRequest(message, false)
                : GetBlockAccessListsMessageData.decodeEthRequest(message))
            .forEach(hash -> lists.add(served(hash)));
      }
      try {
        return CompletableFuture.completedFuture(
            new Response<>(request.decode(response(protocol, lists), peer), peer));
      } catch (final SyncRequest.EmptyResponseException e) {
        return CompletableFuture.failedFuture(
            new RequestFailedException(Reason.EMPTY, peer, e.getMessage()));
      }
    }

    @Override
    public <T> CompletableFuture<Response<T>> submitWithRetries(
        final SyncRequest<T> request, final int attempts) {
      return submit(request);
    }

    @Override
    public int peerCount(final Protocol protocol) {
      return peers.size();
    }

    @Override
    public int capacity(final Protocol protocol) {
      return peers.size();
    }

    @Override
    public Executor workers() {
      return Runnable::run;
    }

    // the list of a test block, none for another hash
    private Optional<BlockAccessList> served(final Hash hash) {
      return headers.stream()
          .filter(header -> header.getHash().equals(hash))
          .findFirst()
          .map(header -> bal(header.getNumber()));
    }
  }
}

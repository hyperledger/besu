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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Priority;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

class HeaderFetcherTest {

  private final List<BlockHeader> chain = chain(20);
  private final FakeSender sender = new FakeSender();

  @Test
  void takesAHeaderTheHeadSourceKnows() {
    final BlockHeader header = chain.get(10);

    assertThat(fetcher(knowing(header)).byHash(header.getHash(), Priority.PIVOT))
        .isCompletedWithValue(header);
    assertThat(sender.requests).isEmpty();
  }

  @Test
  void asksAPivotHeaderToPeersCloseToTheTip() {
    final BlockHeader header = chain.get(10);
    final HeaderFetcher fetcher = fetcher(Optional::empty);
    sender.bestPeerHeight = 100;

    assertThat(fetcher.byHash(header.getHash(), Priority.PIVOT)).isCompletedWithValue(header);
    assertThat(fetcher.byHash(header.getHash(), Priority.HEADERS)).isCompletedWithValue(header);
    assertThat(sender.requests.get(0).minimumPeerHeight()).isEqualTo(92);
    assertThat(sender.requests.get(1).minimumPeerHeight()).isZero();
  }

  @Test
  void walksDownTheParentLinks() {
    assertThat(fetcher(Optional::empty).chainDown(chain.get(17), 5, Priority.HEADERS))
        .isCompletedWithValue(chain.subList(5, 18));
    // the 12 headers below the top, 5 per request
    assertThat(sender.messages)
        .extracting(GetBlockHeadersMessage::maxHeaders)
        .containsExactly(5, 5, 2);
  }

  @Test
  void returnsTheTopAloneWhenItIsTheLowest() {
    assertThat(fetcher(Optional::empty).chainDown(chain.get(5), 5, Priority.HEADERS))
        .isCompletedWithValue(List.of(chain.get(5)));
    assertThat(sender.requests).isEmpty();
  }

  private HeaderFetcher fetcher(final ChainHeadSource heads) {
    return new HeaderFetcher(sender, ProtocolScheduleFixture.MAINNET, heads, 5);
  }

  // a source whose only known header is the given one
  private static ChainHeadSource knowing(final BlockHeader header) {
    return new ChainHeadSource() {
      @Override
      public Optional<Hash> headHash() {
        return Optional.of(header.getHash());
      }

      @Override
      public Optional<BlockHeader> knownHeader(final Hash hash) {
        return Optional.of(header).filter(known -> known.getHash().equals(hash));
      }
    };
  }

  // headers 0 to count - 1, each the parent of the next
  private static List<BlockHeader> chain(final int count) {
    final List<BlockHeader> headers = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final BlockHeaderTestFixture fixture = new BlockHeaderTestFixture().number(i);
      if (i > 0) {
        fixture.parentHash(headers.getLast().getHash());
      }
      headers.add(fixture.buildHeader());
    }
    return headers;
  }

  /** Answers the header requests from the test chain, on the calling thread. */
  private final class FakeSender implements RequestSender {
    private final EthPeer peer = mock(EthPeer.class);
    private final List<SyncRequest<?>> requests = new ArrayList<>();
    private final List<GetBlockHeadersMessage> messages = new ArrayList<>();
    private long bestPeerHeight;

    private FakeSender() {
      when(peer.getAgreedCapabilities()).thenReturn(Set.of(EthProtocol.LATEST));
      // header tasks report the height they see to the peer
      when(peer.chainState()).thenReturn(new ChainState());
    }

    @Override
    public <T> CompletableFuture<Response<T>> submit(
        final SyncRequest<T> request, final Set<EthPeer> excluded) {
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(request.message(peer));
      requests.add(request);
      messages.add(message);
      // a reverse request by hash, from that header down
      int number = (int) header(message.hash().orElseThrow()).getNumber();
      final List<BlockHeader> headers = new ArrayList<>();
      while (number >= 0 && headers.size() < message.maxHeaders()) {
        headers.add(chain.get(number--));
      }
      return CompletableFuture.completedFuture(
          new Response<>(request.decode(BlockHeadersMessage.create(headers), peer), peer));
    }

    @Override
    public <T> CompletableFuture<Response<T>> submitWithRetries(
        final SyncRequest<T> request, final int attempts) {
      return submit(request);
    }

    @Override
    public int peerCount(final Protocol protocol) {
      return 1;
    }

    @Override
    public long bestPeerHeight() {
      return bestPeerHeight;
    }

    @Override
    public int capacity(final Protocol protocol) {
      return 1;
    }

    @Override
    public Executor workers() {
      return Runnable::run;
    }

    private BlockHeader header(final Hash hash) {
      return chain.stream()
          .filter(header -> header.getHash().equals(hash))
          .findFirst()
          .orElseThrow();
    }
  }
}

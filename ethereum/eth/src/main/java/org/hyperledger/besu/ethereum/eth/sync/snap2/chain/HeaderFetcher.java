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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask.Direction;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.PeerTaskRequest;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.RequestSender;
import org.hyperledger.besu.ethereum.eth.sync.snap2.net.SyncRequest.Priority;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads block headers trusted through hash links: a header known by hash, then its ancestors,
 * each one the parent of the previous.
 */
public final class HeaderFetcher {

  private static final long HEAD_TOLERANCE = 8;

  private final RequestSender sender;
  private final ProtocolSchedule protocolSchedule;
  private final ChainHeadSource heads;
  private final int headersPerRequest;

  public HeaderFetcher(
      final RequestSender sender,
      final ProtocolSchedule protocolSchedule,
      final ChainHeadSource heads,
      final int headersPerRequest) {
    this.sender = sender;
    this.protocolSchedule = protocolSchedule;
    this.heads = heads;
    this.headersPerRequest = headersPerRequest;
  }

  /** Returns the header with the given hash. */
  public CompletableFuture<BlockHeader> byHash(final Hash hash, final Priority priority) {
    // a head just announced is only known by the peers closest to the tip
    final long recent =
        priority == Priority.PIVOT ? Math.max(0, sender.bestPeerHeight() - HEAD_TOLERANCE) : 0;
    return heads
        .knownHeader(hash)
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> fetch(hash, 1, priority, recent).thenApply(List::getFirst));
  }

  /**
   * Returns the headers from {@code lowest} up to {@code top} included, in ascending order, each
   * linked to the next by its hash.
   */
  public CompletableFuture<List<BlockHeader>> chainDown(
      final BlockHeader top, final long lowest, final Priority priority) {
    final List<BlockHeader> descending = new ArrayList<>();
    descending.add(top);
    return walkDown(descending, lowest, priority)
        .thenApply(
            headers -> {
              Collections.reverse(headers);
              return headers;
            });
  }

  private CompletableFuture<List<BlockHeader>> walkDown(
      final List<BlockHeader> descending, final long lowest, final Priority priority) {
    final BlockHeader cursor = descending.getLast();
    if (cursor.getNumber() <= lowest) {
      return CompletableFuture.completedFuture(descending);
    }
    final int count = (int) Math.min(headersPerRequest, cursor.getNumber() - lowest);
    return fetch(cursor.getParentHash(), count, priority, cursor.getNumber() - 1)
        .thenCompose(
            headers -> {
              for (final BlockHeader header : headers) {
                if (header.getNumber() < lowest) {
                  break;
                }
                descending.add(header);
              }
              return walkDown(descending, lowest, priority);
            });
  }

  // headers from the given hash going down, the first one checked against the hash
  private CompletableFuture<List<BlockHeader>> fetch(
      final Hash hash, final int count, final Priority priority, final long minimumPeerHeight) {
    return sender
        .submitWithRetries(
            new PeerTaskRequest<>(
                () ->
                    new GetHeadersFromPeerTask(
                        hash, count, 0, Direction.REVERSE, 0, protocolSchedule),
                priority,
                "headers",
                minimumPeerHeight))
        .thenApply(response -> response.value());
  }
}

/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Immutable state for chain synchronization in two-stage fast sync. This state is managed
 * exclusively by the chain downloader and persisted separately from world state sync state to avoid
 * concurrent modification issues.
 *
 * <p>All fields are non-null and immutable. Updates create new instances.
 *
 * @param pivotBlockHeader header of the pivot block
 * @param checkpointBlockHeader header of the checkpoint block
 * @param headerDownloadAnchor set if the anchor is different from the checkpoint block header
 * @param headersDownloadComplete true if the header download has finished
 */
public record ChainSyncState(
    BlockHeader pivotBlockHeader,
    BlockHeader checkpointBlockHeader,
    BlockHeader headerDownloadAnchor,
    boolean headersDownloadComplete) {

  /**
   * Creates a new state with an initial pivot block for full sync from genesis.
   *
   * @param pivotBlockHeader the pivot block header
   * @param checkpointBlockHeader the checkpoint block to start bodies download from
   * @param headerDownloadAnchor set if the anchor is different from the checkpoint block header
   * @return new ChainSyncState
   */
  public static ChainSyncState initialSync(
      final BlockHeader pivotBlockHeader,
      final BlockHeader checkpointBlockHeader,
      final BlockHeader headerDownloadAnchor) {
    return new ChainSyncState(pivotBlockHeader, checkpointBlockHeader, headerDownloadAnchor, false);
  }

  /**
   * Creates a new state for continuing sync to an updated pivot.
   *
   * @param newPivotHeader the new pivot block header
   * @param previousPivotHeader the previous pivot block header
   * @return new ChainSyncState for continuation
   */
  public ChainSyncState continueToNewPivot(
      final BlockHeader newPivotHeader, final BlockHeader previousPivotHeader) {
    return new ChainSyncState(newPivotHeader, previousPivotHeader, null, false);
  }

  /**
   * Creates a new state with headers download marked as complete.
   *
   * @return new ChainSyncState instance
   */
  public ChainSyncState withHeadersDownloadComplete() {
    return new ChainSyncState(this.pivotBlockHeader, this.checkpointBlockHeader, null, true);
  }

  public ChainSyncState fromHead(final BlockHeader chainHeadHeader) {
    return new ChainSyncState(
        this.pivotBlockHeader,
        chainHeadHeader,
        this.headerDownloadAnchor,
        this.headersDownloadComplete);
  }

  public static BlockHeader downloadCheckpointHeader(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final Hash checkpointHash) {

    final EthPeers ethPeers = ethContext.getEthPeers();
    GetHeadersFromPeerTask task =
        new GetHeadersFromPeerTask(
            checkpointHash,
            1,
            0,
            GetHeadersFromPeerTask.Direction.FORWARD,
            ethPeers.getMaxPeers(),
            protocolSchedule);

    final EthPeer ethPeer;

    try {
      ethPeer =
          ethPeers
              .waitForPeer(candidatePeer -> task.getPeerRequirementFilter().test(candidatePeer))
              .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    try {
      return getHeader(
              ethContext, ethPeer, ethPeers, Collections.synchronizedSet(new HashSet<>()), task)
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static CompletableFuture<BlockHeader> getHeader(
      final EthContext ethContext,
      final EthPeer ethPeer,
      final EthPeers ethPeers,
      final Set<EthPeer> peersUsed,
      final PeerTask<List<BlockHeader>> task) {
    return ethContext
        .getScheduler()
        .scheduleServiceTask(
            () -> {
              PeerTaskExecutorResult<List<BlockHeader>> taskResult =
                  ethContext.getPeerTaskExecutor().executeAgainstPeer(task, ethPeer);
              if (taskResult.responseCode() == PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR) {
                // something is probably wrong with the request, so we won't retry as below
                return CompletableFuture.failedFuture(
                    new RuntimeException("Unexpected internal issue"));
              } else if (taskResult.responseCode() != PeerTaskExecutorResponseCode.SUCCESS
                  || taskResult.result().isEmpty()) {
                // recursively call executePivotQueryWithPeerTaskSystem to retry with a different
                // peer.
                try {
                  return getHeader(
                      ethContext,
                      ethPeers
                          .waitForPeer(
                              (p) ->
                                  !peersUsed.contains(p.ethPeer())
                                      && task.getPeerRequirementFilter().test(p))
                          .get(),
                      ethPeers,
                      peersUsed,
                      task);
                } catch (InterruptedException | ExecutionException e) {
                  return CompletableFuture.failedFuture(e);
                }
              }
              return CompletableFuture.completedFuture(taskResult.result().get().getFirst());
            });
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ChainSyncState that = (ChainSyncState) o;
    return Objects.equals(checkpointBlockHeader, that.checkpointBlockHeader)
        && Objects.equals(pivotBlockHeader, that.pivotBlockHeader)
        && Objects.equals(headerDownloadAnchor, that.headerDownloadAnchor)
        && headersDownloadComplete == that.headersDownloadComplete;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pivotBlockHeader, checkpointBlockHeader, headerDownloadAnchor, headersDownloadComplete);
  }

  @Override
  public String toString() {
    return "ChainSyncState{"
        + "pivotBlockNumber="
        + pivotBlockHeader.getNumber()
        + ", pivotBlockHash="
        + pivotBlockHeader.getHash()
        + ", checkpointBlockNumber="
        + checkpointBlockHeader.getNumber()
        + ", headerDownloadAnchorNumber="
        + headerDownloadAnchor.getNumber()
        + ", headersDownloadComplete="
        + headersDownloadComplete
        + '}';
  }
}

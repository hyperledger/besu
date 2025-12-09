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
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Immutable state for the chain synchronization in two-stages. This state is managed exclusively by
 * the SnapSyncChainDownloader.
 *
 * <p>Updates create new instances.
 *
 * @param pivotBlockHeader header of the pivot block
 * @param blockDownloadAnchor header of the checkpoint block
 * @param headerDownloadAnchor set if the anchor is different from the checkpoint block header
 * @param headersDownloadComplete true if the header download has finished
 */
public record ChainSyncState(
    BlockHeader pivotBlockHeader,
    BlockHeader blockDownloadAnchor,
    BlockHeader headerDownloadAnchor,
    boolean headersDownloadComplete) {

  /**
   * Creates a new state with an initial pivot block.
   *
   * @param pivotBlockHeader the pivot block header
   * @param blockDownloadAnchor the checkpoint block to start bodies download from
   * @param headerDownloadAnchor set if the anchor is different from the checkpoint block header
   * @return new ChainSyncState
   */
  public static ChainSyncState initialSync(
      final BlockHeader pivotBlockHeader,
      final BlockHeader blockDownloadAnchor,
      final BlockHeader headerDownloadAnchor) {
    return new ChainSyncState(pivotBlockHeader, blockDownloadAnchor, headerDownloadAnchor, false);
  }

  /**
   * Creates a new state for continuing sync to an updated pivot that is used once the previous
   * pivot block has been reached (previous pivot becomes the new block download anchor).
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
    return new ChainSyncState(this.pivotBlockHeader, this.blockDownloadAnchor, null, true);
  }

  /**
   * Creates a new state when we restart the sync from the current chain head.
   *
   * @param chainHeadHeader the current head of our local chain
   * @return new ChainSyncState instance
   */
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

    ethPeer = getEthPeer(ethPeers, task);

    try {
      return getHeader(ethContext, ethPeer, ethPeers, task).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private static EthPeer getEthPeer(final EthPeers ethPeers, final GetHeadersFromPeerTask task) {
    final EthPeer ethPeer;
    try {
      ethPeer =
          ethPeers
              .waitForPeer(candidatePeer -> task.getPeerRequirementFilter().test(candidatePeer))
              .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return ethPeer;
  }

  private static CompletableFuture<BlockHeader> getHeader(
      final EthContext ethContext,
      final EthPeer ethPeer,
      final EthPeers ethPeers,
      final GetHeadersFromPeerTask task) {
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
                ethPeer.recordUselessResponse("headers");
                return getHeader(ethContext, getEthPeer(ethPeers, task), ethPeers, task);
              }
              return CompletableFuture.completedFuture(taskResult.result().get().getFirst());
            });
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ChainSyncState that = (ChainSyncState) o;
    return Objects.equals(blockDownloadAnchor, that.blockDownloadAnchor)
        && Objects.equals(pivotBlockHeader, that.pivotBlockHeader)
        && Objects.equals(headerDownloadAnchor, that.headerDownloadAnchor)
        && headersDownloadComplete == that.headersDownloadComplete;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pivotBlockHeader, blockDownloadAnchor, headerDownloadAnchor, headersDownloadComplete);
  }

  @Override
  public String toString() {
    return "ChainSyncState{"
        + "pivotBlockNumber="
        + pivotBlockHeader.getNumber()
        + ", pivotBlockHash="
        + pivotBlockHeader.getHash()
        + ", checkpointBlockNumber="
        + blockDownloadAnchor.getNumber()
        + ", headerDownloadAnchorNumber="
        + headerDownloadAnchor.getNumber()
        + ", headersDownloadComplete="
        + headersDownloadComplete
        + '}';
  }
}

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

import java.util.Objects;

/**
 * Immutable state for chain synchronization in two-stage fast sync. This state is managed
 * exclusively by the chain downloader and persisted separately from world state sync state to avoid
 * concurrent modification issues.
 *
 * <p>All fields are non-null and immutable. Updates create new instances.
 */
public class ChainSyncState {

  private final BlockHeader pivotBlockHeader;
  private final long
      headerDownloadStopBlock; // Where to stop backward download (0 or previous pivot+1)
  private final long
      bodiesDownloadStartBlock; // Where to start forward download (checkpoint or previous pivot+1)
  private final boolean headersDownloadComplete;

  /**
   * Creates a new ChainSyncState.
   *
   * @param pivotBlockHeader the pivot block header (must not be null)
   * @param headerDownloadStopBlock the block number to stop backward header download at
   * @param bodiesDownloadStartBlock the block number to start forward bodies/receipts download from
   * @param headersDownloadComplete whether header download has completed
   */
  public ChainSyncState(
      final BlockHeader pivotBlockHeader,
      final long headerDownloadStopBlock,
      final long bodiesDownloadStartBlock,
      final boolean headersDownloadComplete) {
    this.pivotBlockHeader =
        Objects.requireNonNull(pivotBlockHeader, "pivotBlockHeader is required");
    this.headerDownloadStopBlock = headerDownloadStopBlock;
    this.bodiesDownloadStartBlock = bodiesDownloadStartBlock;
    this.headersDownloadComplete = headersDownloadComplete;
  }

  /**
   * Creates a new state with an initial pivot block for full sync from genesis.
   *
   * @param pivotBlockHeader the pivot block header
   * @param checkpointBlock the checkpoint block to start bodies download from (0 for full sync)
   * @return new ChainSyncState
   */
  public static ChainSyncState initialSync(
      final BlockHeader pivotBlockHeader, final long checkpointBlock) {
    return new ChainSyncState(pivotBlockHeader, 0L, checkpointBlock, false);
  }

  /**
   * Creates a new state for continuing sync to an updated pivot.
   *
   * @param newPivotHeader the new pivot block header
   * @param previousPivotNumber the previous pivot block number
   * @return new ChainSyncState for continuation
   */
  public ChainSyncState continueToNewPivot(
      final BlockHeader newPivotHeader, final long previousPivotNumber) {
    return new ChainSyncState(
        newPivotHeader,
        previousPivotNumber + 1, // Stop backward at previous pivot
        previousPivotNumber + 1, // Start forward from previous pivot
        false); // New download not complete yet
  }

  /**
   * Creates a new state with headers download marked as complete.
   *
   * @return new ChainSyncState instance
   */
  public ChainSyncState withHeadersDownloadComplete() {
    return new ChainSyncState(
        this.pivotBlockHeader, this.headerDownloadStopBlock, this.bodiesDownloadStartBlock, true);
  }

  public BlockHeader getPivotBlockHeader() {
    return pivotBlockHeader;
  }

  public long getPivotBlockNumber() {
    return pivotBlockHeader.getNumber();
  }

  public Hash getPivotBlockHash() {
    return pivotBlockHeader.getHash();
  }

  public long getHeaderDownloadStopBlock() {
    return headerDownloadStopBlock;
  }

  public long getBodiesDownloadStartBlock() {
    return bodiesDownloadStartBlock;
  }

  public boolean isHeadersDownloadComplete() {
    return headersDownloadComplete;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ChainSyncState that = (ChainSyncState) o;
    return headerDownloadStopBlock == that.headerDownloadStopBlock
        && bodiesDownloadStartBlock == that.bodiesDownloadStartBlock
        && headersDownloadComplete == that.headersDownloadComplete
        && Objects.equals(pivotBlockHeader, that.pivotBlockHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pivotBlockHeader,
        headerDownloadStopBlock,
        bodiesDownloadStartBlock,
        headersDownloadComplete);
  }

  @Override
  public String toString() {
    return "ChainSyncState{"
        + "pivotBlockNumber="
        + getPivotBlockNumber()
        + ", pivotBlockHash="
        + getPivotBlockHash()
        + ", headerDownloadStopBlock="
        + headerDownloadStopBlock
        + ", bodiesDownloadStartBlock="
        + bodiesDownloadStartBlock
        + ", headersDownloadComplete="
        + headersDownloadComplete
        + '}';
  }
}

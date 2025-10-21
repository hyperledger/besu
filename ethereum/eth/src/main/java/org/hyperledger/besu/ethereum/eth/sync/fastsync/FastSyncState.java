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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public class FastSyncState {

  public static final FastSyncState EMPTY_SYNC_STATE = new FastSyncState();

  private OptionalLong pivotBlockNumber;
  private Optional<Hash> pivotBlockHash;
  private Optional<BlockHeader> pivotBlockHeader;
  private boolean sourceIsTrusted = false;

  // Backward header download progress tracking
  private OptionalLong lowestContiguousBlockHeaderDownloaded;
  private boolean backwardHeaderDownloadComplete = false;

  public FastSyncState() {
    pivotBlockNumber = OptionalLong.empty();
    pivotBlockHash = Optional.empty();
    pivotBlockHeader = Optional.empty();
    lowestContiguousBlockHeaderDownloaded = OptionalLong.empty();
  }

  public FastSyncState(final long pivotBlockNumber, final boolean sourceIsTrusted) {
    this(
        OptionalLong.of(pivotBlockNumber),
        Optional.empty(),
        Optional.empty(),
        sourceIsTrusted,
        OptionalLong.empty(),
        false);
  }

  public FastSyncState(final Hash pivotBlockHash, final boolean sourceIsTrusted) {
    this(
        OptionalLong.empty(),
        Optional.of(pivotBlockHash),
        Optional.empty(),
        sourceIsTrusted,
        OptionalLong.empty(),
        false);
  }

  public FastSyncState(final BlockHeader pivotBlockHeader, final boolean sourceIsTrusted) {
    this(
        OptionalLong.of(pivotBlockHeader.getNumber()),
        Optional.of(pivotBlockHeader.getHash()),
        Optional.of(pivotBlockHeader),
        sourceIsTrusted,
        OptionalLong.empty(),
        false);
  }

  protected FastSyncState(
      final OptionalLong pivotBlockNumber,
      final Optional<Hash> pivotBlockHash,
      final Optional<BlockHeader> pivotBlockHeader,
      final boolean sourceIsTrusted,
      final OptionalLong lowestContiguousBlockHeaderDownloaded,
      final boolean backwardHeaderDownloadComplete) {
    this.pivotBlockNumber = pivotBlockNumber;
    this.pivotBlockHash = pivotBlockHash;
    this.pivotBlockHeader = pivotBlockHeader;
    this.sourceIsTrusted = sourceIsTrusted;
    this.lowestContiguousBlockHeaderDownloaded = lowestContiguousBlockHeaderDownloaded;
    this.backwardHeaderDownloadComplete = backwardHeaderDownloadComplete;
  }

  public OptionalLong getPivotBlockNumber() {
    return pivotBlockNumber;
  }

  public Optional<Hash> getPivotBlockHash() {
    return pivotBlockHash;
  }

  public Optional<BlockHeader> getPivotBlockHeader() {
    return pivotBlockHeader;
  }

  public boolean hasPivotBlockHeader() {
    return pivotBlockHeader.isPresent();
  }

  public boolean hasPivotBlockHash() {
    return pivotBlockHash.isPresent();
  }

  /**
   * Returns true if the source of the pivot block is fully trusted. In practice this means that it
   * comes from the Consensus client through the engine API and the {@link
   * PivotSelectorFromSafeBlock} is used for the pivot.
   *
   * @return true if the source is fully trusted, false otherwise
   */
  public boolean isSourceTrusted() {
    return sourceIsTrusted;
  }

  public void setCurrentHeader(final BlockHeader header) {
    pivotBlockNumber = OptionalLong.of(header.getNumber());
    pivotBlockHash = Optional.of(header.getHash());
    pivotBlockHeader = Optional.of(header);
  }

  /**
   * Gets the lowest contiguous block number downloaded during backward header sync.
   *
   * @return the lowest contiguous block downloaded, or empty if not started
   */
  public OptionalLong getLowestContiguousBlockHeaderDownloaded() {
    return lowestContiguousBlockHeaderDownloaded;
  }

  /**
   * Sets the lowest contiguous block downloaded during backward header sync.
   *
   * @param blockNumber the lowest contiguous block number
   */
  public void setLowestContiguousBlockHeaderDownloaded(final long blockNumber) {
    this.lowestContiguousBlockHeaderDownloaded = OptionalLong.of(blockNumber);
  }

  /**
   * Returns true if the backward header download has completed (reached block 0).
   *
   * @return true if backward header download is complete
   */
  public boolean isBackwardHeaderDownloadComplete() {
    return backwardHeaderDownloadComplete;
  }

  /**
   * Marks the backward header download as complete.
   */
  public void setBackwardHeaderDownloadComplete(final boolean complete) {
    this.backwardHeaderDownloadComplete = complete;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FastSyncState that = (FastSyncState) o;
    return sourceIsTrusted == that.sourceIsTrusted
        && backwardHeaderDownloadComplete == that.backwardHeaderDownloadComplete
        && Objects.equals(pivotBlockNumber, that.pivotBlockNumber)
        && Objects.equals(pivotBlockHash, that.pivotBlockHash)
        && Objects.equals(pivotBlockHeader, that.pivotBlockHeader)
        && Objects.equals(lowestContiguousBlockHeaderDownloaded, that.lowestContiguousBlockHeaderDownloaded);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pivotBlockNumber,
        pivotBlockHash,
        pivotBlockHeader,
        sourceIsTrusted,
            lowestContiguousBlockHeaderDownloaded,
        backwardHeaderDownloadComplete);
  }

  @Override
  public String toString() {
    return "FastSyncState{"
        + "pivotBlockNumber="
        + pivotBlockNumber
        + ", pivotBlockHash="
        + pivotBlockHash
        + ", pivotBlockHeader="
        + pivotBlockHeader
        + ", sourceIsTrusted="
        + sourceIsTrusted
        + ", lowestContiguousBlockDownloaded="
        + lowestContiguousBlockHeaderDownloaded
        + ", backwardHeaderDownloadComplete="
        + backwardHeaderDownloadComplete
        + '}';
  }
}

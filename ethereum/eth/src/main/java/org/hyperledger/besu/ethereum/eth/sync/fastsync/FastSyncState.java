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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.common.base.MoreObjects;

public class FastSyncState {

  public static FastSyncState EMPTY_SYNC_STATE =
      new FastSyncState(OptionalLong.empty(), Optional.empty());

  private final OptionalLong pivotBlockNumber;
  private final Optional<BlockHeader> pivotBlockHeader;

  public FastSyncState(final long pivotBlockNumber) {
    this(OptionalLong.of(pivotBlockNumber), Optional.empty());
  }

  public FastSyncState(final BlockHeader pivotBlockHeader) {
    this(OptionalLong.of(pivotBlockHeader.getNumber()), Optional.of(pivotBlockHeader));
  }

  private FastSyncState(
      final OptionalLong pivotBlockNumber, final Optional<BlockHeader> pivotBlockHeader) {
    this.pivotBlockNumber = pivotBlockNumber;
    this.pivotBlockHeader = pivotBlockHeader;
  }

  public OptionalLong getPivotBlockNumber() {
    return pivotBlockNumber;
  }

  public Optional<BlockHeader> getPivotBlockHeader() {
    return pivotBlockHeader;
  }

  public boolean hasPivotBlockHeader() {
    return pivotBlockHeader.isPresent();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FastSyncState that = (FastSyncState) o;
    return Objects.equals(pivotBlockNumber, that.pivotBlockNumber)
        && Objects.equals(pivotBlockHeader, that.pivotBlockHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pivotBlockNumber, pivotBlockHeader);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pivotBlockNumber", pivotBlockNumber)
        .add("pivotBlockHeader", pivotBlockHeader)
        .toString();
  }
}

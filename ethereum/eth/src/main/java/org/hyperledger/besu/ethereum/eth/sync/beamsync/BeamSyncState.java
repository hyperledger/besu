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
package org.hyperledger.besu.ethereum.eth.sync.beamsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.common.base.MoreObjects;

public class BeamSyncState {

  public static BeamSyncState EMPTY_SYNC_STATE =
      new BeamSyncState(OptionalLong.empty(), Optional.empty());

  private final OptionalLong launchBlockNumber;
  private final Optional<BlockHeader> launchBlockHeader;

  public BeamSyncState(final long launchBlockNumber) {
    this(OptionalLong.of(launchBlockNumber), Optional.empty());
  }

  public BeamSyncState(final BlockHeader launchBlockHeader) {
    this(OptionalLong.of(launchBlockHeader.getNumber()), Optional.of(launchBlockHeader));
  }

  private BeamSyncState(
      final OptionalLong launchBlockNumber, final Optional<BlockHeader> launchBlockHeader) {
    this.launchBlockNumber = launchBlockNumber;
    this.launchBlockHeader = launchBlockHeader;
  }

  public OptionalLong getLaunchBlockNumber() {
    return launchBlockNumber;
  }

  public Optional<BlockHeader> getLaunchBlockHeader() {
    return launchBlockHeader;
  }

  public boolean hasLaunchBlockHeader() {
    return launchBlockHeader.isPresent();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeamSyncState that = (BeamSyncState) o;
    return Objects.equals(launchBlockNumber, that.launchBlockNumber)
        && Objects.equals(launchBlockHeader, that.launchBlockHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(launchBlockNumber, launchBlockHeader);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pivotBlockNumber", launchBlockNumber)
        .add("pivotBlockHeader", launchBlockHeader)
        .toString();
  }
}

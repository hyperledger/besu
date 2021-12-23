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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;

import java.util.Optional;
import java.util.OptionalLong;

public class SnapSyncState extends FastSyncState {

  private OptionalLong originalPivotBlockNumber;
  private boolean isHealInProgress;
  private boolean isResettingPivotBlock;

  public SnapSyncState(final FastSyncState fastSyncState) {
    super(fastSyncState.getPivotBlockNumber(), fastSyncState.getPivotBlockHeader());
    originalPivotBlockNumber = fastSyncState.getPivotBlockNumber();
  }

  public SnapSyncState(final long pivotBlockNumber) {
    super(OptionalLong.of(pivotBlockNumber), Optional.empty());
    this.originalPivotBlockNumber = OptionalLong.of(pivotBlockNumber);
  }

  public SnapSyncState(final BlockHeader pivotBlockHeader) {
    super(OptionalLong.of(pivotBlockHeader.getNumber()), Optional.of(pivotBlockHeader));
    this.originalPivotBlockNumber = OptionalLong.of(pivotBlockHeader.getNumber());
  }

  public OptionalLong getOriginalPivotBlockNumber() {
    return originalPivotBlockNumber;
  }

  public boolean isPivotBlockChanged() {
    return !originalPivotBlockNumber.equals(getPivotBlockNumber());
  }

  public void setSnapHealInProgress(final boolean isHealInProgress) {
    this.isHealInProgress = isHealInProgress;
  }

  public boolean isHealInProgress() {
    return isHealInProgress;
  }

  public boolean isResettingPivotBlock() {
    return isResettingPivotBlock;
  }

  public void setOriginalPivotBlockNumber(final OptionalLong originalPivotBlockNumber) {
    this.originalPivotBlockNumber = originalPivotBlockNumber;
  }

  public boolean lockResettingPivotBlock() {
    synchronized (this) {
      if (isResettingPivotBlock) {
        return false;
      } else {
        isResettingPivotBlock = true;
        return true;
      }
    }
  }

  public void unlockResettingPivotBlock() {
    synchronized (this) {
      isResettingPivotBlock = false;
    }
  }
}

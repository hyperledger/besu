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

import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;

import java.util.concurrent.atomic.AtomicInteger;

public class SnapSyncState extends FastSyncState {

  private boolean isHealInProgress;

  private final AtomicInteger lock = new AtomicInteger();

  public SnapSyncState(final FastSyncState fastSyncState) {
    super(fastSyncState.getPivotBlockNumber(), fastSyncState.getPivotBlockHeader());
  }

  public boolean isHealInProgress() {
    return isHealInProgress;
  }

  public void notifyStartHeal() {
    isHealInProgress = true;
  }

  public boolean isResettingPivotBlock() {
    return lock.get() == 1;
  }

  public boolean isValidTask(final SnapDataRequest request) {
    return getPivotBlockHeader()
        .map(SealableBlockHeader::getStateRoot)
        .filter(hash -> hash.equals(request.getRootHash()))
        .isPresent();
  }

  public boolean lockResettingPivotBlock() {
    return lock.compareAndSet(0, 1);
  }

  public void unlockResettingPivotBlock() {
    lock.set(1);
  }
}

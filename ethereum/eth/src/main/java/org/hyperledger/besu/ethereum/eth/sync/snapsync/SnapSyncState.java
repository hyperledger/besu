/*
 * Copyright contributors to Hyperledger Besu
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

public class SnapSyncState extends FastSyncState {

  private boolean isHealInProgress;

  public SnapSyncState(final FastSyncState fastSyncState) {
    super(
        fastSyncState.getPivotBlockNumber(),
        fastSyncState.getPivotBlockHash(),
        fastSyncState.getPivotBlockHeader());
  }

  public boolean isHealInProgress() {
    return isHealInProgress;
  }

  public void setHealStatus(final boolean healStatus) {
    isHealInProgress = healStatus;
  }

  public boolean isExpired(final SnapDataRequest request) {
    return getPivotBlockHeader()
        .map(SealableBlockHeader::getStateRoot)
        .filter(hash -> hash.equals(request.getRootHash()))
        .isEmpty();
  }
}

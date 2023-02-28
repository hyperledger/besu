/*
 * Copyright Hyperledger Besu contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class CachedBonsaiWorldView implements BonsaiStorageSubscriber {
  private BonsaiWorldView worldView;
  private final BlockHeader blockHeader;
  private long worldViewSubscriberId = -1L;

  public CachedBonsaiWorldView(final BlockHeader blockHeader, final BonsaiWorldView worldView) {
    this.blockHeader = blockHeader;
    this.worldView = worldView;
  }

  public BonsaiWorldView getWorldView() {
    return worldView;
  }

  public BonsaiWorldStateUpdateAccumulator cloneUpdater() {
    if (worldView instanceof BonsaiWorldStateUpdateAccumulator) {
      return ((BonsaiWorldStateUpdateAccumulator) worldView).copy();
    } else if (worldView instanceof BonsaiWorldState) {
      return ((BonsaiWorldStateUpdateAccumulator) worldView.updater()).copy();
    }
    throw new UnsupportedOperationException("unable to clone unknown world view type");
  }

  void updateWorldView(final BonsaiWorldView updatedWorldView) {
    // update this worldview with a new one, such as a new snapshot BonsaiWorldView after a
    // forkchoiceUpdate
    this.worldView = updatedWorldView;
  }

  public long subscribe(final CachedBonsaiWorldView subscriber) {
    this.worldViewSubscriberId = worldView.subscribe(subscriber);
    return this.worldViewSubscriberId;
  }

  public void unSubscribe(final long subscriberId) {
    worldView.unSubscribe(subscriberId);
  }

  public long getBlockNumber() {
    return blockHeader.getNumber();
  }

  public Hash getBlockHash() {
    return blockHeader.getHash();
  }

  public void close() {
    worldView.unSubscribe(this.worldViewSubscriberId);
  }
}

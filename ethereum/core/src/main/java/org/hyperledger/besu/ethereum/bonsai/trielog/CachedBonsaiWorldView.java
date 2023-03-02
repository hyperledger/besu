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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class CachedBonsaiWorldView implements BonsaiStorageSubscriber {
  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final BlockHeader blockHeader;
  private final long worldViewSubscriberId;

  public CachedBonsaiWorldView(
      final BlockHeader blockHeader, final BonsaiWorldStateKeyValueStorage worldView) {
    this.blockHeader = blockHeader;
    this.worldStateStorage = worldView;
    this.worldViewSubscriberId = worldStateStorage.subscribe(this);
  }

  public BonsaiWorldStateKeyValueStorage getWorldstateStorage() {
    return worldStateStorage;
  }

  public long getBlockNumber() {
    return blockHeader.getNumber();
  }

  public Hash getBlockHash() {
    return blockHeader.getHash();
  }

  public void close() {
    worldStateStorage.unSubscribe(this.worldViewSubscriberId);
  }
}

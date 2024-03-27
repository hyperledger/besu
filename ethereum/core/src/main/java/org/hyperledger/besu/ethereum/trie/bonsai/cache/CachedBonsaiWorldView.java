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
package org.hyperledger.besu.ethereum.trie.bonsai.cache;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedBonsaiWorldView
    implements BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber {
  private BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage;
  private final BlockHeader blockHeader;
  private long worldViewSubscriberId;
  private static final Logger LOG = LoggerFactory.getLogger(CachedBonsaiWorldView.class);

  public CachedBonsaiWorldView(
      final BlockHeader blockHeader, final BonsaiWorldStateKeyValueStorage worldView) {
    this.blockHeader = blockHeader;
    this.worldStateKeyValueStorage = worldView;
    this.worldViewSubscriberId = worldStateKeyValueStorage.subscribe(this);
  }

  public BonsaiWorldStateKeyValueStorage getWorldStateStorage() {
    return worldStateKeyValueStorage;
  }

  public long getBlockNumber() {
    return blockHeader.getNumber();
  }

  public Hash getBlockHash() {
    return blockHeader.getHash();
  }

  public synchronized void close() {
    worldStateKeyValueStorage.unSubscribe(this.worldViewSubscriberId);
    try {
      worldStateKeyValueStorage.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close worldstate storage for block " + blockHeader.toLogString(), e);
    }
  }

  public synchronized void updateWorldStateStorage(
      final BonsaiWorldStateKeyValueStorage newWorldStateStorage) {
    long newSubscriberId = newWorldStateStorage.subscribe(this);
    this.worldStateKeyValueStorage.unSubscribe(this.worldViewSubscriberId);
    BonsaiWorldStateKeyValueStorage oldWorldStateStorage = this.worldStateKeyValueStorage;
    this.worldStateKeyValueStorage = newWorldStateStorage;
    this.worldViewSubscriberId = newSubscriberId;
    try {
      oldWorldStateStorage.close();
    } catch (final Exception e) {
      LOG.warn(
          "During update, failed to close prior worldstate storage for block "
              + blockHeader.toLogString(),
          e);
    }
  }
}

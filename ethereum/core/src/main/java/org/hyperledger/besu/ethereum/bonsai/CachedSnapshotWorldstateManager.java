/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedSnapshotWorldstateManager extends AbstractTrieLogManager<BonsaiWorldState>
    implements BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber {
  private static final Logger LOG = LoggerFactory.getLogger(CachedSnapshotWorldstateManager.class);

  CachedSnapshotWorldstateManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, BonsaiSnapshotWorldStateKeyValueStorage> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
  }

  public CachedSnapshotWorldstateManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(blockchain, worldStateStorage, maxLayersToLoad, new HashMap<>());
  }

  @Override
  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final BonsaiWorldState forWorldState) {
    infoLambda(
        LOG,
        "adding layered world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toShortHexString);
    if (forWorldState.worldStateStorage instanceof BonsaiSnapshotWorldStateKeyValueStorage) {
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          ((BonsaiSnapshotWorldStateKeyValueStorage) forWorldState.worldStateStorage).clone());
    } else {
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          new BonsaiSnapshotWorldStateKeyValueStorage(
              blockHeader.getNumber(), forWorldState.worldStateStorage));
    }
    scrubCachedLayers(blockHeader.getNumber());
  }
}

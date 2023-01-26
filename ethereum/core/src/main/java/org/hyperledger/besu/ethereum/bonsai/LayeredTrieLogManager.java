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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredTrieLogManager extends AbstractTrieLogManager<BonsaiLayeredWorldState> {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredTrieLogManager.class);

  LayeredTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedWorldState<BonsaiLayeredWorldState>> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
  }

  public LayeredTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(blockchain, worldStateStorage, maxLayersToLoad, new HashMap<>());
  }

  @Override
  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiPersistedWorldState forWorldState) {

    final BonsaiLayeredWorldState bonsaiLayeredWorldState =
        new BonsaiLayeredWorldState(
            blockchain,
            worldStateArchive,
            Optional.of(forWorldState),
            blockHeader.getNumber(),
            worldStateRootHash,
            trieLog);
    debugLambda(
        LOG,
        "adding layered world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toShortHexString);
    cachedWorldStatesByHash.put(
        blockHeader.getHash(), new LayeredWorldStateCache(bonsaiLayeredWorldState));
  }

  @Override
  public synchronized void updateCachedLayers(final Hash blockParentHash, final Hash blockHash) {
    cachedWorldStatesByHash.computeIfPresent(
        blockParentHash,
        (parentHash, bonsaiLayeredWorldState) -> {
          if (cachedWorldStatesByHash.containsKey(blockHash)) {
            bonsaiLayeredWorldState
                .getMutableWorldState()
                .setNextWorldView(
                    Optional.of(cachedWorldStatesByHash.get(blockHash).getMutableWorldState()));
          }
          return bonsaiLayeredWorldState;
        });
  }

  public static class LayeredWorldStateCache implements CachedWorldState<BonsaiLayeredWorldState> {

    final BonsaiLayeredWorldState layeredWorldState;

    public LayeredWorldStateCache(final BonsaiLayeredWorldState layeredWorldState) {
      this.layeredWorldState = layeredWorldState;
    }

    @Override
    public void dispose() {
      // no-op
    }

    @Override
    public long getHeight() {
      return layeredWorldState.getHeight();
    }

    @Override
    public TrieLogLayer getTrieLog() {
      return layeredWorldState.getTrieLog();
    }

    @Override
    public BonsaiLayeredWorldState getMutableWorldState() {
      return layeredWorldState;
    }
  }
}

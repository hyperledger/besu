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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;

import java.util.Optional;
import java.util.function.Function;

public interface TrieLogManager {

  void saveTrieLog(
      final BonsaiWorldStateUpdateAccumulator localUpdater,
      final Hash forWorldStateRootHash,
      final BlockHeader forBlockHeader,
      final BonsaiWorldState forWorldState);

  void addCachedLayer(
      BlockHeader blockHeader, Hash worldStateRootHash, BonsaiWorldState forWorldState);

  boolean containWorldStateStorage(final Hash blockHash);

  Optional<BonsaiWorldState> getWorldState(final Hash blockHash);

  Optional<BonsaiWorldState> getNearestWorldState(final BlockHeader blockHeader);

  Optional<BonsaiWorldState> getHeadWorldState(
      final Function<Hash, Optional<BlockHeader>> hashBlockHeaderFunction);

  Optional<BonsaiWorldState> getCheckpointedWorldState(
      final BlockHeader blockHeader,
      final Function<Long, Optional<BlockHeader>> checkPointerBlockHeaderFunction);

  long getMaxLayersToLoad();

  void reset();

  Optional<? extends TrieLog> getTrieLogLayer(final Hash blockHash);

  long subscribe(final TrieLogEvent.TrieLogObserver sub);

  void unsubscribe(final long id);
}

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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class BonsaiInMemoryWorldState extends BonsaiPersistedWorldState {

  public BonsaiInMemoryWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    super(archive, worldStateStorage);
  }

  @Override
  public Hash rootHash() {
    final BonsaiWorldStateKeyValueStorage.Updater updater = worldStateStorage.updater();
    try {
      final Hash calculatedRootHash = calculateRootHash(updater);
      return Hash.wrap(calculatedRootHash);
    } finally {
      updater.rollback();
    }
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    final BonsaiWorldStateUpdater localUpdater = updater.copy();
    try {
      final Hash newWorldStateRootHash = rootHash();
      prepareTrieLog(blockHeader, localUpdater, newWorldStateRootHash);
      worldStateBlockHash = blockHeader.getHash();
      worldStateRootHash = newWorldStateRootHash;
    } finally {
      localUpdater.reset();
    }
  }
}

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
package org.hyperledger.besu.ethereum.bonsai.light;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.Updater;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateUpdater;
import org.hyperledger.besu.ethereum.core.BlockHeader;

public class BonsaiLightPersistedWorldState extends BonsaiPersistedWorldState {

  public BonsaiLightPersistedWorldState(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    super(archive, worldStateStorage);
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    super.persist(blockHeader);
  }

  @Override
  protected void verifyRootHash(final BlockHeader blockHeader, final Hash newWorldStateRootHash) {}

  @Override
  protected Hash calculateRootHash(
      final Updater stateUpdater, final BonsaiWorldStateUpdater worldStateUpdater) {
    // there is no point in calculating the root hash as it will be incorrect with no trie branches
    return Hash.EMPTY;
  }
}

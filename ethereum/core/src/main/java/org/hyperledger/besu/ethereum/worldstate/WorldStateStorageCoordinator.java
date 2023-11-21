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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.strategy.BonsaiWorldStateStorageStrategy;
import org.hyperledger.besu.ethereum.worldstate.strategy.ForestWorldStateStorageStrategy;
import org.hyperledger.besu.ethereum.worldstate.strategy.WorldStateStorageStrategy;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public record WorldStateStorageCoordinator(WorldStateStorageStrategy worldStateStorageStrategy) {

  public DataStorageFormat getDataStorageFormat() {
    return worldStateStorageStrategy.getDataStorageFormat();
  }

  public boolean isWorldStateAvailable(final Bytes32 nodeHash, final Hash blockHash) {
    return applyForStrategy(
        bonsai -> bonsai.isWorldStateAvailable(nodeHash, blockHash),
        forest -> forest.isWorldStateAvailable(nodeHash));
  }

  public Optional<Bytes> getTrieNodeUnsafe(final Bytes key) {
    return applyForStrategy(
        bonsai -> bonsai.getTrieNodeUnsafe(key),
        forest -> forest.getAccountStateTrieNode(Bytes32.wrap(key)));
  }

  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    return applyForStrategy(
        bonsai -> bonsai.getAccountStateTrieNode(location, nodeHash),
        forest -> forest.getAccountStateTrieNode(nodeHash));
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return applyForStrategy(
        bonsai -> bonsai.getAccountStorageTrieNode(accountHash, location, nodeHash),
        forest -> forest.getAccountStorageTrieNode(nodeHash));
  }

  @SuppressWarnings("unchecked")
  public <STRATEGY extends WorldStateStorageStrategy> STRATEGY getStrategy(
      final Class<STRATEGY> strategyClass) {
    return (STRATEGY) worldStateStorageStrategy;
  }

  public void applyOnMatchingFlatMode(
      final FlatDbMode flatDbMode, final Consumer<BonsaiWorldStateStorageStrategy> onStrategy) {
    applyOnMatchingStrategy(
        DataStorageFormat.BONSAI,
        worldStateStorageStrategy -> {
          final BonsaiWorldStateStorageStrategy bonsaiWorldStateStorageStrategy =
              (BonsaiWorldStateStorageStrategy) worldStateStorageStrategy();
          if (bonsaiWorldStateStorageStrategy.getFlatDbMode().equals(flatDbMode)) {
            onStrategy.accept(bonsaiWorldStateStorageStrategy);
          }
        });
  }

  public void applyWhenFlatModeEnabled(final Consumer<BonsaiWorldStateStorageStrategy> onStrategy) {
    applyOnMatchingStrategy(
        DataStorageFormat.BONSAI,
        worldStateStorageStrategy -> {
          final BonsaiWorldStateStorageStrategy bonsaiWorldStateStorageStrategy =
              (BonsaiWorldStateStorageStrategy) worldStateStorageStrategy();
          if (!bonsaiWorldStateStorageStrategy.getFlatDbMode().equals(FlatDbMode.NO_FLATTENED)) {
            onStrategy.accept(bonsaiWorldStateStorageStrategy);
          }
        });
  }

  public void applyOnMatchingStrategy(
      final DataStorageFormat dataStorageFormat,
      final Consumer<WorldStateStorageStrategy> onStrategy) {
    if (getDataStorageFormat().equals(dataStorageFormat)) {
      onStrategy.accept(worldStateStorageStrategy());
    }
  }

  public <RESPONSE> RESPONSE applyForStrategy(
      final Function<BonsaiWorldStateStorageStrategy, RESPONSE> onBonsai,
      final Function<ForestWorldStateStorageStrategy, RESPONSE> onForest) {
    if (getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
      return onBonsai.apply(((BonsaiWorldStateStorageStrategy) worldStateStorageStrategy()));
    } else {
      return onForest.apply(((ForestWorldStateStorageStrategy) worldStateStorageStrategy()));
    }
  }

  public void consumeForStrategy(
      final Consumer<BonsaiWorldStateStorageStrategy> onBonsai,
      final Consumer<ForestWorldStateStorageStrategy> onForest) {
    if (getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
      onBonsai.accept(((BonsaiWorldStateStorageStrategy) worldStateStorageStrategy()));
    } else {
      onForest.accept(((ForestWorldStateStorageStrategy) worldStateStorageStrategy()));
    }
  }

  public static void applyForStrategy(
      final WorldStateStorageStrategy.Updater updater,
      final Consumer<BonsaiWorldStateStorageStrategy.Updater> onBonsai,
      final Consumer<ForestWorldStateStorageStrategy.Updater> onForest) {
    if (updater instanceof BonsaiWorldStateKeyValueStorage.Updater) {
      onBonsai.accept(((BonsaiWorldStateStorageStrategy.Updater) updater));
    } else if (updater instanceof ForestWorldStateStorageStrategy.Updater) {
      onForest.accept(((ForestWorldStateStorageStrategy.Updater) updater));
    }
  }

  public WorldStateStorageStrategy.Updater updater() {
    return worldStateStorageStrategy().updater();
  }

  public void clear() {
    worldStateStorageStrategy.clear();
  }
}

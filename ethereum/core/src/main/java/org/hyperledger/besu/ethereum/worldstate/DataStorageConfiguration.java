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

package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogPruner;

import org.immutables.value.Value;

@Value.Immutable
@Value.Enclosing
public interface DataStorageConfiguration {

  long DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD = 512;

  DataStorageConfiguration DEFAULT_CONFIG =
      ImmutableDataStorageConfiguration.builder()
          .dataStorageFormat(DataStorageFormat.FOREST)
          .bonsaiMaxLayersToLoad(DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD)
          .unstable(Unstable.DEFAULT)
          .build();

  DataStorageFormat getDataStorageFormat();

  Long getBonsaiMaxLayersToLoad();

  @Value.Default
  default Unstable getUnstable() {
    return Unstable.DEFAULT;
  }

  @Value.Immutable
  interface Unstable {

    long DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD = TrieLogPruner.DEFAULT_RETENTION_THRESHOLD;
    int DEFAULT_BONSAI_TRIE_LOG_PRUNE_LIMIT = TrieLogPruner.DEFAULT_PRUNING_LIMIT;

    DataStorageConfiguration.Unstable DEFAULT =
        ImmutableDataStorageConfiguration.Unstable.builder().build();

    @Value.Default
    default long getBonsaiTrieLogRetentionThreshold() {
      return DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;
    }

    default boolean isTrieLogPruningEnabled() {
      return getBonsaiTrieLogRetentionThreshold() > 0;
    }

    @Value.Default
    default int getBonsaiTrieLogPruningLimit() {
      return DEFAULT_BONSAI_TRIE_LOG_PRUNE_LIMIT;
    }
  }
}

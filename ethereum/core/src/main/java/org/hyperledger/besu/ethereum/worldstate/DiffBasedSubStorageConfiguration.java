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

import org.immutables.value.Value;

@Value.Immutable
@Value.Enclosing
public interface DiffBasedSubStorageConfiguration {

  DiffBasedSubStorageConfiguration DEFAULT =
      ImmutableDiffBasedSubStorageConfiguration.builder().build();

  DiffBasedSubStorageConfiguration DISABLED =
      ImmutableDiffBasedSubStorageConfiguration.builder()
          .limitTrieLogsEnabled(false)
          .unstable(DiffBasedUnstable.DISABLED)
          .build();

  long DEFAULT_MAX_LAYERS_TO_LOAD = 512;
  boolean DEFAULT_LIMIT_TRIE_LOGS_ENABLED = true;
  long MINIMUM_TRIE_LOG_RETENTION_LIMIT = DEFAULT_MAX_LAYERS_TO_LOAD;
  int DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE = 5_000;

  @Value.Default
  default Long getMaxLayersToLoad() {
    return DEFAULT_MAX_LAYERS_TO_LOAD;
  }

  @Value.Default
  default boolean getLimitTrieLogsEnabled() {
    return DEFAULT_LIMIT_TRIE_LOGS_ENABLED;
  }

  @Value.Default
  default int getTrieLogPruningWindowSize() {
    return DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE;
  }

  @Value.Default
  default DiffBasedUnstable getUnstable() {
    return DiffBasedUnstable.DEFAULT;
  }

  @Value.Immutable
  interface DiffBasedUnstable {

    DiffBasedSubStorageConfiguration.DiffBasedUnstable DEFAULT =
        ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder().build();

    DiffBasedSubStorageConfiguration.DiffBasedUnstable PARTIAL_MODE =
        ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder()
            .fullFlatDbEnabled(false)
            .build();

    DiffBasedSubStorageConfiguration.DiffBasedUnstable DISABLED =
        ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder()
            .fullFlatDbEnabled(false)
            .codeStoredByCodeHashEnabled(false)
            .isParallelTxProcessingEnabled(false)
            .build();

    boolean DEFAULT_FULL_FLAT_DB_ENABLED = true;
    boolean DEFAULT_CODE_USING_CODE_HASH_ENABLED = true;

    boolean DEFAULT_PARALLEL_TRX_ENABLED = false;

    @Value.Default
    default boolean getFullFlatDbEnabled() {
      return DEFAULT_FULL_FLAT_DB_ENABLED;
    }

    @Value.Default
    default boolean getCodeStoredByCodeHashEnabled() {
      return DEFAULT_CODE_USING_CODE_HASH_ENABLED;
    }

    @Value.Default
    default boolean isParallelTxProcessingEnabled() {
      return DEFAULT_PARALLEL_TRX_ENABLED;
    }
  }
}

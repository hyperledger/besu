/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;

import org.junit.jupiter.api.Test;

class EphemeryDatabaseResetTest {

  @Test
  void isResetNeeded_shouldReturnTrue_whenPeriodHasElapsed() {
    // Given
    long genesisTimestamp = System.currentTimeMillis() / 1000 - (29 * 24 * 60 * 60); // 29 days ago

    // When
    boolean result = EphemeryDatabaseReset.isResetNeeded(genesisTimestamp);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  void isResetNeeded_shouldReturnFalse_whenPeriodHasNotElapsed() {
    // Given
    long genesisTimestamp = System.currentTimeMillis() / 1000 - (27 * 24 * 60 * 60); // 27 days ago

    // When
    boolean result = EphemeryDatabaseReset.isResetNeeded(genesisTimestamp);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  void resetDatabase_shouldClearAllStorages() {
    // Given
    PathBasedWorldStateKeyValueStorage storage = mock(PathBasedWorldStateKeyValueStorage.class);

    // When
    EphemeryDatabaseReset.resetDatabase(storage);

    // Then
    verify(storage).clearFlatDatabase();
    verify(storage).clearTrieLog();
    verify(storage).clearTrie();
  }

  @Test
  void resetDatabase_shouldUpgradeToFullFlatDbMode_whenUsingBonsaiStorage() {
    // Given
    BonsaiWorldStateKeyValueStorage storage = mock(BonsaiWorldStateKeyValueStorage.class);

    // When
    EphemeryDatabaseReset.resetDatabase(storage);

    // Then
    verify(storage).clearFlatDatabase();
    verify(storage).clearTrieLog();
    verify(storage).clearTrie();
    verify(storage).upgradeToFullFlatDbMode();
  }
}

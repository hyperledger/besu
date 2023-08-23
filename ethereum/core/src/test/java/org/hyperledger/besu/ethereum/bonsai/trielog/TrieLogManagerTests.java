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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.trielog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedWorldStorageManager;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TrieLogManagerTests {

  BlockHeader blockHeader = new BlockHeaderTestFixture().buildHeader();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  BonsaiWorldState bonsaiWorldState;

  @Mock BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage;
  @Mock BonsaiWorldState worldState;
  @Mock BonsaiWorldStateProvider archive;
  @Mock Blockchain blockchain;
  BonsaiWorldStateUpdateAccumulator bonsaiUpdater =
      spy(new BonsaiWorldStateUpdateAccumulator(worldState, (__, ___) -> {}, (__, ___) -> {}));

  TrieLogManager trieLogManager;

  @BeforeEach
  public void setup() {
    trieLogManager =
        new CachedWorldStorageManager(
            archive,
            blockchain,
            bonsaiWorldStateKeyValueStorage,
            new NoOpMetricsSystem(),
            512,
            null);
  }

  @Test
  public void testSaveTrieLogEvent() {
    AtomicBoolean eventFired = new AtomicBoolean(false);
    trieLogManager.subscribe(
        layer -> {
          assertThat(layer).isNotNull();
          eventFired.set(true);
        });
    trieLogManager.saveTrieLog(bonsaiUpdater, Hash.ZERO, blockHeader, bonsaiWorldState);

    assertThat(eventFired.get()).isTrue();
  }
}

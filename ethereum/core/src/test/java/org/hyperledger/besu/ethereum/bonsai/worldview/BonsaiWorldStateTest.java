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

package org.hyperledger.besu.ethereum.bonsai.worldview;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiValue;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BonsaiWorldStateTest {
  @Mock TrieLogManager trieLogManager;
  @Mock CachedMerkleTrieLoader cachedMerkleTrieLoader;
  @Mock BonsaiWorldStateUpdateAccumulator bonsaiWorldStateUpdateAccumulator;
  @Mock BonsaiWorldStateKeyValueStorage.BonsaiUpdater bonsaiUpdater;

  private final Bytes code = Bytes.of(10);
  private final Hash codeHash = Hash.hash(code);
  private final Hash accountHash = Hash.hash(Address.ZERO);
  private final Address account = Address.ZERO;

  final BonsaiWorldState worldState =
      new BonsaiWorldState(
          new BonsaiWorldStateLayerStorage(
              new BonsaiWorldStateKeyValueStorage(
                  new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem())),
          cachedMerkleTrieLoader,
          trieLogManager);

  @ParameterizedTest
  @MethodSource("priorAndUpdatedEmptyAndNullBytes")
  void doesNothingWhenMarkedAsDeletedButAlreadyDeleted(final Bytes prior, final Bytes updated) {
    final Map<Address, BonsaiValue<Bytes>> codeToUpdate =
        Map.of(Address.ZERO, new BonsaiValue<>(prior, updated));
    when(bonsaiWorldStateUpdateAccumulator.getCodeToUpdate()).thenReturn(codeToUpdate);
    worldState.updateCode(Optional.of(bonsaiUpdater), bonsaiWorldStateUpdateAccumulator);

    verifyNoInteractions(bonsaiUpdater);
  }

  @ParameterizedTest
  @MethodSource("emptyAndNullBytes")
  void removesCodeWhenMarkedAsDeleted(final Bytes updated) {
    final Map<Address, BonsaiValue<Bytes>> codeToUpdate =
        Map.of(Address.ZERO, new BonsaiValue<>(code, updated));
    when(bonsaiWorldStateUpdateAccumulator.getCodeToUpdate()).thenReturn(codeToUpdate);
    worldState.updateCode(Optional.of(bonsaiUpdater), bonsaiWorldStateUpdateAccumulator);

    verify(bonsaiUpdater).removeCode(accountHash, codeHash);
  }

  @ParameterizedTest
  @MethodSource("emptyAndNullBytes")
  void addsCodeForNewCodeValue(final Bytes prior) {
    final Map<Address, BonsaiValue<Bytes>> codeToUpdate =
        Map.of(account, new BonsaiValue<>(prior, code));

    when(bonsaiWorldStateUpdateAccumulator.getCodeToUpdate()).thenReturn(codeToUpdate);
    worldState.updateCode(Optional.of(bonsaiUpdater), bonsaiWorldStateUpdateAccumulator);

    verify(bonsaiUpdater).putCode(accountHash, codeHash, code);
  }

  private static Stream<Bytes> emptyAndNullBytes() {
    return Stream.of(Bytes.EMPTY, null);
  }

  private static Stream<Arguments> priorAndUpdatedEmptyAndNullBytes() {
    return Stream.of(
        Arguments.of(null, Bytes.EMPTY),
        Arguments.of(Bytes.EMPTY, null),
        Arguments.of(null, null),
        Arguments.of(Bytes.EMPTY, Bytes.EMPTY));
  }
}

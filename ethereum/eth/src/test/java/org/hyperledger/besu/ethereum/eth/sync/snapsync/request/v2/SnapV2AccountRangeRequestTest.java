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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.trie.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.trie.RangeManager.MIN_RANGE;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncProcessState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapRequestContext;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class SnapV2AccountRangeRequestTest {

  private static final Bytes CODE_X = Bytes.fromHexString("0x6001");
  private static final Bytes CODE_Y = Bytes.fromHexString("0x6002");
  private static final Bytes CODE_Z = Bytes.fromHexString("0x6003");

  private static final Hash ACCOUNT_A = accountHash(1);
  private static final Hash ACCOUNT_B = accountHash(2);
  private static final Hash ACCOUNT_C = accountHash(3);
  private static final Hash ACCOUNT_D = accountHash(4);
  private static final Hash ACCOUNT_E = accountHash(5);

  @Test
  void skipsStoredAndRepeatedCodeWhenCodeIsStoredByHash() {
    final BonsaiWorldStateKeyValueStorage storage =
        storage(DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    storeCode(storage, ACCOUNT_A, CODE_X);

    assertThat(codeRequests(storage))
        .extracting(SnapV2BytecodeRequest::getCodeHash)
        .containsExactlyInAnyOrder(codeHash(CODE_Y), codeHash(CODE_Z));
  }

  @Test
  void requestsCodeOfEachAccountWhenCodeIsStoredByAccountHash() {
    final BonsaiWorldStateKeyValueStorage storage = storage(codeStoredByAccountHashConfig());
    storeCode(storage, ACCOUNT_A, CODE_X);

    assertThat(codeRequests(storage))
        .extracting(SnapV2BytecodeRequest::getAccountHash)
        .containsExactlyInAnyOrder(key(ACCOUNT_B), key(ACCOUNT_C), key(ACCOUNT_D));
  }

  /** A has code X, B and C share code Y, D has code Z and E has no code. */
  private static List<SnapV2BytecodeRequest> codeRequests(
      final BonsaiWorldStateKeyValueStorage storage) {
    final NavigableMap<Bytes32, Bytes> accounts = new TreeMap<>();
    accounts.put(key(ACCOUNT_A), accountValue(codeHash(CODE_X)));
    accounts.put(key(ACCOUNT_B), accountValue(codeHash(CODE_Y)));
    accounts.put(key(ACCOUNT_C), accountValue(codeHash(CODE_Y)));
    accounts.put(key(ACCOUNT_D), accountValue(codeHash(CODE_Z)));
    accounts.put(key(ACCOUNT_E), accountValue(key(Hash.EMPTY)));

    final SimpleMerklePatriciaTrie<Bytes, Bytes> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    accounts.forEach(trie::put);
    final BlockHeader pivot =
        new BlockHeaderTestFixture().stateRoot(Hash.wrap(trie.getRootHash())).buildHeader();

    final WorldStateStorageCoordinator coordinator = new WorldStateStorageCoordinator(storage);
    final SnapV2AccountRangeRequest request =
        new SnapV2AccountRangeRequest(pivot, MIN_RANGE, MAX_RANGE);
    request.addResponse(new WorldStateProofProvider(coordinator), accounts, List.of());
    assertThat(request.isResponseReceived()).isTrue();

    return request
        .getChildRequests(
            mock(SnapRequestContext.class, RETURNS_DEEP_STUBS),
            coordinator,
            mock(SnapSyncProcessState.class))
        .filter(SnapV2BytecodeRequest.class::isInstance)
        .map(SnapV2BytecodeRequest.class::cast)
        .toList();
  }

  private static BonsaiWorldStateKeyValueStorage storage(
      final DataStorageConfiguration dataStorageConfiguration) {
    return new BonsaiWorldStateKeyValueStorage(
        new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem(), dataStorageConfiguration);
  }

  private static DataStorageConfiguration codeStoredByAccountHashConfig() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(DataStorageFormat.BONSAI)
        .extraStorageConfiguration(
            ImmutableExtraStorageConfiguration.builder()
                .unstable(
                    ImmutableExtraStorageConfiguration.Unstable.builder()
                        .codeStoredByCodeHashEnabled(false)
                        .build())
                .build())
        .build();
  }

  private static void storeCode(
      final BonsaiWorldStateKeyValueStorage storage, final Hash accountHash, final Bytes code) {
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    updater.putCode(accountHash, code);
    updater.commit();
  }

  private static Bytes accountValue(final Bytes32 codeHash) {
    final PmtStateTrieAccountValue value =
        new PmtStateTrieAccountValue(1L, Wei.ONE, Hash.EMPTY_TRIE_HASH, Hash.wrap(codeHash));
    return RLP.encode(value::writeTo);
  }

  private static Bytes32 codeHash(final Bytes code) {
    return key(Hash.hash(code));
  }

  private static Bytes32 key(final Hash hash) {
    return Bytes32.wrap(hash.getBytes());
  }

  private static Hash accountHash(final int seed) {
    return Hash.hash(Bytes.of(seed));
  }
}

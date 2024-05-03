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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.CHAIN_HEAD_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FINALIZED_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FORK_HEADS;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SAFE_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SEQ_NO_STORE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VariablesStorageHelper {
  public static final Bytes VARIABLES_PREFIX = Bytes.of(1);
  public static final Hash SAMPLE_CHAIN_HEAD = Hash.fromHexStringLenient("0x01234");
  public static final Hash SAMPLE_FINALIZED = Hash.fromHexStringLenient("0x56789");
  public static final Hash SAMPLE_SAFE = Hash.fromHexStringLenient("0xabcde");
  public static final Hash FORK1 = Hash.fromHexStringLenient("0xf1357");
  public static final Hash FORK2 = Hash.fromHexStringLenient("0xf2468");
  public static final Bytes SAMPLE_FORK_HEADS =
      RLP.encode(o -> o.writeList(List.of(FORK1, FORK2), (val, out) -> out.writeBytes(val)));
  public static final Bytes SAMPLE_ERN_SEQNO = Bytes.fromHexStringLenient("0xabc123");
  public static final EnumSet<Keys> NOT_PREFIXED_KEYS = EnumSet.of(SEQ_NO_STORE);

  public static Map<Keys, Bytes> getSampleVariableValues() {
    final var variableValues = new EnumMap<Keys, Bytes>(Keys.class);
    variableValues.put(CHAIN_HEAD_HASH, SAMPLE_CHAIN_HEAD);
    variableValues.put(FINALIZED_BLOCK_HASH, SAMPLE_FINALIZED);
    variableValues.put(SAFE_BLOCK_HASH, SAMPLE_SAFE);
    variableValues.put(FORK_HEADS, SAMPLE_FORK_HEADS);
    variableValues.put(SEQ_NO_STORE, SAMPLE_ERN_SEQNO);
    return variableValues;
  }

  public static void assertNoVariablesInStorage(final KeyValueStorage kvStorage) {
    assertThat(kvStorage.streamKeys()).isEmpty();
  }

  public static void assertVariablesPresentInVariablesStorage(
      final KeyValueStorage kvVariables, final Map<Keys, Bytes> variableValues) {
    assertVariablesPresentInStorage(kvVariables, Bytes.EMPTY, variableValues);
  }

  public static void assertVariablesPresentInBlockchainStorage(
      final KeyValueStorage kvBlockchain, final Map<Keys, Bytes> variableValues) {
    assertVariablesPresentInStorage(kvBlockchain, VARIABLES_PREFIX, variableValues);
  }

  public static void assertVariablesPresentInStorage(
      final KeyValueStorage kvStorage, final Bytes prefix, final Map<Keys, Bytes> variableValues) {
    variableValues.forEach(
        (k, v) ->
            assertThat(
                    kvStorage.get(
                        Bytes.concatenate(
                                (NOT_PREFIXED_KEYS.contains(k) ? Bytes.EMPTY : prefix),
                                k.getBytes())
                            .toArrayUnsafe()))
                .contains(v.toArrayUnsafe()));
  }

  public static void assertVariablesReturnedByBlockchainStorage(
      final KeyValueStoragePrefixedKeyBlockchainStorage blockchainStorage,
      final Map<Keys, Bytes> variableValues) {
    variableValues.computeIfPresent(
        CHAIN_HEAD_HASH,
        (k, v) -> {
          assertThat(blockchainStorage.getChainHead()).isPresent().contains(bytesToHash(v));
          return v;
        });

    variableValues.computeIfPresent(
        FINALIZED_BLOCK_HASH,
        (k, v) -> {
          assertThat(blockchainStorage.getFinalized()).isPresent().contains(bytesToHash(v));
          return v;
        });

    variableValues.computeIfPresent(
        SAFE_BLOCK_HASH,
        (k, v) -> {
          assertThat(blockchainStorage.getSafeBlock()).isPresent().contains(bytesToHash(v));
          return v;
        });

    variableValues.computeIfPresent(
        FORK_HEADS,
        (k, v) -> {
          assertThat(blockchainStorage.getForkHeads())
              .containsExactlyElementsOf(
                  RLP.input(v).readList(in -> bytesToHash(in.readBytes32())));
          return v;
        });
  }

  public static void populateBlockchainStorage(
      final KeyValueStorage storage, final Map<Keys, Bytes> variableValues) {
    populateStorage(storage, VARIABLES_PREFIX, variableValues);
  }

  public static void populateVariablesStorage(
      final KeyValueStorage storage, final Map<Keys, Bytes> variableValues) {
    populateStorage(storage, Bytes.EMPTY, variableValues);
  }

  public static void populateStorage(
      final KeyValueStorage storage, final Bytes prefix, final Map<Keys, Bytes> variableValues) {
    populateVariables(storage, prefix, variableValues);
  }

  public static void populateVariables(
      final KeyValueStorage storage, final Bytes prefix, final Map<Keys, Bytes> variableValues) {
    final var tx = storage.startTransaction();
    variableValues.forEach(
        (k, v) -> putVariable(tx, (NOT_PREFIXED_KEYS.contains(k) ? Bytes.EMPTY : prefix), k, v));
    tx.commit();
  }

  public static void putVariable(
      final KeyValueStorageTransaction tx, final Bytes prefix, final Keys key, final Bytes value) {
    tx.put(Bytes.concatenate(prefix, key.getBytes()).toArrayUnsafe(), value.toArrayUnsafe());
  }

  public static Hash bytesToHash(final Bytes bytes) {
    return Hash.wrap(Bytes32.wrap(bytes, 0));
  }
}

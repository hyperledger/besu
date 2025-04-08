/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache.preloader.StemPreloader;
import org.hyperledger.besu.ethereum.trie.verkle.node.LeafNode;
import org.hyperledger.besu.ethereum.trie.verkle.node.StemNode;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;
import org.hyperledger.besu.ethereum.trie.verkle.util.SuffixTreeEncoder;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class VerkleStemFlatDbStrategyTest {

  @Mock private MetricsSystem metricsSystem;

  @Mock private Counter accountNotFoundCounter;

  @Mock private Counter accountFoundCounter;

  @Mock private Counter storageNotFoundCounter;

  @Mock private Counter storageFoundCounter;

  @Mock private Counter getAccountCounter;

  @Mock private Counter getStorageValueCounter;

  @Mock private StemPreloader stemPreloader;

  @Mock private SegmentedKeyValueStorage storage;

  @Mock private PathBasedWorldView context;

  private VerkleStemFlatDbStrategy strategy;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    initializeCounters();
    strategy = new VerkleStemFlatDbStrategy(metricsSystem, new CodeHashCodeStorageStrategy());
  }

  @Test
  void testGetFlatAccountFound() {
    final Address address = Address.fromHexString("0x1");
    final Bytes stem = prepareStemForAccount(address);
    final Bytes nonce = Bytes.repeat((byte) 0x1, 8);
    final Bytes balance = Bytes.repeat((byte) 0x1, 16);
    final Bytes codeSize = Bytes.repeat((byte) 0x1, 3);

    final Bytes32 suffix = encodeAccountProperties(nonce, balance, codeSize);
    mockStorageWithStemNode(stem, (byte) Parameters.BASIC_DATA_LEAF_KEY.toInt(), suffix);

    final Optional<VerkleAccount> result =
        strategy.getFlatAccount(address, context, stemPreloader, storage);

    assertTrue(result.isPresent());
    assertAccountProperties(result.get(), nonce, balance, codeSize);
    verifyAccountMetrics(true);
  }

  @Test
  void testGetFlatAccountNotFound() {
    final Address address = Address.fromHexString("0x1");
    final Bytes stem = prepareStemForAccount(address);
    when(storage.get(eq(TRIE_BRANCH_STORAGE), eq(stem.toArrayUnsafe())))
        .thenReturn(Optional.empty());

    final Optional<VerkleAccount> result =
        strategy.getFlatAccount(address, context, stemPreloader, storage);

    assertFalse(result.isPresent());
    verifyAccountMetrics(false);
  }

  @Test
  void testGetFlatStorageValueByStorageSlotKeyFound() {
    final Address address = Address.fromHexString("0x1");
    final StorageSlotKey slotKey = createSlotKey();
    final Bytes stem = prepareStemForSlot(address, slotKey);
    final Bytes expectedValue = Bytes32.fromHexString("0x1234");

    mockStorageWithStemNode(stem, (byte) Parameters.HEADER_STORAGE_OFFSET.toInt(), expectedValue);

    final Optional<Bytes> result =
        strategy.getFlatStorageValueByStorageSlotKey(address, slotKey, stemPreloader, storage);

    assertTrue(result.isPresent());
    assertEquals(expectedValue, result.get());
    verifyStorageMetrics(true);
  }

  @Test
  void testGetFlatStorageValueByStorageSlotKeyNotFound() {
    final Address address = Address.fromHexString("0x1");
    final StorageSlotKey slotKey = createSlotKey();
    final Bytes stem = prepareStemForSlot(address, slotKey);
    when(storage.get(eq(TRIE_BRANCH_STORAGE), eq(stem.toArrayUnsafe())))
        .thenReturn(Optional.empty());

    final Optional<Bytes> result =
        strategy.getFlatStorageValueByStorageSlotKey(address, slotKey, stemPreloader, storage);

    assertFalse(result.isPresent());
    verifyStorageMetrics(false);
  }

  private void initializeCounters() {
    mockCounter(metricsSystem, "get_account_missing_flat_database", accountNotFoundCounter);
    mockCounter(metricsSystem, "get_account_flat_database", accountFoundCounter);
    mockCounter(metricsSystem, "get_storagevalue_missing_flat_database", storageNotFoundCounter);
    mockCounter(metricsSystem, "get_storagevalue_flat_database", storageFoundCounter);
    mockCounter(metricsSystem, "get_account_total", getAccountCounter);
    mockCounter(metricsSystem, "get_storagevalue_total", getStorageValueCounter);
  }

  private void mockCounter(
      final MetricsSystem metricsSystem, final String name, final Counter counter) {
    when(metricsSystem.createCounter(eq(BesuMetricCategory.BLOCKCHAIN), eq(name), anyString()))
        .thenReturn(counter);
  }

  private Bytes32 encodeAccountProperties(
      final Bytes nonce, final Bytes balance, final Bytes codeSize) {
    Bytes32 suffix = SuffixTreeEncoder.setBalanceInValue(Bytes32.ZERO, balance);
    suffix = SuffixTreeEncoder.setNonceInValue(suffix, nonce);
    suffix = SuffixTreeEncoder.setVersionInValue(suffix, Bytes.of(1));
    return SuffixTreeEncoder.setCodeSizeInValue(suffix, codeSize);
  }

  private void mockStorageWithStemNode(final Bytes stem, final byte index, final Bytes children) {
    final StemNode<Object> stemNode = createStemNode(stem, index, children);
    when(storage.get(eq(TRIE_BRANCH_STORAGE), eq(stem.toArrayUnsafe())))
        .thenReturn(Optional.of(stemNode.getEncodedValue().toArrayUnsafe()));
  }

  private StemNode<Object> createStemNode(
      final Bytes stem, final byte index, final Bytes children) {
    final StemNode<Object> stemNode = new StemNode<>(Bytes.EMPTY, stem);
    stemNode.replaceChild(index, new LeafNode<>(Bytes.EMPTY, children));
    stemNode.replaceHash(
        Bytes32.ZERO, Bytes.EMPTY, Bytes32.ZERO, Bytes.EMPTY, Bytes32.ZERO, Bytes.EMPTY);
    return stemNode;
  }

  private Bytes prepareStemForAccount(final Address address) {
    final Bytes stem = Bytes32.random();
    when(stemPreloader.preloadAccountStem(eq(address))).thenReturn(stem);
    return stem;
  }

  private Bytes prepareStemForSlot(final Address address, final StorageSlotKey storageSlotKey) {
    final Bytes stem = Bytes32.random();
    when(stemPreloader.preloadSlotStems(eq(address), eq(storageSlotKey))).thenReturn(stem);
    return stem;
  }

  private StorageSlotKey createSlotKey() {
    return new StorageSlotKey(UInt256.valueOf(0x00));
  }

  private void assertAccountProperties(
      final VerkleAccount account, final Bytes nonce, final Bytes balance, final Bytes codeSize) {
    assertEquals(account.getNonce(), nonce.toLong());
    assertEquals(account.getBalance(), Wei.wrap(balance));
    assertEquals(account.getCodeSize(), Optional.of(codeSize.toLong()));
  }

  private void verifyAccountMetrics(final boolean isFound) {
    if (isFound) {
      verify(accountNotFoundCounter, never()).inc();
      verify(accountFoundCounter).inc();
    } else {
      verify(accountFoundCounter, never()).inc();
      verify(accountNotFoundCounter).inc();
    }
    verify(getAccountCounter).inc();
  }

  private void verifyStorageMetrics(final boolean isFound) {
    if (isFound) {
      verify(storageNotFoundCounter, never()).inc();
      verify(storageFoundCounter).inc();
    } else {
      verify(storageFoundCounter, never()).inc();
      verify(storageNotFoundCounter).inc();
    }
    verify(getStorageValueCounter).inc();
  }
}

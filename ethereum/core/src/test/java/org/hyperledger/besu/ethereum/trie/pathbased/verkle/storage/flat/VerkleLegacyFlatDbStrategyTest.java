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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
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

class VerkleLegacyFlatDbStrategyTest {

  @Mock private MetricsSystem metricsSystem;

  @Mock private Counter accountNotFoundCounter;

  @Mock private Counter accountFoundCounter;

  @Mock private Counter storageNotFoundCounter;

  @Mock private Counter storageFoundCounter;

  @Mock private Counter getAccountCounter;

  @Mock private Counter getStorageValueCounter;

  @Mock private SegmentedKeyValueStorage storage;

  @Mock private PathBasedWorldView context;

  private VerkleLegacyFlatDbStrategy strategy;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN),
            eq("get_account_missing_flat_database"),
            anyString()))
        .thenReturn(accountNotFoundCounter);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN), eq("get_account_flat_database"), anyString()))
        .thenReturn(accountFoundCounter);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN),
            eq("get_storagevalue_missing_flat_database"),
            anyString()))
        .thenReturn(storageNotFoundCounter);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN), eq("get_storagevalue_flat_database"), anyString()))
        .thenReturn(storageFoundCounter);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN), eq("get_account_total"), anyString()))
        .thenReturn(getAccountCounter);

    when(metricsSystem.createCounter(
            eq(BesuMetricCategory.BLOCKCHAIN), eq("get_storagevalue_total"), anyString()))
        .thenReturn(getStorageValueCounter);

    strategy = new VerkleLegacyFlatDbStrategy(metricsSystem, new CodeHashCodeStorageStrategy());
  }

  @Test
  void testGetFlatAccountFound() {
    Address address = Address.fromHexString("0x1");
    Bytes encodedAccount =
        new VerkleAccount(context, address, address.addressHash(), 1, Wei.ONE, 0, Hash.EMPTY, false)
            .serializeAccount();

    when(storage.get(eq(ACCOUNT_INFO_STATE), any(byte[].class)))
        .thenReturn(Optional.of(encodedAccount.toArrayUnsafe()));

    Optional<VerkleAccount> result = strategy.getFlatAccount(address, context, storage);

    assertTrue(result.isPresent());
    verify(accountNotFoundCounter, never()).inc();
    verify(accountFoundCounter).inc();
    verify(getAccountCounter).inc();
  }

  @Test
  void testGetFlatAccountNotFound() {
    Address address = Address.fromHexString("0x1");

    when(storage.get(eq(ACCOUNT_INFO_STATE), any(byte[].class))).thenReturn(Optional.empty());

    Optional<VerkleAccount> result = strategy.getFlatAccount(address, context, storage);

    assertFalse(result.isPresent());
    verify(accountFoundCounter, never()).inc();
    verify(accountNotFoundCounter).inc();
    verify(getAccountCounter).inc();
  }

  @Test
  void testGetFlatStorageValueByStorageSlotKeyFound() {
    Address address = Address.fromHexString("0x1");
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes32.random()));
    Bytes expectedValue = Bytes.fromHexString("0x1234");

    when(storage.get(eq(ACCOUNT_STORAGE_STORAGE), any(byte[].class)))
        .thenReturn(Optional.of(expectedValue.toArrayUnsafe()));

    Optional<Bytes> result =
        strategy.getFlatStorageValueByStorageSlotKey(address, slotKey, storage);

    assertTrue(result.isPresent());
    assertEquals(expectedValue, result.get());
    verify(storageNotFoundCounter, never()).inc();
    verify(storageFoundCounter).inc();
    verify(getStorageValueCounter).inc();
  }

  @Test
  void testGetFlatStorageValueByStorageSlotKeyNotFound() {
    Address address = Address.fromHexString("0x1");
    StorageSlotKey slotKey = new StorageSlotKey(UInt256.fromBytes(Bytes32.random()));

    when(storage.get(eq(ACCOUNT_STORAGE_STORAGE), any(byte[].class))).thenReturn(Optional.empty());

    Optional<Bytes> result =
        strategy.getFlatStorageValueByStorageSlotKey(address, slotKey, storage);

    assertFalse(result.isPresent());
    verify(storageFoundCounter, never()).inc();
    verify(storageNotFoundCounter).inc();
    verify(getStorageValueCounter).inc();
  }
}

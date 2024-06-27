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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.StemPreloader;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StemPreloaderTests {

  private StemPreloader stemPreLoader;
  private final List<Address> accounts =
      List.of(Address.fromHexString("0xdeadbeef"), Address.fromHexString("0xdeadbeee"));

  @BeforeEach
  public void setup() {
    stemPreLoader = new StemPreloader();
  }

  // Test to verify that a single account's trie key is correctly added to the cache during preload
  @Test
  void shouldAddAccountsStemsInCacheDuringPreload() {
    stemPreLoader.cacheAccountStems(accounts.get(0));
    Assertions.assertThat(stemPreLoader.getStemByAddressCache().size()).isEqualTo(1);
    final Map<Bytes32, Bytes> accountCache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(0));
    // Assert that the cache size for the account is exactly 1, indicating a single trie key has
    // been cached
    Assertions.assertThat(Objects.requireNonNull(accountCache).size()).isEqualTo(1);
  }

  // Test to verify that stems for multiple accounts are correctly added to the cache during
  // preload
  @Test
  void shouldAddMultipleAccountsStemsInCacheDuringPreload() {
    stemPreLoader.cacheAccountStems(accounts.get(0));
    stemPreLoader.cacheAccountStems(accounts.get(1));
    Assertions.assertThat(stemPreLoader.getStemByAddressCache().size()).isEqualTo(2);
    final Map<Bytes32, Bytes> accountCache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(0));
    // Assert that each account's cache size is 1, indicating that stems for both accounts have
    // been cached
    Assertions.assertThat(Objects.requireNonNull(accountCache).size()).isEqualTo(1);
    final Map<Bytes32, Bytes> account2Cache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(1));
    Assertions.assertThat(Objects.requireNonNull(account2Cache).size()).isEqualTo(1);
  }

  // Test to verify that header slots stems are correctly added to the cache for a single
  // account during preload
  @Test
  void shouldAddHeaderSlotsStemsInCacheDuringPreload() {
    stemPreLoader.cacheSlotStems(accounts.get(0), new StorageSlotKey(UInt256.ZERO));
    stemPreLoader.cacheSlotStems(accounts.get(0), new StorageSlotKey(UInt256.ONE));
    Assertions.assertThat(stemPreLoader.getStemByAddressCache().size()).isEqualTo(1);
    final Map<Bytes32, Bytes> accountCache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(0));
    // Assert that the cache size for the account is 1, indicating that two slots in the header
    // storage have been cached
    Assertions.assertThat(Objects.requireNonNull(accountCache).size()).isEqualTo(1);
  }

  // Test to verify that multiple slots stems are correctly added to the cache for a single
  // account during preload
  @Test
  void shouldAddMultipleSlotsStemsInCacheDuringPreload() {
    stemPreLoader.cacheSlotStems(accounts.get(0), new StorageSlotKey(UInt256.ONE));
    stemPreLoader.cacheSlotStems(accounts.get(0), new StorageSlotKey(UInt256.valueOf(64)));
    Assertions.assertThat(stemPreLoader.getStemByAddressCache().size()).isEqualTo(1);
    final Map<Bytes32, Bytes> accountCache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(0));
    // Assert that the cache size for the account is 2, indicating that slots in both the header and
    // main storage have been cached
    Assertions.assertThat(Objects.requireNonNull(accountCache).size()).isEqualTo(2);
  }

  // Test to verify that code stems are correctly added to the cache for a single account during
  // preload
  @Test
  void shouldAddCodeStemsInCacheDuringPreload() {
    stemPreLoader.cacheCodeStems(accounts.get(0), Bytes.repeat((byte) 0x01, 4096));
    Assertions.assertThat(stemPreLoader.getStemByAddressCache().size()).isEqualTo(1);
    final Map<Bytes32, Bytes> accountCache =
        stemPreLoader.getStemByAddressCache().getIfPresent(accounts.get(0));
    // Assert that the cache size for the account is 2, indicating that the code is too big and
    // multiple offsets have been cached
    Assertions.assertThat(Objects.requireNonNull(accountCache).size()).isEqualTo(2);
  }

  // Test to verify that missing stems are correctly generated and accounted for
  @Test
  void shouldGenerateMissingStems() {
    stemPreLoader.cacheAccountStems(accounts.get(0));
    Map<Bytes32, Bytes> stems =
        stemPreLoader.generateManyStems(
            accounts.get(0),
            Collections.emptyList(),
            List.of(UInt256.valueOf(64)),
            List.of(UInt256.valueOf(128)),
            true);
    // Assert that two stems (slot and code) are missing from the cache
    Assertions.assertThat(stemPreLoader.getNbMissedStems()).isEqualTo(2);
    // Assert that three stems have been generated: one for the header, one for the slot in main
    // storage, and one for the code chunk
    Assertions.assertThat(stems.size()).isEqualTo(3);
  }
}

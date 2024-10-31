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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class BonsaiAccountTest {

  @Mock BonsaiWorldState bonsaiWorldState;
  @Mock BonsaiWorldState bonsaiWorldStateAlternate;

  @Test
  void shouldCopyTrackedBonsaiAccountCorrectly() {
    final BonsaiAccount trackedAccount =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    trackedAccount.setCode(Bytes.of(1));
    final UpdateTrackingAccount<BonsaiAccount> bonsaiAccountUpdateTrackingAccount =
        new UpdateTrackingAccount<>(trackedAccount);
    bonsaiAccountUpdateTrackingAccount.setStorageValue(UInt256.ONE, UInt256.ONE);

    final BonsaiAccount expectedAccount = new BonsaiAccount(trackedAccount, bonsaiWorldState, true);
    expectedAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    assertThat(new BonsaiAccount(bonsaiWorldState, bonsaiAccountUpdateTrackingAccount))
        .isEqualToComparingFieldByField(expectedAccount);
  }

  @Test
  void shouldCopyBonsaiAccountCorrectly() {
    final BonsaiAccount account =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    account.setCode(Bytes.of(1));
    account.setStorageValue(UInt256.ONE, UInt256.ONE);
    assertThat(new BonsaiAccount(account, bonsaiWorldState, true))
        .isEqualToComparingFieldByField(account);
  }

  @Test
  void shouldBeEqualIfAttributesMatch() {
    // Basic equality test
    final BonsaiAccount account1 =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    final BonsaiAccount account2 =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    assertThat(account1.equals(account2)).isTrue();

    // Another equality test
    final BonsaiAccount account3 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x123")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    final BonsaiAccount account4 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x123")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    assertThat(account3.equals(account4)).isTrue();

    // Context and mutability should not affect equality
    final BonsaiAccount account5 =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            true);
    final BonsaiAccount account6 =
        new BonsaiAccount(
            bonsaiWorldStateAlternate,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            false);
    assertThat(account5.equals(account6)).isTrue();
  }


  @Test
  void shouldBeUnEqualIfAnyAttributesDiffer() {
    // Nonce
    final BonsaiAccount account1 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.ONE,
                    Hash.EMPTY_TRIE_HASH,
                    Hash.EMPTY,
                    true);
    final BonsaiAccount account2 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    100,
                    Wei.ONE,
                    Hash.EMPTY_TRIE_HASH,
                    Hash.EMPTY,
                    true);
    assertThat(account1.equals(account2)).isFalse();

    // Balance
    final BonsaiAccount account3 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x123")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    final BonsaiAccount account4 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x456")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    assertThat(account3.equals(account4)).isFalse();

    // Storage root
    final BonsaiAccount account5 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x123")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    final BonsaiAccount account6 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x456")),
                    Hash.hash(Bytes.fromHexString("0xfedcba")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    assertThat(account5.equals(account6)).isFalse();

    // Code hash
    final BonsaiAccount account7 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x123")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x6789")),
                    true);
    final BonsaiAccount account8 =
            new BonsaiAccount(
                    bonsaiWorldState,
                    Address.ZERO,
                    Hash.hash(Address.ZERO),
                    99,
                    Wei.of(UInt256.fromHexString("0x456")),
                    Hash.hash(Bytes.fromHexString("0xabcdef")),
                    Hash.hash(Bytes.fromHexString("0x9876")),
                    true);
    assertThat(account7.equals(account8)).isFalse();
  }
}

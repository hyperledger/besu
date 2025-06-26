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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class BonsaiAccountTest {

  @Mock BonsaiWorldState bonsaiWorldState;

  @Test
  void shouldCopyTrackedBonsaiAccountCorrectly() {
    final BonsaiAccount trackedAccount = createAccount(Optional.empty());
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
    final BonsaiAccount account = createAccount(Optional.empty());
    account.setCode(Bytes.of(1));
    account.setStorageValue(UInt256.ONE, UInt256.ONE);
    assertThat(new BonsaiAccount(account, bonsaiWorldState, true))
        .isEqualToComparingFieldByField(account);
  }

  private final PathBasedWorldView mockContext = mock(PathBasedWorldView.class);
  private final Address mockAddress = mock(Address.class);

  @Test
  void shouldHaveEmptyCodeSizeWhenRlpHasNoSize() {
    BonsaiAccount account = createAccount(Optional.of(42));

    Bytes encoded = encodeAccountWithoutCodeSize(account);

    BonsaiAccount decodedAccount = decodeAccount(encoded);
    assertThat(decodedAccount.getCodeSize()).isEmpty();
  }

  @Test
  void shouldHaveCodeSizeWhenRlpSizeIsPresent() {
    BonsaiAccount account = createAccount(Optional.of(42));

    Bytes encoded = encodeAccountWithCodeSize(account);

    BonsaiAccount decodedAccount = decodeAccount(encoded);
    assertThat(decodedAccount.getCodeSize()).isPresent();
    assertThat(decodedAccount.getCodeSize().get()).isEqualTo(42);
  }

  @Test
  void shouldNotWriteCodeSizeWhenEmpty() {
    BonsaiAccount account = createAccount(Optional.empty());

    Bytes encoded = encodeAccountWithCodeSize(account);

    RLPInput in = RLP.input(encoded);
    // Should only have 4 elements: nonce, balance, storageRoot, codeHash
    assertThat(in.readList(RLPInput::readAsRlp)).hasSize(4);
  }

  @Test
  void shouldWriteCodeSizeWhenPresent() {
    BonsaiAccount account = createAccount(Optional.of(42));

    Bytes encoded = encodeAccountWithCodeSize(account);

    RLPInput in = RLP.input(encoded);
    List<RLPInput> items = in.readList(RLPInput::readAsRlp);
    // Should have 5 elements: nonce, balance, storageRoot, codeHash, codeSize
    assertThat(items).hasSize(5);
    assertThat(items.get(4).readLongScalar()).isEqualTo(42);
  }

  private BonsaiAccount createAccount(final Optional<Integer> codeSize) {
    return new BonsaiAccount(
        bonsaiWorldState,
        Address.ZERO,
        Hash.hash(Address.ZERO),
        0,
        Wei.ONE,
        Hash.EMPTY_TRIE_HASH,
        Hash.EMPTY,
        codeSize,
        true);
  }

  private Bytes encodeAccountWithoutCodeSize(final BonsaiAccount account) {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    account.writeTo(out);
    return out.encoded();
  }

  private Bytes encodeAccountWithCodeSize(final BonsaiAccount account) {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    account.writeToWithCodeSize(out);
    return out.encoded();
  }

  private BonsaiAccount decodeAccount(final Bytes encoded) {
    return BonsaiAccount.fromRLP(mockContext, mockAddress, encoded, true);
  }
}

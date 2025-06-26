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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class BonsaiAccountTest {

  private final BonsaiWorldState bonsaiWorldState = mock(BonsaiWorldState.class);
  private final Address mockAddress = mock(Address.class);
  private final Bytes code = Bytes.repeat((byte) 1, 42);

  @Test
  void shouldCopyTrackedBonsaiAccountCorrectly() {
    final BonsaiAccount trackedAccount = createAccount(Bytes.EMPTY);
    trackedAccount.setCode(Bytes.of(1));
    final UpdateTrackingAccount<BonsaiAccount> bonsaiAccountUpdateTrackingAccount =
        new UpdateTrackingAccount<>(trackedAccount);
    bonsaiAccountUpdateTrackingAccount.setStorageValue(UInt256.ONE, UInt256.ONE);

    final BonsaiAccount expectedAccount = new BonsaiAccount(trackedAccount, bonsaiWorldState, true);
    expectedAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    BonsaiAccount copiedAccount =
        new BonsaiAccount(bonsaiWorldState, bonsaiAccountUpdateTrackingAccount);
    assertThat(copiedAccount).isEqualToComparingFieldByField(expectedAccount);
  }

  @Test
  void shouldCopyBonsaiAccountCorrectly() {
    final BonsaiAccount account = createAccount(Bytes.EMPTY);
    account.setCode(Bytes.of(1));
    account.setStorageValue(UInt256.ONE, UInt256.ONE);
    BonsaiAccount copiedAccount = new BonsaiAccount(account, bonsaiWorldState, true);
    assertThat(copiedAccount).isEqualToComparingFieldByField(account);
  }

  @Test
  void shouldFetchCodeFromWorldStateWhenCodeSizeIsNotPresent() {
    Bytes encodedAccount = encodeRlpAccount(code, false);
    BonsaiAccount decodedAccount = decodeRlpAccount(encodedAccount);
    verifyNoInteractions(bonsaiWorldState);

    // The first time we access the code size, it should fetch the code from the world state
    int codeSize = decodedAccount.getCodeSize();
    assertThat(codeSize).isEqualTo(code.size());
    verify(bonsaiWorldState, times(1)).getCode(any(), any());

    // The second time we access the code size, it should not fetch the code again
    codeSize = decodedAccount.getCodeSize();
    assertThat(codeSize).isEqualTo(code.size());
    verifyNoMoreInteractions(bonsaiWorldState);
  }

  @Test
  void shouldNotFetchCodeFromWorldStateIfCodeAlreadyLoaded() {
    Bytes encodedAccount = encodeRlpAccount(code, false);
    BonsaiAccount decodedAccount = decodeRlpAccount(encodedAccount);
    verifyNoInteractions(bonsaiWorldState);

    Bytes code = decodedAccount.getCode();
    verify(bonsaiWorldState, times(1)).getCode(any(), any());

    int codeSize = decodedAccount.getCodeSize();
    assertThat(codeSize).isEqualTo(code.size());
    // If we have already accessed the code, codeSize should not trigger another fetch
    verifyNoMoreInteractions(bonsaiWorldState);
  }

  @Test
  void shouldGetCodeFromRlpWhenCodeSizeIsPresent() {
    Bytes encodedAccount = encodeRlpAccount(code, true);
    BonsaiAccount decodedAccount = decodeRlpAccount(encodedAccount);

    int codeSize = decodedAccount.getCodeSize();

    // Verify that the code is never fetched from the world state
    verify(bonsaiWorldState, never()).getCode(any(), any());
    assertThat(codeSize).isEqualTo(code.size());
  }

  @Test
  void shouldNotEncodeCodeSizeWhenNotPresent() {
    Bytes encodedAccount = encodeRlpAccount(code, false);
    RLPInput in = RLP.input(encodedAccount);

    assertThat(in.readList(RLPInput::readAsRlp)).hasSize(4);
  }

  @Test
  void shouldEncodeCodeSizeWhenPresent() {
    Bytes encodedAccount = encodeRlpAccount(code, true);
    RLPInput in = RLP.input(encodedAccount);
    List<RLPInput> items = in.readList(RLPInput::readAsRlp);

    assertThat(items).hasSize(5);
    assertThat(items.get(4).readLongScalar()).isEqualTo(code.size());
  }

  private BonsaiAccount createAccount(final Bytes code) {
    when(bonsaiWorldState.getCode(any(), any())).thenReturn(Optional.of(code));
    BonsaiAccount bonsaiAccount =
        new BonsaiAccount(
            bonsaiWorldState,
            Address.ZERO,
            Hash.hash(Address.ZERO),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.hash(code),
            true);
    bonsaiAccount.setCode(code);
    return bonsaiAccount;
  }

  private Bytes encodeRlpAccount(final Bytes code, final boolean withCodeSize) {
    BonsaiAccount account = createAccount(code);
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    if (withCodeSize) {
      account.writeToWithCodeSize(out);
    } else {
      account.writeTo(out);
    }
    return out.encoded();
  }

  private BonsaiAccount decodeRlpAccount(final Bytes encoded) {
    return BonsaiAccount.fromRLP(bonsaiWorldState, mockAddress, encoded, true);
  }
}

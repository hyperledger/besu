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
package org.hyperledger.besu.ethereum.eth.messages.snap;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public final class AccountRangeMessageTest {

  @Test
  public void roundTripTest() {
    final Map<Bytes32, Bytes> keys = new HashMap<>();
    final PmtStateTrieAccountValue accountValue =
        new PmtStateTrieAccountValue(1L, Wei.of(2L), Hash.EMPTY_TRIE_HASH, Hash.EMPTY);
    keys.put(Hash.wrap(Bytes32.leftPad(Bytes.of(1))), RLP.encode(accountValue::writeTo));

    final List<Bytes> proofs = new ArrayList<>();
    proofs.add(Bytes32.random());

    // Perform round-trip transformation
    final MessageData initialMessage = AccountRangeMessage.create(keys, proofs);
    final MessageData raw = new RawMessage(SnapV1.ACCOUNT_RANGE, initialMessage.getData());

    final AccountRangeMessage message = AccountRangeMessage.readFrom(raw);

    // check match originals.
    final AccountRangeMessage.AccountRangeData range = message.accountData(false);
    assertThat(range.accounts()).isEqualTo(keys);
    assertThat(range.proofs()).isEqualTo(proofs);
  }

  @Test
  public void toSlimAccountTest() {
    // Initialize nonce and balance
    long nonce = 1L;
    Wei balance = Wei.of(2L);

    // Create a StateTrieAccountValue with the given nonce and balance
    final PmtStateTrieAccountValue accountValue =
        new PmtStateTrieAccountValue(nonce, balance, Hash.EMPTY_TRIE_HASH, Hash.EMPTY);

    // Encode the account value to RLP
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    accountValue.writeTo(rlpOut);

    // Convert the encoded account value to a slim account representation
    Bytes slimAccount = AccountRangeMessage.toSlimAccount(RLP.input(rlpOut.encoded()));

    // Read the slim account RLP input
    RLPInput in = RLP.input(slimAccount);
    in.enterList();

    // Verify the nonce and balance
    final long expectedNonce = in.readLongScalar();
    final Wei expectedWei = Wei.of(in.readUInt256Scalar());
    assertThat(expectedNonce).isEqualTo(nonce);
    assertThat(expectedWei).isEqualTo(balance);

    // Check that the storageRoot is empty
    assertThat(in.nextIsNull()).isTrue();
    in.skipNext();

    // Check that the codeHash is empty
    assertThat(in.nextIsNull()).isTrue();
    in.skipNext();

    // Exit the list
    in.leaveList();
  }

  @Test
  public void toFullAccountTest() {
    // Initialize nonce and balance
    long nonce = 1L;
    Wei balance = Wei.of(2L);

    // Create a StateTrieAccountValue with the given nonce and balance
    final PmtStateTrieAccountValue accountValue =
        new PmtStateTrieAccountValue(nonce, balance, Hash.EMPTY_TRIE_HASH, Hash.EMPTY);

    // Encode the account value to RLP
    final BytesValueRLPOutput rlpOut = new BytesValueRLPOutput();
    accountValue.writeTo(rlpOut);

    // Convert the encoded account value to a full account representation
    Bytes fullAccount = AccountRangeMessage.toFullAccount(RLP.input(rlpOut.encoded()));

    // Read the full account RLP input
    RLPInput in = RLP.input(fullAccount);
    in.enterList();

    // Verify the nonce and balance
    final long expectedNonce = in.readLongScalar();
    final Wei expectedWei = Wei.of(in.readUInt256Scalar());
    assertThat(expectedNonce).isEqualTo(nonce);
    assertThat(expectedWei).isEqualTo(balance);

    // Verify the storageRoot and codeHash
    assertThat(in.readBytes32()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(in.readBytes32()).isEqualTo(Hash.EMPTY);

    // Exit the list
    in.leaveList();
  }
}

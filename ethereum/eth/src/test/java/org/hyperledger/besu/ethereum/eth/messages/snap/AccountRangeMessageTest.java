/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class AccountRangeMessageTest {

  @Test
  public void roundTripTest() {
    final Map<Bytes32, Bytes> keys = new HashMap<>();
    final StateTrieAccountValue accountValue =
        new StateTrieAccountValue(1L, Wei.of(2L), Hash.EMPTY_TRIE_HASH, Hash.EMPTY);
    keys.put(Hash.wrap(Bytes32.leftPad(Bytes.of(1))), RLP.encode(accountValue::writeTo));

    final List<Bytes> proofs = new ArrayList<>();
    proofs.add(Bytes32.random());

    // Perform round-trip transformation
    final MessageData initialMessage = AccountRangeMessage.create(keys, proofs);
    final MessageData raw = new RawMessage(SnapV1.ACCOUNT_RANGE, initialMessage.getData());

    final AccountRangeMessage message = AccountRangeMessage.readFrom(raw);

    // check match originals.
    final AccountRangeMessage.AccountRangeData range = message.accountData(false);
    Assertions.assertThat(range.accounts()).isEqualTo(keys);
    Assertions.assertThat(range.proofs()).isEqualTo(proofs);
  }
}

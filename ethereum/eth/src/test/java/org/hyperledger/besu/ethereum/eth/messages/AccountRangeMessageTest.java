/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.messages;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AccountRangeMessageTest {

  @Test
  public void roundTripTest() {
    // Generate some hashes
    final Map<Bytes32, Bytes> keys = new HashMap<>();
    final StateTrieAccountValue accountValue =
            new StateTrieAccountValue(1L, Wei.of(2L), Hash.EMPTY_TRIE_HASH, Hash.EMPTY);
    keys.put(Hash.wrap(Bytes32.leftPad(Bytes.of(1))), RLP.encode(accountValue::writeTo));

    final ArrayDeque<Bytes> proofs = new ArrayDeque<>();
    proofs.add(Bytes32.random());

    // Perform round-trip transformation
    // Create GetNodeData, copy it to a generic message, then read back into a GetNodeData message
    final MessageData initialMessage = AccountRangeMessage.create(keys, proofs);
    final MessageData raw = new RawMessage(SnapV1.ACCOUNT_RANGE, initialMessage.getData());

    final AccountRangeMessage message = AccountRangeMessage.readFrom(raw);

    // check match originals.
    final AccountRangeMessage.AccountRangeData range = message.accountData(false);
    Assertions.assertThat(range.accounts()).isEqualTo(keys);
    Assertions.assertThat(range.proofs()).isEqualTo(proofs);
  }


}

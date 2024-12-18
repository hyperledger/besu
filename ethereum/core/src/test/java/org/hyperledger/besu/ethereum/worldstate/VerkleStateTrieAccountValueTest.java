/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.common.VerkleStateTrieAccountValue;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class VerkleStateTrieAccountValueTest {

  @Test
  public void roundTripMainNetAccountValue() {
    final long nonce = 0;
    final Wei balance = Wei.ZERO;
    final long codeSize = 1L;
    final Hash codeHash = Hash.EMPTY_LIST_HASH;

    roundTripMainNetAccountValue(nonce, balance, codeSize, codeHash);
  }

  private void roundTripMainNetAccountValue(
      final long nonce, final Wei balance, final long codeSize, final Hash codeHash) {

    VerkleStateTrieAccountValue accountValue =
        new VerkleStateTrieAccountValue(nonce, balance, codeHash, Optional.of(codeSize));
    Bytes encoded = RLP.encode(accountValue::writeTo);
    final RLPInput in = RLP.input(encoded);
    VerkleStateTrieAccountValue roundTripAccountValue = VerkleStateTrieAccountValue.readFrom(in);

    assertThat(nonce).isEqualTo(roundTripAccountValue.getNonce());
    assertThat(balance).isEqualTo(roundTripAccountValue.getBalance());
    assertThat(codeSize).isEqualTo(roundTripAccountValue.getCodeSize().orElseThrow());
    assertThat(codeHash).isEqualTo(roundTripAccountValue.getCodeHash());
    assertThat(Hash.EMPTY_TRIE_HASH)
        .isEqualTo(roundTripAccountValue.getStorageRoot()); // always empty for verkle
  }
}

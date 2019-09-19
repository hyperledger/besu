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
 */
package org.hyperledger.besu.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.junit.Test;

public class StateTrieAccountValueTest {

  @Test
  public void roundTripMainNetAccountValueVersionZero() {
    final long nonce = 0;
    final Wei balance = Wei.ZERO;
    // Have the storageRoot and codeHash as different values to ensure the encode / decode
    // doesn't cross the values over.
    final Hash storageRoot = Hash.EMPTY_TRIE_HASH;
    final Hash codeHash = Hash.EMPTY_LIST_HASH;
    final int version = 0;

    roundTripMainNetAccountValue(nonce, balance, storageRoot, codeHash, version);
  }

  @Test
  public void roundTripMainNetAccountValueVersionNotZero() {
    final long nonce = 0;
    final Wei balance = Wei.ZERO;
    final Hash storageRoot = Hash.EMPTY_TRIE_HASH;
    final Hash codeHash = Hash.EMPTY_LIST_HASH;
    final int version = 1;

    roundTripMainNetAccountValue(nonce, balance, storageRoot, codeHash, version);
  }

  @Test
  public void roundTripMainNetAccountValueMax() {
    final long nonce = (Long.MAX_VALUE >> 1);
    final Wei balance =
        Wei.fromHexString("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    final Hash storageRoot = Hash.EMPTY_TRIE_HASH;
    final Hash codeHash = Hash.EMPTY_LIST_HASH;
    final int version = Integer.MAX_VALUE;

    roundTripMainNetAccountValue(nonce, balance, storageRoot, codeHash, version);
  }

  private void roundTripMainNetAccountValue(
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final int version) {

    StateTrieAccountValue accountValue =
        new StateTrieAccountValue(nonce, balance, storageRoot, codeHash, version);
    BytesValue encoded = RLP.encode(accountValue::writeTo);
    final RLPInput in = RLP.input(encoded);
    StateTrieAccountValue roundTripAccountValue = StateTrieAccountValue.readFrom(in);

    assertThat(nonce).isEqualTo(roundTripAccountValue.getNonce());
    assertThat(balance).isEqualTo(roundTripAccountValue.getBalance());
    assertThat(storageRoot).isEqualTo(roundTripAccountValue.getStorageRoot());
    assertThat(codeHash).isEqualTo(roundTripAccountValue.getCodeHash());
    assertThat(version).isEqualTo(roundTripAccountValue.getVersion());
  }
}

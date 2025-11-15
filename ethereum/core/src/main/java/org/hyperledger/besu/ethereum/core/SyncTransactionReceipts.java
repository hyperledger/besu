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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class SyncTransactionReceipts {

  public static final SyncTransactionReceipts EMPTY =
      new SyncTransactionReceipts(Bytes.fromHexString("0xc0"), List.of());

  private final Bytes rlpForTransactionReceipts;
  private final List<SyncTransactionReceipt> receiptSet;

  public SyncTransactionReceipts(
      final Bytes rlpForTransactionReceipts, final List<SyncTransactionReceipt> receiptSet) {
    this.rlpForTransactionReceipts = rlpForTransactionReceipts;
    this.receiptSet = receiptSet;
  }

  public Bytes getRlp() {
    return rlpForTransactionReceipts;
  }

  public Hash getReceiptsRoot() {
    final MerkleTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(b -> b);

    IntStream.range(0, receiptSet.size())
        .forEach(i -> trie.put(indexKey(i), receiptSet.get(i).getRlp()));

    return Hash.wrap(trie.getRootHash());
  }

  public int size() {
    return receiptSet.size();
  }

  public List<Bytes> getEncodedTransactionReceipts() {
    final ArrayList<Bytes> list = new ArrayList<>(receiptSet.size());
    RLPInput rlpInput = RLP.input(rlpForTransactionReceipts);
    rlpInput.enterList();
    while (!rlpInput.isEndOfCurrentList()) {
      if (rlpInput.nextIsList()) {
        // This is for Transactions before Berlin fork
        list.add(rlpInput.currentListAsBytesNoCopy(true));
      }
    }
    return list;
  }

  private static Bytes indexKey(final int i) {
    return RLP.encodeOne(UInt256.valueOf(i).trimLeadingZeros());
  }
}

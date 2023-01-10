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
package org.hyperledger.besu.ethereum.eth.encoding;

import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocolVersion;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.ArrayList;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

public class TransactionAnnouncementEncoder {

  @FunctionalInterface
  public interface Encoder {
    Bytes encode(List<Transaction> transaction);
  }

  /**
   * Returns the correct encoder given an Eth Capability
   *
   * <p>See <a href="https://eips.ethereum.org/EIPS/eip-5793">EIP-5793</a>
   *
   * @param capability the version of the eth protocol
   * @return the correct encoder
   */
  public static Encoder getEncoder(final Capability capability) {
    if (capability.getVersion() >= EthProtocolVersion.V68) {
      return TransactionAnnouncementEncoder::encodeForEth68;
    } else {
      return TransactionAnnouncementEncoder::encodeForEth66;
    }
  }

  /**
   * Encode a list of hashes for the NewPooledTransactionHashesMessage using the Eth/66
   *
   * <p>format: [hash_0: B_32, hash_1: B_32, ...]
   *
   * @param transactions the list to encode
   * @return the encoded value. The message data will contain only the transaction hashes
   */
  private static Bytes encodeForEth66(final List<Transaction> transactions) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(toHashList(transactions), (h, w) -> w.writeBytes(h));
    return out.encoded();
  }

  /**
   * Encode a list of transactions for the NewPooledTransactionHashesMessage using the Eth/68
   *
   * <p>format: [[type_0: B_1, type_1: B_1, ...], [size_0: B_4, size_1: B_4, ...], ...]
   *
   * @param transactions the list to encode
   * @return the encoded value. The message data will contain hashes, types and sizes.
   */
  private static Bytes encodeForEth68(final List<Transaction> transactions) {
    final List<Integer> sizes = new ArrayList<>(transactions.size());
    final List<TransactionType> types = new ArrayList<>(transactions.size());
    final List<Hash> hashes = new ArrayList<>(transactions.size());
    transactions.forEach(
        transaction -> {
          types.add(transaction.getType());
          sizes.add(transaction.getSize());
          hashes.add(transaction.getHash());
        });

    return encodeForEth68(types, sizes, hashes);
  }

  @VisibleForTesting
  public static Bytes encodeForEth68(
      final List<TransactionType> types, final List<Integer> sizes, final List<Hash> hashes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    // Check if lists have the same size
    if (!(types.size() == hashes.size() && hashes.size() == sizes.size())) {
      throw new IllegalArgumentException(
          "Hashes, sizes and types must have the same number of elements");
    }
    out.startList();
    out.writeList(
        types, (h, w) -> w.writeByte(h == TransactionType.FRONTIER ? 0x00 : h.getSerializedType()));
    out.writeList(sizes, (h, w) -> w.writeInt(h));
    out.writeList(hashes, (h, w) -> w.writeBytes(h));
    out.endList();
    return out.encoded();
  }
}

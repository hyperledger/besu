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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.eth.EthProtocolVersion;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAnnouncement;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class TransactionAnnouncementDecoder {

  @FunctionalInterface
  public interface Decoder {
    List<TransactionAnnouncement> decode(RLPInput input);
  }

  /**
   * Returns the correct decoder given an Eth Capability
   *
   * <p>See <a href="https://eips.ethereum.org/EIPS/eip-5793">EIP-5793</a>
   *
   * @param capability the version of the eth protocol
   * @return the correct decoder
   */
  public static Decoder getDecoder(final Capability capability) {
    if (capability.getVersion() >= EthProtocolVersion.V68) {
      return TransactionAnnouncementDecoder::decodeForEth68;
    } else {
      return TransactionAnnouncementDecoder::decodeForEth66;
    }
  }

  /**
   * Decode the list of transactions in the NewPooledTransactionHashesMessage
   *
   * @param input input used to decode the NewPooledTransactionHashesMessage before Eth/68
   *     <p>format: [hash_0: B_32, hash_1: B_32, ...]
   * @return the list of TransactionAnnouncement decoded from the message. Only hash is present.
   *     size and type will return an Optional.empty()
   */
  private static List<TransactionAnnouncement> decodeForEth66(final RLPInput input) {
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    return hashes.stream().map(TransactionAnnouncement::new).collect(Collectors.toList());
  }

  /**
   * Decode the list of transactions in the NewPooledTransactionHashesMessage
   *
   * @param input input used to decode the NewPooledTransactionHashesMessage after Eth/68
   *     <p>format: [[type_0: B_1, type_1: B_1, ...], [size_0: P, size_1: P, ...], ...]
   * @return the list of TransactionAnnouncement decoded from the message with size, type and hash
   */
  private static List<TransactionAnnouncement> decodeForEth68(final RLPInput input) {
    input.enterList();

    final List<TransactionType> types = new ArrayList<>();
    final byte[] bytes = input.readBytes().toArray();
    for (final byte b : bytes) {
      types.add(b == 0 ? TransactionType.FRONTIER : TransactionType.of(b));
    }

    List<Long> sizes =
        input.readList(
            in -> {
              // for backward compatibility with previous Besu implementation be lenient and support
              // also unsigned int with leading zeros.
              // ToDo: this could be replaced with the simpler `RLPInput::readUnsignedIntScalar`
              // after some months it has been released, since most of the Besus
              // will be using the new implementation.
              final Bytes intBytes = in.readBytes();
              if (intBytes.size() > 4) {
                throw new RLPException(
                    "Expected max 4 bytes for unsigned int, but got " + intBytes.size() + " bytes");
              }
              return intBytes.toLong();
            });

    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    input.leaveList();
    if (!(types.size() == hashes.size() && hashes.size() == sizes.size())) {
      throw new RLPException("Hashes, sizes and types must have the same number of elements");
    }
    return TransactionAnnouncement.create(types, sizes, hashes);
  }
}

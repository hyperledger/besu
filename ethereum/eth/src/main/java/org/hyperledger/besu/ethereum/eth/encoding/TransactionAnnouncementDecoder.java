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
import org.hyperledger.besu.ethereum.eth.EthProtocolVersion;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAnnouncement;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.List;
import java.util.stream.Collectors;

public class TransactionAnnouncementDecoder {

  @FunctionalInterface
  public interface Decoder {
    List<TransactionAnnouncement> decode(RLPInput input);
  }

  public static Decoder getDecoder(final Capability capability) {
    if (capability.getVersion() >= EthProtocolVersion.V68) {
      return TransactionAnnouncementDecoder::decodeForEth68;
    } else {
      return TransactionAnnouncementDecoder::decodeForEth66;
    }
  }

  private static List<TransactionAnnouncement> decodeForEth66(final RLPInput input) {
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    return hashes.stream().map(TransactionAnnouncement::new).collect(Collectors.toList());
  }

  private static List<TransactionAnnouncement> decodeForEth68(final RLPInput input) {
    input.enterList();
    final List<TransactionType> types =
        input.readList(rlp -> TransactionType.of(rlp.readByte() & 0xff));
    final List<Integer> sizes = input.readList(RLPInput::readInt);
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    input.leaveList();
    return TransactionAnnouncement.create(types, sizes, hashes);
  }
}

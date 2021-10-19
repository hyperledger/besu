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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class BftBlockHashing {
  private final BftExtraDataCodec bftExtraDataCodec;

  public BftBlockHashing(final BftExtraDataCodec bftExtraDataCodec) {
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  /**
   * Constructs a hash of the block header suitable for signing as a committed seal. The extra data
   * in the hash uses an empty list for the committed seals.
   *
   * @param header The header for which a proposer seal is to be calculated (with or without extra
   *     data)
   * @param bftExtraData The extra data block which is to be inserted to the header once seal is
   *     calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  public Hash calculateDataHashForCommittedSeal(
      final BlockHeader header, final BftExtraData bftExtraData) {
    return Hash.hash(
        serializeHeader(
            header,
            () -> bftExtraDataCodec.encodeWithoutCommitSeals(bftExtraData),
            bftExtraDataCodec));
  }

  public Hash calculateDataHashForCommittedSeal(final BlockHeader header) {
    final BftExtraData bftExtraData = bftExtraDataCodec.decode(header);
    return Hash.hash(
        serializeHeader(
            header,
            () -> bftExtraDataCodec.encodeWithoutCommitSeals(bftExtraData),
            bftExtraDataCodec));
  }

  /**
   * Constructs a hash of the block header, but omits the committerSeals and sets round number to 0
   * (as these change on each of the potentially circulated blocks at the current chain height).
   *
   * @param header The header for which a block hash is to be calculated
   * @return the hash of the header to be used when referencing the header on the blockchain
   */
  public Hash calculateHashOfBftBlockOnchain(final BlockHeader header) {
    final BftExtraData bftExtraData = bftExtraDataCodec.decode(header);
    return Hash.hash(
        serializeHeader(
            header,
            () -> bftExtraDataCodec.encodeWithoutCommitSealsAndRoundNumber(bftExtraData),
            bftExtraDataCodec));
  }

  /**
   * Recovers the {@link Address} for each validator that contributed a committed seal to the block.
   *
   * @param header the block header that was signed by the committed seals
   * @param bftExtraData the parsed {@link BftExtraData} from the header
   * @return the addresses of validators that provided a committed seal
   */
  public List<Address> recoverCommitterAddresses(
      final BlockHeader header, final BftExtraData bftExtraData) {
    final Hash committerHash = calculateDataHashForCommittedSeal(header, bftExtraData);

    return bftExtraData.getSeals().stream()
        .map(p -> Util.signatureToAddress(p, committerHash))
        .collect(Collectors.toList());
  }

  public static Bytes serializeHeader(
      final BlockHeader header,
      final Supplier<Bytes> extraDataSerializer,
      final BftExtraDataCodec bftExtraDataCodec) {

    // create a block header which is a copy of the header supplied as parameter except of the
    // extraData field
    final BlockHeaderBuilder builder = BlockHeaderBuilder.fromHeader(header);
    builder.blockHeaderFunctions(BftBlockHeaderFunctions.forOnchainBlock(bftExtraDataCodec));

    // set the extraData field using the supplied extraDataSerializer if the block height is not 0
    if (header.getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER) {
      builder.extraData(header.getExtraData());
    } else {
      builder.extraData(extraDataSerializer.get());
    }

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    builder.buildBlockHeader().writeTo(out);
    return out.encoded();
  }
}

/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IbftBlockHashing {

  /**
   * Constructs a hash of the block header suitable for signing as a committed seal. The extra data
   * in the hash uses an empty list for the committed seals.
   *
   * @param header The header for which a proposer seal is to be calculated (with or without extra
   *     data)
   * @param ibftExtraData The extra data block which is to be inserted to the header once seal is
   *     calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  public static Hash calculateDataHashForCommittedSeal(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    return Hash.hash(serializeHeader(header, ibftExtraData::encodeWithoutCommitSeals));
  }

  public static Hash calculateDataHashForCommittedSeal(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    return Hash.hash(serializeHeader(header, ibftExtraData::encodeWithoutCommitSeals));
  }

  /**
   * Constructs a hash of the block header, but omits the committerSeals and sets round number to 0
   * (as these change on each of the potentially circulated blocks at the current chain height).
   *
   * @param header The header for which a block hash is to be calculated
   * @return the hash of the header to be used when referencing the header on the blockchain
   */
  public static Hash calculateHashOfIbftBlockOnChain(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    return Hash.hash(
        serializeHeader(header, ibftExtraData::encodeWithoutCommitSealsAndRoundNumber));
  }

  /**
   * Recovers the {@link Address} for each validator that contributed a committed seal to the block.
   *
   * @param header the block header that was signed by the committed seals
   * @param ibftExtraData the parsed {@link IbftExtraData} from the header
   * @return the addresses of validators that provided a committed seal
   */
  public static List<Address> recoverCommitterAddresses(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    final Hash committerHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(header, ibftExtraData);

    return ibftExtraData.getSeals().stream()
        .map(p -> Util.signatureToAddress(p, committerHash))
        .collect(Collectors.toList());
  }

  private static BytesValue serializeHeader(
      final BlockHeader header, final Supplier<BytesValue> extraDataSerializer) {

    // create a block header which is a copy of the header supplied as parameter except of the
    // extraData field
    final BlockHeaderBuilder builder = BlockHeaderBuilder.fromHeader(header);
    builder.blockHeaderFunctions(IbftBlockHeaderFunctions.forOnChainBlock());

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

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
package org.hyperledger.besu.consensus.ibftlegacy;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class IbftBlockHashing {

  private static final Bytes COMMIT_MSG_CODE = Bytes.wrap(new byte[] {2});

  /**
   * Constructs a hash of the block header, suitable for use when creating the proposer seal. The
   * extra data is modified to have a null proposer seal and empty list of committed seals.
   *
   * @param header The header for which a proposer seal is to be calculated
   * @param ibftExtraData The extra data block which is to be inserted to the header once seal is
   *     calculated
   * @return the hash of the header suitable for signing as the proposer seal
   */
  public static Hash calculateDataHashForProposerSeal(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    final Bytes headerRlp =
        serializeHeader(header, () -> encodeExtraDataWithoutCommittedSeals(ibftExtraData, null));

    // Proposer hash is the hash of the hash
    return Hash.hash(Hash.hash(headerRlp));
  }

  /**
   * Constructs a hash of the block header suitable for signing as a committed seal. The extra data
   * in the hash uses an empty list for the committed seals.
   *
   * @param header The header for which a proposer seal is to be calculated (without extra data)
   * @param ibftExtraData The extra data block which is to be inserted to the header once seal is
   *     calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  public static Hash calculateDataHashForCommittedSeal(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    // The data signed by a committer is an array of [Hash, COMMIT_MSG_CODE]
    final Hash dataHash = Hash.hash(serializeHeaderWithoutCommittedSeals(header, ibftExtraData));
    final Bytes seal = Bytes.wrap(dataHash, COMMIT_MSG_CODE);
    return Hash.hash(seal);
  }

  /**
   * Constructs a hash of the block header, but omits the committerSeals (as this changes on each of
   * the potentially circulated blocks at the current chain height).
   *
   * @param header The header for which a block hash is to be calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  public static Hash calculateHashOfIbftBlockOnchain(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    return Hash.hash(serializeHeaderWithoutCommittedSeals(header, ibftExtraData));
  }

  private static Bytes serializeHeaderWithoutCommittedSeals(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    return serializeHeader(
        header,
        () -> encodeExtraDataWithoutCommittedSeals(ibftExtraData, ibftExtraData.getProposerSeal()));
  }

  /**
   * Recovers the proposer's {@link Address} from the proposer seal.
   *
   * @param header the block header that was signed by the proposer seal
   * @param ibftExtraData the parsed IBftExtraData from the header
   * @return the proposer address
   */
  public static Address recoverProposerAddress(
      final BlockHeader header, final IbftExtraData ibftExtraData) {
    final Hash proposerHash = calculateDataHashForProposerSeal(header, ibftExtraData);
    return Util.signatureToAddress(ibftExtraData.getProposerSeal(), proposerHash);
  }

  /**
   * Recovers the {@link Address} for each validator that contributed a committed seal to the block.
   *
   * @param header the block header that was signed by the committed seals
   * @param ibftExtraData the parsed IBftExtraData from the header
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

  private static Bytes encodeExtraDataWithoutCommittedSeals(
      final IbftExtraData ibftExtraData, final SECPSignature proposerSeal) {
    final BytesValueRLPOutput extraDataEncoding = new BytesValueRLPOutput();
    extraDataEncoding.startList();
    extraDataEncoding.writeList(
        ibftExtraData.getValidators(), (validator, rlp) -> rlp.writeBytes(validator));

    if (proposerSeal != null) {
      extraDataEncoding.writeBytes(proposerSeal.encodedBytes());
    } else {
      extraDataEncoding.writeNull();
    }

    // Represents an empty committer list (i.e this is not included in the hashing of the block)
    extraDataEncoding.startList();
    extraDataEncoding.endList();

    extraDataEncoding.endList();

    return Bytes.wrap(ibftExtraData.getVanityData(), extraDataEncoding.encoded());
  }

  private static Bytes serializeHeader(
      final BlockHeader header, final Supplier<Bytes> extraDataSerializer) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();

    out.writeBytes(header.getParentHash());
    out.writeBytes(header.getOmmersHash());
    out.writeBytes(header.getCoinbase());
    out.writeBytes(header.getStateRoot());
    out.writeBytes(header.getTransactionsRoot());
    out.writeBytes(header.getReceiptsRoot());
    out.writeBytes(header.getLogsBloom());
    out.writeBytes(header.getDifficulty().toMinimalBytes());
    out.writeLongScalar(header.getNumber());
    out.writeLongScalar(header.getGasLimit());
    out.writeLongScalar(header.getGasUsed());
    out.writeLongScalar(header.getTimestamp());
    // Cannot decode an IbftExtraData on block 0 due to missing/illegal signatures
    if (header.getNumber() == 0) {
      out.writeBytes(header.getExtraData());
    } else {
      out.writeBytes(extraDataSerializer.get());
    }
    out.writeBytes(header.getMixHash());
    out.writeLong(header.getNonce());
    out.endList();
    return out.encoded();
  }
}

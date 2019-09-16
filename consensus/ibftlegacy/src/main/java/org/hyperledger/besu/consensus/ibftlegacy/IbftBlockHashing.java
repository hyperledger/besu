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
package org.hyperledger.besu.consensus.ibftlegacy;

import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IbftBlockHashing {

  private static final BytesValue COMMIT_MSG_CODE = BytesValue.wrap(new byte[] {2});

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
    final BytesValue headerRlp =
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
    final BytesValue seal = BytesValue.wrap(dataHash, COMMIT_MSG_CODE);
    return Hash.hash(seal);
  }

  /**
   * Constructs a hash of the block header, but omits the committerSeals (as this changes on each of
   * the potentially circulated blocks at the current chain height).
   *
   * @param header The header for which a block hash is to be calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  public static Hash calculateHashOfIbftBlockOnChain(final BlockHeader header) {
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);
    return Hash.hash(serializeHeaderWithoutCommittedSeals(header, ibftExtraData));
  }

  private static BytesValue serializeHeaderWithoutCommittedSeals(
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

  private static BytesValue encodeExtraDataWithoutCommittedSeals(
      final IbftExtraData ibftExtraData, final Signature proposerSeal) {
    final BytesValueRLPOutput extraDataEncoding = new BytesValueRLPOutput();
    extraDataEncoding.startList();
    extraDataEncoding.writeList(
        ibftExtraData.getValidators(), (validator, rlp) -> rlp.writeBytesValue(validator));

    if (proposerSeal != null) {
      extraDataEncoding.writeBytesValue(proposerSeal.encodedBytes());
    } else {
      extraDataEncoding.writeNull();
    }

    // Represents an empty committer list (i.e this is not included in the hashing of the block)
    extraDataEncoding.startList();
    extraDataEncoding.endList();

    extraDataEncoding.endList();

    return BytesValue.wrap(ibftExtraData.getVanityData(), extraDataEncoding.encoded());
  }

  private static BytesValue serializeHeader(
      final BlockHeader header, final Supplier<BytesValue> extraDataSerializer) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();

    out.writeBytesValue(header.getParentHash());
    out.writeBytesValue(header.getOmmersHash());
    out.writeBytesValue(header.getCoinbase());
    out.writeBytesValue(header.getStateRoot());
    out.writeBytesValue(header.getTransactionsRoot());
    out.writeBytesValue(header.getReceiptsRoot());
    out.writeBytesValue(header.getLogsBloom().getBytes());
    out.writeUInt256Scalar(header.getDifficulty());
    out.writeLongScalar(header.getNumber());
    out.writeLongScalar(header.getGasLimit());
    out.writeLongScalar(header.getGasUsed());
    out.writeLongScalar(header.getTimestamp());
    // Cannot decode an IbftExtraData on block 0 due to missing/illegal signatures
    if (header.getNumber() == 0) {
      out.writeBytesValue(header.getExtraData());
    } else {
      out.writeBytesValue(extraDataSerializer.get());
    }
    out.writeBytesValue(header.getMixHash());
    out.writeLong(header.getNonce());
    out.endList();
    return out.encoded();
  }
}

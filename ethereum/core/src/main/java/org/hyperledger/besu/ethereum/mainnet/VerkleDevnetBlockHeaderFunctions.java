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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

/** Implements the block hashing algorithm for MainNet as per the yellow paper. */
public class VerkleDevnetBlockHeaderFunctions implements BlockHeaderFunctions {

  @Override
  public Hash hash(final BlockHeader header) {
    return createHash(header);
  }

  public static Hash createHash(final BlockHeader header) {
    final Bytes rlp = RLP.encode(rlpOutput -> serializeBlockHeader(header, rlpOutput));
    return Hash.hash(rlp);
  }

  // TODO: Remove for mainnet, only needed for the current Verkle devnet.
  protected static void serializeBlockHeader(final BlockHeader blockHeader, final RLPOutput out) {
    out.startList();

    out.writeBytes(blockHeader.getParentHash());
    out.writeBytes(blockHeader.getOmmersHash());
    out.writeBytes(blockHeader.getCoinbase());
    out.writeBytes(blockHeader.getStateRoot());
    out.writeBytes(blockHeader.getTransactionsRoot());
    out.writeBytes(blockHeader.getReceiptsRoot());
    out.writeBytes(blockHeader.getLogsBloom());
    out.writeUInt256Scalar(blockHeader.getDifficulty());
    out.writeLongScalar(blockHeader.getNumber());
    out.writeLongScalar(blockHeader.getGasLimit());
    out.writeLongScalar(blockHeader.getGasUsed());
    out.writeLongScalar(blockHeader.getTimestamp());
    out.writeBytes(blockHeader.getExtraData());
    out.writeBytes(blockHeader.getMixHashOrPrevRandao());
    out.writeLong(blockHeader.getNonce());
    do {
      if (blockHeader.getBaseFee().isEmpty()) break;
      out.writeUInt256Scalar(blockHeader.getBaseFee().get());

      if (blockHeader.getWithdrawalsRoot().isEmpty()) break;
      out.writeBytes(blockHeader.getWithdrawalsRoot().get());
    } while (false);
    out.endList();
  }

  @Override
  public ParsedExtraData parseExtraData(final BlockHeader header) {
    return null;
  }
}

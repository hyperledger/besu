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

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;

/** The Bft helpers. */
public class BftHelpers {

  /** The constant EXPECTED_MIX_HASH. */
  public static final Hash EXPECTED_MIX_HASH =
      Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365");

  /** Default constructor. */
  private BftHelpers() {}

  /**
   * Calculate required validator quorum int.
   *
   * @param validatorCount the validator count
   * @return the int
   */
  public static int calculateRequiredValidatorQuorum(final int validatorCount) {
    return Util.fastDivCeiling(2 * validatorCount, 3);
  }

  /**
   * Prepare message count for quorum.
   *
   * @param quorum the quorum
   * @return the long
   */
  public static long prepareMessageCountForQuorum(final long quorum) {
    return quorum - 1;
  }

  /**
   * Create sealed block.
   *
   * @param bftExtraDataCodec the bft extra data codec
   * @param block the block
   * @param roundNumber the round number
   * @param commitSeals the commit seals
   * @return the block
   */
  public static Block createSealedBlock(
      final BftExtraDataCodec bftExtraDataCodec,
      final Block block,
      final int roundNumber,
      final Collection<SECPSignature> commitSeals) {
    final BlockHeader initialHeader = block.getHeader();
    final BftExtraData initialExtraData = bftExtraDataCodec.decode(initialHeader);

    final BftExtraData sealedExtraData =
        new BftExtraData(
            initialExtraData.getVanityData(),
            commitSeals,
            initialExtraData.getVote(),
            roundNumber,
            initialExtraData.getValidators());

    final BlockHeader sealedHeader =
        BlockHeaderBuilder.fromHeader(initialHeader)
            .extraData(bftExtraDataCodec.encode(sealedExtraData))
            .blockHeaderFunctions(BftBlockHeaderFunctions.forOnchainBlock(bftExtraDataCodec))
            .buildBlockHeader();

    return new Block(sealedHeader, block.getBody());
  }
}

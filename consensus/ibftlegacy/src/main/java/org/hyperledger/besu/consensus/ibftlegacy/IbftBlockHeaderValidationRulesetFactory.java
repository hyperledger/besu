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

import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MIN_GAS_LIMIT;

import org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules.IbftExtraDataValidationRule;
import org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules.VoteValidationRule;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

import org.apache.tuweni.units.bigints.UInt256;

public class IbftBlockHeaderValidationRulesetFactory {

  /**
   * Produces a BlockHeaderValidator configured for assessing ibft block headers which are to form
   * part of the BlockChain (i.e. not proposed blocks, which do not contain commit seals)
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @param ceil2nBy3Block the block after which 2/3n commit seals must exist, rather than 2F+1
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator.Builder ibftBlockHeaderValidator(
      final long secondsBetweenBlocks, final long ceil2nBy3Block) {
    return createValidator(secondsBetweenBlocks, true, ceil2nBy3Block);
  }

  /**
   * Produces a BlockHeaderValidator configured for assessing IBFT proposed blocks (i.e. blocks
   * which need to be vetted by the validators, and do not contain commit seals).
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator.Builder ibftProposedBlockValidator(
      final long secondsBetweenBlocks) {
    return createValidator(secondsBetweenBlocks, false, 0);
  }

  private static BlockHeaderValidator.Builder createValidator(
      final long secondsBetweenBlocks,
      final boolean validateCommitSeals,
      final long ceil2nBy3Block) {
    return new BlockHeaderValidator.Builder()
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(
            new GasLimitRangeAndDeltaValidationRule(DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT))
        .addRule(new TimestampBoundedByFutureParameter(1))
        .addRule(new TimestampMoreRecentThanParent(secondsBetweenBlocks))
        .addRule(
            new ConstantFieldValidationRule<>(
                "MixHash", BlockHeader::getMixHash, IbftHelpers.EXPECTED_MIX_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "Difficulty", BlockHeader::getDifficulty, UInt256.ONE))
        .addRule(new VoteValidationRule())
        .addRule(new IbftExtraDataValidationRule(validateCommitSeals, ceil2nBy3Block));
  }
}

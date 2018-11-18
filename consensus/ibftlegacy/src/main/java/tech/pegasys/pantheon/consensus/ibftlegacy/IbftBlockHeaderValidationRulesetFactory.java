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
package tech.pegasys.pantheon.consensus.ibftlegacy;

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibftlegacy.headervalidationrules.IbftExtraDataValidationRule;
import tech.pegasys.pantheon.consensus.ibftlegacy.headervalidationrules.VoteValidationRule;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;
import tech.pegasys.pantheon.util.uint.UInt256;

public class IbftBlockHeaderValidationRulesetFactory {

  /**
   * Produces a BlockHeaderValidator configured for assessing ibft block headers which are to form
   * part of the BlockChain (i.e. not proposed blocks, which do not contain commit seals)
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator<IbftContext> ibftBlockHeaderValidator(
      final long secondsBetweenBlocks) {
    return createValidator(secondsBetweenBlocks, true);
  }

  /**
   * Produces a BlockHeaderValidator configured for assessing IBFT proposed blocks (i.e. blocks
   * which need to be vetted by the validators, and do not contain commit seals).
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator<IbftContext> ibftProposedBlockValidator(
      final long secondsBetweenBlocks) {
    return createValidator(secondsBetweenBlocks, false);
  }

  private static BlockHeaderValidator<IbftContext> createValidator(
      final long secondsBetweenBlocks, final boolean validateCommitSeals) {
    return new BlockHeaderValidator.Builder<IbftContext>()
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(5000, 0x7fffffffffffffffL))
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
        .addRule(new IbftExtraDataValidationRule(validateCommitSeals))
        .build();
  }
}

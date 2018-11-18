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
package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CliqueDifficultyValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CliqueExtraDataValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CoinbaseHeaderValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.SignerRateLimitValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.VoteValidationRule;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

public class BlockHeaderValidationRulesetFactory {

  /**
   * Creates a set of rules which when executed will determine if a given block header is valid with
   * respect to its parent (or chain).
   *
   * <p>Specifically the set of rules provided by this function are to be used for a Clique chain.
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @param epochManager an object which determines if a given block is an epoch block.
   * @return the header validator.
   */
  public static BlockHeaderValidator<CliqueContext> cliqueBlockHeaderValidator(
      final long secondsBetweenBlocks, final EpochManager epochManager) {

    return new BlockHeaderValidator.Builder<CliqueContext>()
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(5000, 0x7fffffffffffffffL))
        .addRule(new TimestampBoundedByFutureParameter(10))
        .addRule(new TimestampMoreRecentThanParent(secondsBetweenBlocks))
        .addRule(new ConstantFieldValidationRule<>("MixHash", BlockHeader::getMixHash, Hash.ZERO))
        .addRule(
            new ConstantFieldValidationRule<>(
                "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH))
        .addRule(new CliqueExtraDataValidationRule(epochManager))
        .addRule(new VoteValidationRule())
        .addRule(new CliqueDifficultyValidationRule())
        .addRule(new SignerRateLimitValidationRule())
        .addRule(new CoinbaseHeaderValidationRule(epochManager))
        .build();
  }
}

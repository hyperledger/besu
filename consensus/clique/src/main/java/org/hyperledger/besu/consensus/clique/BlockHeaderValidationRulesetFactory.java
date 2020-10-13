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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.consensus.clique.headervalidationrules.CliqueDifficultyValidationRule;
import org.hyperledger.besu.consensus.clique.headervalidationrules.CliqueExtraDataValidationRule;
import org.hyperledger.besu.consensus.clique.headervalidationrules.CoinbaseHeaderValidationRule;
import org.hyperledger.besu.consensus.clique.headervalidationrules.SignerRateLimitValidationRule;
import org.hyperledger.besu.consensus.clique.headervalidationrules.VoteValidationRule;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559BlockHeaderGasPriceValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

import java.util.Optional;

public class BlockHeaderValidationRulesetFactory {

  /**
   * Creates a set of rules which when executed will determine if a given block header is valid with
   * respect to its parent (or chain).
   *
   * <p>Specifically the set of rules provided by this function are to be used for a Clique chain.
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @param epochManager an object which determines if a given block is an epoch block.
   * @param eip1559 an {@link Optional} wrapping {@link EIP1559} manager class if appropriate.
   * @return the header validator.
   */
  public static BlockHeaderValidator.Builder cliqueBlockHeaderValidator(
      final long secondsBetweenBlocks,
      final EpochManager epochManager,
      final Optional<EIP1559> eip1559) {

    final BlockHeaderValidator.Builder builder =
        new BlockHeaderValidator.Builder()
            .addRule(new AncestryValidationRule())
            .addRule(new GasLimitRangeAndDeltaValidationRule(5000, 0x7fffffffffffffffL))
            .addRule(new TimestampBoundedByFutureParameter(10))
            .addRule(new TimestampMoreRecentThanParent(secondsBetweenBlocks))
            .addRule(
                new ConstantFieldValidationRule<>("MixHash", BlockHeader::getMixHash, Hash.ZERO))
            .addRule(
                new ConstantFieldValidationRule<>(
                    "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH))
            .addRule(new CliqueExtraDataValidationRule(epochManager))
            .addRule(new VoteValidationRule())
            .addRule(new CliqueDifficultyValidationRule())
            .addRule(new SignerRateLimitValidationRule())
            .addRule(new CoinbaseHeaderValidationRule(epochManager));
    if (ExperimentalEIPs.eip1559Enabled && eip1559.isPresent()) {
      builder
          .addRule((new EIP1559BlockHeaderGasPriceValidationRule(eip1559.get())))
          .addRule(new GasUsageValidationRule(eip1559));
    } else {
      builder.addRule(new GasUsageValidationRule());
    }
    return builder;
  }
}

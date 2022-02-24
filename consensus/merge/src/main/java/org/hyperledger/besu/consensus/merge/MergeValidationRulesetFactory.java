/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.headervalidationrules.ConstantOmmersHashRule;
import org.hyperledger.besu.consensus.merge.headervalidationrules.MergeUnfinalizedValidationRule;
import org.hyperledger.besu.consensus.merge.headervalidationrules.NoDifficultyRule;
import org.hyperledger.besu.consensus.merge.headervalidationrules.NoNonceRule;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.PoWHasher;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Optional;

public class MergeValidationRulesetFactory {

  private static final EpochCalculator preMergeCalculator =
      new EpochCalculator.DefaultEpochCalculator();

  /**
   * Creates a set of rules which when executed will determine if a given block header is valid with
   * respect to its parent (or chain).
   *
   * <p>Specifically the set of rules provided by this function are to be used for a Mainnet Merge
   * chain.
   *
   * @param feeMarket the applicable {@link FeeMarket}, either PGA or BaseFee.
   * @return the header validator.
   */
  public static BlockHeaderValidator.Builder mergeBlockHeaderValidator(final FeeMarket feeMarket) {

    if (!feeMarket.implementsBaseFee()) {
      return MainnetBlockHeaderValidator.createPgaBlockHeaderValidator(
          preMergeCalculator, PoWHasher.ETHASH_LIGHT);
    } else {
      return MainnetBlockHeaderValidator.createBaseFeeMarketValidator(
              Optional.of(feeMarket)
                  .filter(FeeMarket::implementsBaseFee)
                  .map(BaseFeeMarket.class::cast)
                  .orElseThrow(
                      () ->
                          new RuntimeException(
                              "Invalid configuration: missing BaseFeeMarket for merge net")))
          .addRule(new MergeUnfinalizedValidationRule())
          .addRule(new ConstantOmmersHashRule())
          .addRule(new NoNonceRule())
          .addRule(new NoDifficultyRule());
    }
  }
}

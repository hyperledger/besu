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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CalculatedDifficultyValidationRule<C> implements AttachedBlockHeaderValidationRule<C> {
  private final Logger LOG = LogManager.getLogger(CalculatedDifficultyValidationRule.class);
  private final DifficultyCalculator<C> difficultyCalculator;

  public CalculatedDifficultyValidationRule(final DifficultyCalculator<C> difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext<C> context) {
    final BigInteger actualDifficulty =
        new BigInteger(1, header.getDifficulty().getBytes().extractArray());
    final BigInteger expectedDifficulty =
        difficultyCalculator.nextDifficulty(header.getTimestamp(), parent, context);

    if (actualDifficulty.compareTo(expectedDifficulty) != 0) {
      LOG.trace(
          "Invalid block header: difficulty {} does not equal expected difficulty {}",
          actualDifficulty,
          expectedDifficulty);
      return false;
    }

    return true;
  }
}

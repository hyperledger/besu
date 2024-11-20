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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalculatedDifficultyValidationRule implements AttachedBlockHeaderValidationRule {
  private static final Logger LOG =
      LoggerFactory.getLogger(CalculatedDifficultyValidationRule.class);
  private final DifficultyCalculator difficultyCalculator;

  public CalculatedDifficultyValidationRule(final DifficultyCalculator difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext context) {

    final BigInteger actualDifficulty = new BigInteger(1, header.getDifficulty().toArray());
    final BigInteger expectedDifficulty =
        difficultyCalculator.nextDifficulty(header.getTimestamp(), parent);

    if (actualDifficulty.compareTo(expectedDifficulty) != 0) {
      LOG.info(
          "Invalid block header: difficulty {} does not equal expected difficulty {}",
          actualDifficulty,
          expectedDifficulty);
      return false;
    }

    return true;
  }
}

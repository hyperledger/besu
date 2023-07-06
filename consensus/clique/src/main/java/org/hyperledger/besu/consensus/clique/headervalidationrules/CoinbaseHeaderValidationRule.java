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
package org.hyperledger.besu.consensus.clique.headervalidationrules;

import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Coinbase header validation rule. */
public class CoinbaseHeaderValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(CoinbaseHeaderValidationRule.class);

  private final EpochManager epochManager;

  /**
   * Instantiates a new Coinbase header validation rule.
   *
   * @param epochManager the epoch manager
   */
  public CoinbaseHeaderValidationRule(final EpochManager epochManager) {
    this.epochManager = epochManager;
  }

  @Override
  // The coinbase field is used for voting nodes in/out of the validator group. However, no votes
  // are allowed to be cast on epoch blocks
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (epochManager.isEpochBlock(header.getNumber())
        && !header.getCoinbase().equals(CliqueBlockInterface.NO_VOTE_SUBJECT)) {
      LOG.info(
          "Invalid block header: No clique in/out voting may occur on epoch blocks ({})",
          header.getNumber());
      return false;
    }
    return true;
  }
}

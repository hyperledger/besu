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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.headervalidationrules.BftCoinbaseValidationRule;
import org.hyperledger.besu.consensus.common.bft.headervalidationrules.BftCommitSealsValidationRule;
import org.hyperledger.besu.consensus.common.bft.headervalidationrules.BftValidatorsValidationRule;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
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
   * Produces a BlockHeaderValidator configured for assessing bft block headers which are to form
   * part of the BlockChain (i.e. not proposed blocks, which do not contain commit seals)
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing bft block headers
   */
  public static BlockHeaderValidator.Builder blockHeaderValidator(final long secondsBetweenBlocks) {
    return new BlockHeaderValidator.Builder()
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(5000, 0x7fffffffffffffffL))
        .addRule(new TimestampBoundedByFutureParameter(1))
        .addRule(new TimestampMoreRecentThanParent(secondsBetweenBlocks))
        .addRule(
            new ConstantFieldValidationRule<>(
                "MixHash", BlockHeader::getMixHash, BftHelpers.EXPECTED_MIX_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "Difficulty", BlockHeader::getDifficulty, UInt256.ONE))
        .addRule(new ConstantFieldValidationRule<>("Nonce", BlockHeader::getNonce, 0L))
        .addRule(new BftValidatorsValidationRule())
        .addRule(new BftCoinbaseValidationRule())
        .addRule(new BftCommitSealsValidationRule());
  }
}

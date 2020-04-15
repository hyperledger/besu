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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.CalculatedDifficultyValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559BlockHeaderGasLimitValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559BlockHeaderGasPriceValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ExtraDataMaxLengthValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ProofOfWorkValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

import org.apache.tuweni.bytes.Bytes;

public final class MainnetBlockHeaderValidator {

  public static final Bytes DAO_EXTRA_DATA = Bytes.fromHexString("0x64616f2d686172642d666f726b");
  private static final int MIN_GAS_LIMIT = 5000;
  private static final long MAX_GAS_LIMIT = 0x7fffffffffffffffL;
  public static final int TIMESTAMP_TOLERANCE_S = 15;
  public static final int MINIMUM_SECONDS_SINCE_PARENT = 1;
  public static final Bytes CLASSIC_FORK_BLOCK_HEADER =
      Bytes.fromHexString("0x94365e3a8c0b35089c1d1195081fe7489b528a84b22199c916180db8b28ade7f");

  public static BlockHeaderValidator.Builder<Void> create() {
    return createValidator();
  }

  public static BlockHeaderValidator.Builder<Void> createDaoValidator() {
    return createValidator()
        .addRule(
            new ConstantFieldValidationRule<>(
                "extraData", BlockHeader::getExtraData, DAO_EXTRA_DATA));
  }

  public static BlockHeaderValidator.Builder<Void> createClassicValidator() {
    return createValidator()
        .addRule(
            new ConstantFieldValidationRule<>(
                "hash",
                h -> h.getNumber() == 1920000 ? h.getBlockHash() : CLASSIC_FORK_BLOCK_HEADER,
                CLASSIC_FORK_BLOCK_HEADER));
  }

  public static boolean validateHeaderForDaoFork(final BlockHeader header) {
    return DAO_EXTRA_DATA.equals(header.getExtraData());
  }

  public static boolean validateHeaderForClassicFork(final BlockHeader header) {
    return header.getNumber() != 1_920_000 || header.getHash().equals(CLASSIC_FORK_BLOCK_HEADER);
  }

  static BlockHeaderValidator.Builder<Void> createOmmerValidator() {
    return new BlockHeaderValidator.Builder<Void>()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule());
  }

  private static BlockHeaderValidator.Builder<Void> createValidator() {
    return new BlockHeaderValidator.Builder<Void>()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new TimestampBoundedByFutureParameter(TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule());
  }

  static BlockHeaderValidator.Builder<Void> createEip1559Validator(final EIP1559 eip1559) {
    ExperimentalEIPs.eip1559MustBeEnabled();
    return new BlockHeaderValidator.Builder<Void>()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new TimestampBoundedByFutureParameter(TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(true))
        .addRule(new EIP1559BlockHeaderGasLimitValidationRule(eip1559))
        .addRule((new EIP1559BlockHeaderGasPriceValidationRule(eip1559)));
  }

  static BlockHeaderValidator.Builder<Void> createEip1559OmmerValidator(final EIP1559 eip1559) {
    ExperimentalEIPs.eip1559MustBeEnabled();
    return new BlockHeaderValidator.Builder<Void>()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(true))
        .addRule(new EIP1559BlockHeaderGasLimitValidationRule(eip1559))
        .addRule((new EIP1559BlockHeaderGasPriceValidationRule(eip1559)));
  }
}

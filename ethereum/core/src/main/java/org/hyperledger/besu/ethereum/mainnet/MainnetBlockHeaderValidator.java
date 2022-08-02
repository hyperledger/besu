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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.BaseFeeMarketBlockHeaderGasPriceValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.CalculatedDifficultyValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ExtraDataMaxLengthValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.ProofOfWorkValidationRule;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampBoundedByFutureParameter;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.TimestampMoreRecentThanParent;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public final class MainnetBlockHeaderValidator {

  public static final Bytes DAO_EXTRA_DATA = Bytes.fromHexString("0x64616f2d686172642d666f726b");
  public static final int MIN_GAS_LIMIT = 5000;
  public static final long MAX_GAS_LIMIT = 0x7fffffffffffffffL;
  public static final int TIMESTAMP_TOLERANCE_S = 15;
  public static final int MINIMUM_SECONDS_SINCE_PARENT = 1;
  public static final Bytes CLASSIC_FORK_BLOCK_HEADER =
      Bytes.fromHexString("0x94365e3a8c0b35089c1d1195081fe7489b528a84b22199c916180db8b28ade7f");

  public static BlockHeaderValidator.Builder create() {
    return createPgaFeeMarketValidator(PoWHasher.ETHASH_LIGHT);
  }

  public static BlockHeaderValidator.Builder create(final PoWHasher hasher) {
    return createPgaFeeMarketValidator(hasher);
  }

  public static BlockHeaderValidator.Builder createDaoValidator() {
    return createPgaFeeMarketValidator()
        .addRule(
            new ConstantFieldValidationRule<>(
                "extraData", BlockHeader::getExtraData, DAO_EXTRA_DATA));
  }

  public static BlockHeaderValidator.Builder createClassicValidator() {
    return createClassicValidator(PoWHasher.ETHASH_LIGHT);
  }

  public static BlockHeaderValidator.Builder createClassicValidator(final PoWHasher hasher) {
    return createPgaFeeMarketValidator(hasher)
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

  static BlockHeaderValidator.Builder createLegacyFeeMarketOmmerValidator() {
    return createLegacyFeeMarketOmmerValidator(
        new EpochCalculator.DefaultEpochCalculator(), PoWHasher.ETHASH_LIGHT);
  }

  static BlockHeaderValidator.Builder createLegacyFeeMarketOmmerValidator(final PoWHasher hasher) {
    return createLegacyFeeMarketOmmerValidator(
        new EpochCalculator.DefaultEpochCalculator(), hasher);
  }

  static BlockHeaderValidator.Builder createLegacyFeeMarketOmmerValidator(
      final EpochCalculator epochCalculator, final PoWHasher hasher) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(epochCalculator, hasher, Optional.empty()));
  }

  private static BlockHeaderValidator.Builder createPgaFeeMarketValidator() {
    return createPgaFeeMarketValidator(PoWHasher.ETHASH_LIGHT);
  }

  private static BlockHeaderValidator.Builder createPgaFeeMarketValidator(final PoWHasher hasher) {
    return createPgaBlockHeaderValidator(new EpochCalculator.DefaultEpochCalculator(), hasher);
  }

  public static BlockHeaderValidator.Builder createPgaBlockHeaderValidator(
      final EpochCalculator epochCalculator, final PoWHasher hasher) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new TimestampBoundedByFutureParameter(TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule(epochCalculator, hasher, Optional.empty()));
  }

  public static BlockHeaderValidator.Builder createBaseFeeMarketValidator(
      final BaseFeeMarket baseFeeMarket) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(
            new GasLimitRangeAndDeltaValidationRule(
                MIN_GAS_LIMIT, Long.MAX_VALUE, Optional.of(baseFeeMarket)))
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new TimestampBoundedByFutureParameter(TIMESTAMP_TOLERANCE_S))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(
            new ProofOfWorkValidationRule(
                new EpochCalculator.DefaultEpochCalculator(),
                PoWHasher.ETHASH_LIGHT,
                Optional.of(baseFeeMarket)))
        .addRule((new BaseFeeMarketBlockHeaderGasPriceValidationRule(baseFeeMarket)));
  }

  static BlockHeaderValidator.Builder createBaseFeeMarketOmmerValidator(
      final BaseFeeMarket baseFeeMarket) {
    return new BlockHeaderValidator.Builder()
        .addRule(CalculatedDifficultyValidationRule::new)
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(
            new GasLimitRangeAndDeltaValidationRule(
                MIN_GAS_LIMIT, Long.MAX_VALUE, Optional.of(baseFeeMarket)))
        .addRule(new TimestampMoreRecentThanParent(MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(
            new ProofOfWorkValidationRule(
                new EpochCalculator.DefaultEpochCalculator(),
                PoWHasher.ETHASH_LIGHT,
                Optional.of(baseFeeMarket)))
        .addRule((new BaseFeeMarketBlockHeaderGasPriceValidationRule(baseFeeMarket)));
  }
}

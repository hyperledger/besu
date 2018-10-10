package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.CalculatedDifficultyValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.ExtraDataMaxLengthValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.ProofOfWorkValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.TimestampValidationRule;
import net.consensys.pantheon.util.bytes.BytesValue;

public final class MainnetBlockHeaderValidator {

  private static final BytesValue DAO_EXTRA_DATA =
      BytesValue.fromHexString("0x64616f2d686172642d666f726b");
  private static final int MIN_GAS_LIMIT = 5000;
  private static final long MAX_GAS_LIMIT = 0x7fffffffffffffffL;
  public static final int TIMESTAMP_TOLERANCE_S = 15;
  public static final int MINIMUM_SECONDS_SINCE_PARENT = 1;

  public static BlockHeaderValidator<Void> create(
      final DifficultyCalculator<Void> difficultyCalculator) {
    return createValidator(difficultyCalculator).build();
  }

  public static BlockHeaderValidator<Void> createDaoValidator(
      final DifficultyCalculator<Void> difficultyCalculator) {
    return createValidator(difficultyCalculator)
        .addRule(
            new ConstantFieldValidationRule<>(
                "extraData", BlockHeader::getExtraData, DAO_EXTRA_DATA))
        .build();
  }

  private static BlockHeaderValidator.Builder<Void> createValidator(
      final DifficultyCalculator<Void> difficultyCalculator) {
    return new BlockHeaderValidator.Builder<Void>()
        .addRule(new CalculatedDifficultyValidationRule<>(difficultyCalculator))
        .addRule(new AncestryValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(MIN_GAS_LIMIT, MAX_GAS_LIMIT))
        .addRule(new GasUsageValidationRule())
        .addRule(new TimestampValidationRule(TIMESTAMP_TOLERANCE_S, MINIMUM_SECONDS_SINCE_PARENT))
        .addRule(new ExtraDataMaxLengthValidationRule(BlockHeader.MAX_EXTRA_DATA_BYTES))
        .addRule(new ProofOfWorkValidationRule());
  }
}

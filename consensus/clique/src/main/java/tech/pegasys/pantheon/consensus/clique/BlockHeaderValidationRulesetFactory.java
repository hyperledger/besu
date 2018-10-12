package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CliqueDifficultyValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CliqueExtraDataValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.CoinbaseHeaderValidationRule;
import tech.pegasys.pantheon.consensus.clique.headervalidationrules.SignerRateLimitValidationRule;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.headervalidationrules.VoteValidationRule;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampValidationRule;

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
        .addRule(new TimestampValidationRule(10, secondsBetweenBlocks))
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

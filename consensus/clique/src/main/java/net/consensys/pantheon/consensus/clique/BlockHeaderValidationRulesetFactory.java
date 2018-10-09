package net.consensys.pantheon.consensus.clique;

import net.consensys.pantheon.consensus.clique.headervalidationrules.CliqueDifficultyValidationRule;
import net.consensys.pantheon.consensus.clique.headervalidationrules.CliqueExtraDataValidationRule;
import net.consensys.pantheon.consensus.clique.headervalidationrules.CoinbaseHeaderValidationRule;
import net.consensys.pantheon.consensus.clique.headervalidationrules.SignerRateLimitValidationRule;
import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.headervalidationrules.VoteValidationRule;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import net.consensys.pantheon.ethereum.mainnet.headervalidationrules.TimestampValidationRule;

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

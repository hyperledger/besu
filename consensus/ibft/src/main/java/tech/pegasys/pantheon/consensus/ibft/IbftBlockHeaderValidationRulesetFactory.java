package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.common.headervalidationrules.VoteValidationRule;
import tech.pegasys.pantheon.consensus.ibft.headervalidationrules.IbftExtraDataValidationRule;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.AncestryValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.ConstantFieldValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.GasUsageValidationRule;
import tech.pegasys.pantheon.ethereum.mainnet.headervalidationrules.TimestampValidationRule;
import tech.pegasys.pantheon.util.uint.UInt256;

public class IbftBlockHeaderValidationRulesetFactory {

  /**
   * Produces a BlockHeaderValidator configured for assessing ibft block headers which are to form
   * part of the BlockChain (i.e. not proposed blocks, which do not contain commit seals)
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator<IbftContext> ibftBlockHeaderValidator(
      final long secondsBetweenBlocks) {
    return createValidator(secondsBetweenBlocks, true);
  }

  /**
   * Produces a BlockHeaderValidator configured for assessing IBFT proposed blocks (i.e. blocks
   * which need to be vetted by the validators, and do not contain commit seals).
   *
   * @param secondsBetweenBlocks the minimum number of seconds which must elapse between blocks.
   * @return BlockHeaderValidator configured for assessing ibft block headers
   */
  public static BlockHeaderValidator<IbftContext> ibftProposedBlockValidator(
      final long secondsBetweenBlocks) {
    return createValidator(secondsBetweenBlocks, false);
  }

  private static BlockHeaderValidator<IbftContext> createValidator(
      final long secondsBetweenBlocks, final boolean validateCommitSeals) {
    return new BlockHeaderValidator.Builder<IbftContext>()
        .addRule(new AncestryValidationRule())
        .addRule(new GasUsageValidationRule())
        .addRule(new GasLimitRangeAndDeltaValidationRule(5000, 0x7fffffffffffffffL))
        .addRule(new TimestampValidationRule(1, secondsBetweenBlocks))
        .addRule(
            new ConstantFieldValidationRule<>(
                "MixHash", BlockHeader::getMixHash, IbftHelpers.EXPECTED_MIX_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "OmmersHash", BlockHeader::getOmmersHash, Hash.EMPTY_LIST_HASH))
        .addRule(
            new ConstantFieldValidationRule<>(
                "Difficulty", BlockHeader::getDifficulty, UInt256.ONE))
        .addRule(new VoteValidationRule())
        .addRule(new IbftExtraDataValidationRule(validateCommitSeals))
        .build();
  }
}

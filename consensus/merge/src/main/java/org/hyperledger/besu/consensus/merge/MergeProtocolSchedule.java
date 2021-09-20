package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.math.BigInteger;

public class MergeProtocolSchedule {

  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.valueOf(1);

  public static ProtocolSchedule create(
      final GenesisConfigOptions config, final boolean isRevertReasonEnabled) {
    return create(config, PrivacyParameters.DEFAULT, isRevertReasonEnabled);
  }

  public static ProtocolSchedule create(
      final GenesisConfigOptions config,
      final PrivacyParameters privacyParameters,
      final boolean isRevertReasonEnabled) {

    return new ProtocolScheduleBuilder(
            config,
            DEFAULT_CHAIN_ID,
            ProtocolSpecAdapters.create(0, builder -> applyMergeSpecificModifications(builder)),
            privacyParameters,
            isRevertReasonEnabled,
            config.isQuorum())
        .createProtocolSchedule();
  }

  private static ProtocolSpecBuilder applyMergeSpecificModifications(
      final ProtocolSpecBuilder specBuilder) {

    return specBuilder
        // TODO: do we need a merge specific blockheader validator?
        // .blockHeaderValidatorBuilder()

        // TODO: merge doesn't have or need ommers, but the blockImporter will need it for sync,
        // override or leave?
        // .ommerHeaderValidatorBuilder()

        .blockHeaderValidatorBuilder(feeMarket -> getBlockHeaderValidator(feeMarket))
        .blockReward(Wei.ZERO)
        .skipZeroBlockRewards(true);
  }

  private static BlockHeaderValidator.Builder getBlockHeaderValidator(final FeeMarket feeMarket) {
    return MergeValidationRulesetFactory.mergeBlockHeaderValidator(feeMarket);
  }
}

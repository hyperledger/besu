package org.hyperledger.besu.ethereum.epoch;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyCalculators;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;

public class EpochProtocolSchedule {
    // todo ed change to meet epoch needs
    public static ProtocolSchedule create(
            final GenesisConfigOptions config,
            final PrivacyParameters privacyParameters,
            final boolean isRevertReasonEnabled) {
        return new ProtocolScheduleBuilder(
                config,
                builder -> builder.difficultyCalculator(FixedDifficultyCalculators.calculator(config)),
                privacyParameters,
                isRevertReasonEnabled)
                .createProtocolSchedule();
    }
}

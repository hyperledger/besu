package org.hyperledger.besu.ethereum.epoch;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

public class EpochCalculators {
    public static boolean isEpochActivationInConfig(final GenesisConfigOptions config) {
        return config.getEthashConfigOptions().getEpochLengthActivationBlock().isPresent();
    }

    public static long activationBlock(final GenesisConfigOptions config) {
        return config.getEthashConfigOptions().getEpochLengthActivationBlock().getAsLong();
    }
}

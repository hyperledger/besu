package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;

@FunctionalInterface
public interface MiningBeneficiaryCalculator {
  Address calculateBeneficiary(BlockHeader header);
}

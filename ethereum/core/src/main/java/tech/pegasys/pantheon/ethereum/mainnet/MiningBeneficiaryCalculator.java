package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

@FunctionalInterface
public interface MiningBeneficiaryCalculator {
  Address calculateBeneficiary(BlockHeader header);
}

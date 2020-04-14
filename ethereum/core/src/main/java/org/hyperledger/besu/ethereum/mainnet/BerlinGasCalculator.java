package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Gas;

public class BerlinGasCalculator extends IstanbulGasCalculator {

  private static final Gas BEGIN_SUB_GAS_COST = Gas.of(1);

  @Override
  // as https://eips.ethereum.org/EIPS/eip-2315
  public Gas getBeginSubGasCost() {
    return BEGIN_SUB_GAS_COST;
  }
}

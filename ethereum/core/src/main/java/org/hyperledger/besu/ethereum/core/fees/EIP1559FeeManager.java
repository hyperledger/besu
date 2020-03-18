package org.hyperledger.besu.ethereum.core.fees;

import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.*;
import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.TARGET_GAS_USED;

public class EIP1559FeeManager {

  public long computeBaseFee(final long parentBaseFee, final long blockGasUsed) {
    long delta = blockGasUsed - TARGET_GAS_USED;
    if (delta < 0) {
      delta = -delta;
    }
    return parentBaseFee
        + ((parentBaseFee * delta) / TARGET_GAS_USED / BASEFEE_MAX_CHANGE_DENOMINATOR);
  }

  public boolean isValidBaseFee(final long parentBaseFee, final long baseFee) {
    return Math.abs(baseFee - parentBaseFee)
        <= Math.max(1, parentBaseFee / BASEFEE_MAX_CHANGE_DENOMINATOR);
  }
}

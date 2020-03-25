package org.hyperledger.besu.ethereum.core.fees;

import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.BASEFEE_MAX_CHANGE_DENOMINATOR;
import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.EIP1559_DECAY_RANGE;
import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.EIP1559_GAS_INCREMENT_AMOUNT;
import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.MAX_GAS_EIP1559;
import static org.hyperledger.besu.ethereum.core.fees.EIP1559Config.TARGET_GAS_USED;

public class EIP1559Manager {

  // TODO TBD INITIAL_FORK_BLKNUM
  private final long INITIAL_FORK_BLKNUM = 0L;
  private final long FINAL_FORK_BLKNUM = INITIAL_FORK_BLKNUM + EIP1559_DECAY_RANGE;

  public long computeBaseFee(final long parentBaseFee, final long parentBlockGasUsed) {
    long delta = parentBlockGasUsed - TARGET_GAS_USED;
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

  public long eip1559GasPool(final long blockNumber) {
    if (blockNumber >= FINAL_FORK_BLKNUM) {
      return MAX_GAS_EIP1559;
    }
    return (MAX_GAS_EIP1559 / 2)
        + ((blockNumber - INITIAL_FORK_BLKNUM) * EIP1559_GAS_INCREMENT_AMOUNT);
  }

  public long legacyGasPool(final long blockNumber) {
    return MAX_GAS_EIP1559 - eip1559GasPool(blockNumber);
  }
}

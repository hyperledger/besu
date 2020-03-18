package org.hyperledger.besu.ethereum.core.fees;

public class EIP1559Config {
  public static final long BASEFEE_MAX_CHANGE_DENOMINATOR = 8L;
  public static final long TARGET_GAS_USED = 10000000L;
  public static final long MAX_GAS_EIP1559 = 16000000L;
  public static final long EIP1559_DECAY_RANGE = MAX_GAS_EIP1559 / 20L;
  public static final long EIP1559_GAS_INCREMENT_AMOUNT =
      (MAX_GAS_EIP1559 / 2) / EIP1559_DECAY_RANGE;
  public static final long INITIAL_BASEFEE = 1000000000L;
  public static final long PER_TX_GASLIMIT = 8000000L;
}

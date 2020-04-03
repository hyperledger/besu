package org.hyperledger.besu.ethereum.core.fees;

public class FeeMarketConfig implements FeeMarket {
  private final long basefeeMaxChangeDenominator;
  private final long targetGasUsed;
  private final long maxGas;
  private final long decayRange;
  private final long gasIncrementAmount;
  private final long initialBasefee;
  private final long perTxGaslimit;

  public FeeMarketConfig(
      final long basefeeMaxChangeDenominator,
      final long targetGasUsed,
      final long maxGas,
      final long decayRange,
      final long gasIncrementAmount,
      final long initialBasefee,
      final long perTxGaslimit) {
    this.basefeeMaxChangeDenominator = basefeeMaxChangeDenominator;
    this.targetGasUsed = targetGasUsed;
    this.maxGas = maxGas;
    this.decayRange = decayRange;
    this.gasIncrementAmount = gasIncrementAmount;
    this.initialBasefee = initialBasefee;
    this.perTxGaslimit = perTxGaslimit;
  }

  @Override
  public long getBasefeeMaxChangeDenominator() {
    return basefeeMaxChangeDenominator;
  }

  @Override
  public long getTargetGasUsed() {
    return targetGasUsed;
  }

  @Override
  public long getMaxGas() {
    return maxGas;
  }

  @Override
  public long getDecayRange() {
    return decayRange;
  }

  @Override
  public long getGasIncrementAmount() {
    return gasIncrementAmount;
  }

  @Override
  public long getInitialBasefee() {
    return initialBasefee;
  }

  @Override
  public long getPerTxGaslimit() {
    return perTxGaslimit;
  }
}

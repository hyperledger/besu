package tech.pegasys.pantheon.ethereum.blockcreation;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public class MiningParameters {

  private final Optional<Address> coinbase;
  private final Wei minTransactionGasPrice;
  private final BytesValue extraData;
  private final Boolean enabled;

  public MiningParameters(
      final Address coinbase,
      final Wei minTransactionGasPrice,
      final BytesValue extraData,
      final Boolean enabled) {
    this.coinbase = Optional.ofNullable(coinbase);
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.extraData = extraData;
    this.enabled = enabled;
  }

  public Optional<Address> getCoinbase() {
    return coinbase;
  }

  public Wei getMinTransactionGasPrice() {
    return minTransactionGasPrice;
  }

  public BytesValue getExtraData() {
    return extraData;
  }

  public Boolean isMiningEnabled() {
    return enabled;
  }
}

package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.blockcreation.MiningParameters;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class MiningParametersTestBuilder {

  private Address coinbase = AddressHelpers.ofValue(1);
  private Wei minTransactionGasPrice = Wei.of(1000);
  private BytesValue extraData = BytesValue.EMPTY;
  private Boolean enabled = false;

  public MiningParametersTestBuilder coinbase(final Address coinbase) {
    this.coinbase = coinbase;
    return this;
  }

  public MiningParametersTestBuilder minTransactionGasPrice(final Wei minTransactionGasPrice) {
    this.minTransactionGasPrice = minTransactionGasPrice;
    return this;
  }

  public MiningParametersTestBuilder extraData(final BytesValue extraData) {
    this.extraData = extraData;
    return this;
  }

  public MiningParametersTestBuilder enabled(final Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public MiningParameters build() {
    return new MiningParameters(coinbase, minTransactionGasPrice, extraData, enabled);
  }
}

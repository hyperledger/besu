/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.BytesValue;

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

/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.core.feemarket;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

public class BaseFee {
  private final Wei value;
  private final Wei delta;
  private final Wei minNextValue;
  private final Wei maxNextValue;

  public BaseFee(final BaseFeeMarket feeMarket, final Wei value) {
    this.value = value;
    this.delta = value.divide(feeMarket.getBasefeeMaxChangeDenominator());
    this.minNextValue = value.subtract(delta);
    this.maxNextValue = value.add(delta);
  }

  public Wei getValue() {
    return value;
  }

  public Wei getDelta() {
    return delta;
  }

  public Wei getMinNextValue() {
    return minNextValue;
  }

  public Wei getMaxNextValue() {
    return maxNextValue;
  }
}

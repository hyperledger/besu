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

import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

public class BaseFee {
  private final long value;
  private final long delta;
  private final long minNextValue;
  private final long maxNextValue;

  public BaseFee(final BaseFeeMarket feeMarket, final long value) {
    this.value = value;
    this.delta = Math.floorDiv(value, feeMarket.getBasefeeMaxChangeDenominator());
    this.minNextValue = value - delta;
    this.maxNextValue = value + delta;
  }

  public long getValue() {
    return value;
  }

  public long getDelta() {
    return delta;
  }

  public long getMinNextValue() {
    return minNextValue;
  }

  public long getMaxNextValue() {
    return maxNextValue;
  }
}

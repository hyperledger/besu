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
package org.hyperledger.besu.ethereum.core.fees;

public interface FeeMarket {
  long getBasefeeMaxChangeDenominator();

  long getTargetGasUsed();

  long getMaxGas();

  long getDecayRange();

  long getGasIncrementAmount();

  long getInitialBasefee();

  long getPerTxGaslimit();

  double getSlackCoefficient();

  static FeeMarket eip1559() {
    return new FeeMarketConfig(8L, 10000000L, 2.0, 800000L, 10L, 1000000000L, 8000000L);
  }
}

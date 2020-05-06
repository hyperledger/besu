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

public class FeeMarketConfig implements FeeMarket {
  private final long basefeeMaxChangeDenominator;
  private final long targetGasUsed;
  private final long decayRange;
  private final long initialBasefee;
  private final long perTxGaslimit;
  private final long slackCoefficient;
  private final long maxGas;
  private final long gasIncrementAmount;

  public FeeMarketConfig(
      final long basefeeMaxChangeDenominator,
      final long targetGasUsed,
      final long slackCoefficient,
      final long decayRange,
      final long initialBasefee,
      final long perTxGaslimit) {
    this.basefeeMaxChangeDenominator = basefeeMaxChangeDenominator;
    this.targetGasUsed = targetGasUsed;
    this.slackCoefficient = slackCoefficient;
    this.decayRange = decayRange;
    this.initialBasefee = initialBasefee;
    this.perTxGaslimit = perTxGaslimit;
    this.maxGas = slackCoefficient * targetGasUsed;
    this.gasIncrementAmount = this.maxGas / 2 / this.decayRange;
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

  @Override
  public long getSlackCoefficient() {
    return slackCoefficient;
  }
}

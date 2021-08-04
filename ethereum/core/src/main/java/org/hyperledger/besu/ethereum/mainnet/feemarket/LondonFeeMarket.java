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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

public class LondonFeeMarket implements FeeMarket {
  private final Long BASEFEE_MAX_CHANGE_DENOMINATOR = 8L;

  private final Long SLACK_COEFFICIENT = 2L;

  private final Long initialBaseFee;

  public LondonFeeMarket(final Long initialBaseFee) {
    this.initialBaseFee = initialBaseFee;
  }

  @Override
  public long getBasefeeMaxChangeDenominator() {
    return BASEFEE_MAX_CHANGE_DENOMINATOR;
  }

  @Override
  public long getInitialBasefee() {
    return initialBaseFee;
  }

  @Override
  public long getSlackCoefficient() {
    return SLACK_COEFFICIENT;
  }
}

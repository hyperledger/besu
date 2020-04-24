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
package org.hyperledger.besu.ethereum.core;

public enum AcceptedTransactionTypes {
  FRONTIER_TRANSACTIONS(true, false),
  FEE_MARKET_TRANSITIONAL_TRANSACTIONS(true, true),
  FEE_MARKET_TRANSACTIONS(false, true);

  final boolean isFrontierAccepted;
  final boolean isEIP1559Accepted;

  AcceptedTransactionTypes(final boolean isFrontierAccepted, final boolean isEIP1559Accepted) {
    this.isFrontierAccepted = isFrontierAccepted;
    this.isEIP1559Accepted = isEIP1559Accepted;
  }

  public boolean isFrontierAccepted() {
    return isFrontierAccepted;
  }

  public boolean isEIP1559Accepted() {
    return isEIP1559Accepted;
  }

  @Override
  public String toString() {
    return String.format("frontier: %b / eip1559: %b", isFrontierAccepted, isEIP1559Accepted);
  }
}

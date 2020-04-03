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
  static long eip1559BasefeeMaxChangeDenominator() {
    return 8L;
  }

  static long eip1559TargetGasUsed() {
    return 10000000L;
  }

  static long eip1559MaxGas() {
    return 16000000L;
  }

  static long eip1559DecayRange() {
    return eip1559MaxGas() / 20L;
  }

  static long eip1559GasIncrementAmount() {
    return (eip1559MaxGas() / 2) / eip1559DecayRange();
  }

  static long eip1559InitialBasefee() {
    return 1000000000L;
  }

  static long eip1559PerTxGaslimit() {
    return 8000000L;
  }
}

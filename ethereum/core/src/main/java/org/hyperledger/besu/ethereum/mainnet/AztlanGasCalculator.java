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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Gas;

/*
 * This class implements changes to gas calculation for ECIP-1061 https://ecips.ethereumclassic.org/ECIPs/ecip-1061
 * ECIP-1061 is EIP-1679 without EIP-1884 implemented, this class reverses the changes made in EIP-1884.
 */
public class AztlanGasCalculator extends IstanbulGasCalculator {
  private static final Gas BALANCE_OPERATION_GAS_COST = Gas.of(400);
  private static final Gas EXTCODE_HASH_COST = Gas.of(400);
  private static final Gas SLOAD_GAS = Gas.of(200);

  @Override
  // As per https://eips.ethereum.org/EIPS/eip-1884
  public Gas getSloadOperationGasCost() {
    return SLOAD_GAS;
  }

  @Override
  public Gas getBalanceOperationGasCost() {
    return BALANCE_OPERATION_GAS_COST;
  }

  @Override
  public Gas extCodeHashOperationGasCost() {
    return EXTCODE_HASH_COST;
  }
}

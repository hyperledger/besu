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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The Create operation. */
public class CreateOperation extends AbstractCreateOperation {

  /**
   * Instantiates a new Create operation.
   *
   * @param gasCalculator the gas calculator
   * @param maxInitcodeSize Maximum init code size
   */
  public CreateOperation(final GasCalculator gasCalculator, final int maxInitcodeSize) {
    super(0xF0, "CREATE", 3, 1, gasCalculator, maxInitcodeSize);
  }

  @Override
  public long cost(final MessageFrame frame) {
    return gasCalculator().createOperationGasCost(frame);
  }

  @Override
  protected Address targetContractAddress(final MessageFrame frame) {
    final Account sender = frame.getWorldUpdater().get(frame.getRecipientAddress());
    // Decrement nonce by 1 to normalize the effect of transaction execution
    final Address address =
        Address.contractAddress(frame.getRecipientAddress(), sender.getNonce() - 1L);
    frame.warmUpAddress(address);
    return address;
  }
}

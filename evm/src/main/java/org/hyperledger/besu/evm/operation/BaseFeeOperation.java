/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BaseFeeOperation extends AbstractFixedCostOperation {

  public BaseFeeOperation(final GasCalculator gasCalculator) {
    super(0x48, "BASEFEE", 0, 1, false, 1, gasCalculator, gasCalculator.getBaseTierGasCost());
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    if (frame.getBlockValues().getBaseFee().isEmpty()) {
      return new Operation.OperationResult(
          Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INVALID_OPERATION));
    }
    frame.pushStackItem(
        frame
            .getBlockValues()
            .getBaseFee()
            .map(Bytes::ofUnsignedLong)
            .map(Bytes32::leftPad)
            .map(UInt256::fromBytes)
            .orElseThrow());
    return successResponse;
  }
}

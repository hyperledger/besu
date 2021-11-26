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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class ModOperation extends AbstractFixedCostOperation {

  public ModOperation(final GasCalculator gasCalculator) {
    super(0x06, "MOD", 2, 1, 1, gasCalculator, gasCalculator.getLowTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final Bytes value0 = frame.popStackItem();
    final Bytes value1 = frame.popStackItem();
    if (value1.isZero()) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      BigInteger b1 = new BigInteger(1, value0.toArrayUnsafe());
      BigInteger b2 = new BigInteger(1, value1.toArrayUnsafe());
      final BigInteger result = b1.mod(b2);

      Bytes resultBytes = Bytes.wrap(result.toByteArray());
      if (resultBytes.size() > 32) {
        resultBytes = resultBytes.slice(resultBytes.size() - 32, 32);
      }

      final byte[] padding = new byte[32 - resultBytes.size()];
      Arrays.fill(padding, result.signum() < 0 ? (byte) 0xFF : 0x00);

      frame.pushStackItem(Bytes.concatenate(Bytes.wrap(padding), resultBytes));
    }

    return successResponse;
  }
}

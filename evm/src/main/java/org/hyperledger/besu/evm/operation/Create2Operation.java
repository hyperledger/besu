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

import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Create2 operation. */
public class Create2Operation extends AbstractCreateOperation {

  private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

  /**
   * Instantiates a new Create2 operation.
   *
   * @param gasCalculator the gas calculator
   */
  public Create2Operation(final GasCalculator gasCalculator) {
    super(0xF5, "CREATE2", 4, 1, gasCalculator, 0);
  }

  @Override
  public long cost(final MessageFrame frame, final Supplier<Code> unused) {
    final int inputOffset = clampedToInt(frame.getStackItem(1));
    final int inputSize = clampedToInt(frame.getStackItem(2));
    return clampedAdd(
        clampedAdd(
            gasCalculator().txCreateCost(),
            gasCalculator().memoryExpansionGasCost(frame, inputOffset, inputSize)),
        clampedAdd(
            gasCalculator().createKeccakCost(inputSize), gasCalculator().initcodeCost(inputSize)));
  }

  @Override
  public Address generateTargetContractAddress(final MessageFrame frame, final Code initcode) {
    final Address sender = frame.getRecipientAddress();
    final Bytes32 salt = Bytes32.leftPad(frame.getStackItem(3));
    final Bytes32 hash = keccak256(Bytes.concatenate(PREFIX, sender, salt, initcode.getCodeHash()));
    return Address.extract(hash);
  }

  @Override
  protected Code getInitCode(final MessageFrame frame, final EVM evm) {
    final long inputOffset = clampedToLong(frame.getStackItem(1));
    final long inputSize = clampedToLong(frame.getStackItem(2));
    final Bytes inputData = frame.readMemory(inputOffset, inputSize);
    // Never cache CREATEx initcode. The amount of reuse is very low, and caching mostly
    // addresses disk loading delay, and we already have the code.
    return evm.getCodeUncached(inputData);
  }
}

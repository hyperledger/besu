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
public class Create3Operation extends AbstractCreateOperation {

  private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

  /**
   * Instantiates a new Create3 operation.
   *
   * @param gasCalculator the gas calculator
   * @param maxInitcodeSize Maximum init code size
   */
  public Create3Operation(final GasCalculator gasCalculator, final int maxInitcodeSize) {
    super(0xEC, "CREATE3", 4, 1, gasCalculator, maxInitcodeSize, 1);
  }

  @Override
  public long cost(final MessageFrame frame, final Supplier<Code> codeSupplier) {
    return gasCalculator().create3OperationGasCost(codeSupplier.get());
  }

  @Override
  public Address targetContractAddress(final MessageFrame frame, final Code targetCode) {
    final Address sender = frame.getRecipientAddress();
    final Bytes32 salt = Bytes32.leftPad(frame.getStackItem(1));
    final Bytes32 hash =
        keccak256(Bytes.concatenate(PREFIX, sender, salt, targetCode.getCodeHash()));
    final Address address = Address.extract(hash);
    frame.warmUpAddress(address);
    return address;
  }

  @Override
  protected Code getCode(final MessageFrame frame, final EVM evm) {
    final Code code = frame.getCode();
    int startIndex = frame.getPC() + 1;
    final int initContainerIndex = code.readU8(startIndex);

    return code.getSubContainer(initContainerIndex, Bytes.EMPTY).orElse(null);
  }

  @Override
  protected Bytes getAuxData(final MessageFrame frame) {
    final long inputOffset = clampedToLong(frame.getStackItem(2));
    final long inputSize = clampedToLong(frame.getStackItem(3));
    return frame.readMemory(inputOffset, inputSize);
  }

  @Override
  protected int getPcIncrement() {
    return 2;
  }
}

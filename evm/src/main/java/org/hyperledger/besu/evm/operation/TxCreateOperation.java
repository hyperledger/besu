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
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Create2 operation. */
public class TxCreateOperation extends AbstractCreateOperation {

  /** Opcode 0xEC for operation TXCREATE */
  public static final int OPCODE = 0xed;

  private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

  /**
   * Instantiates a new TXCreate operation.
   *
   * @param gasCalculator the gas calculator
   * @param maxInitcodeSize Maximum init code size
   */
  public TxCreateOperation(final GasCalculator gasCalculator, final int maxInitcodeSize) {
    super(OPCODE, "TXCREATE", 5, 1, gasCalculator, maxInitcodeSize, 1);
  }

  @Override
  public long cost(final MessageFrame frame, final Supplier<Code> codeSupplier) {
    Code code = codeSupplier.get();
    if (code == null) {
      return 0;
    } else {
      int codeSize = code.getSize();
      final int inputOffset = clampedToInt(frame.getStackItem(2));
      final int inputSize = clampedToInt(frame.getStackItem(3));
      return clampedAdd(
          clampedAdd(
              gasCalculator().memoryExpansionGasCost(frame, inputOffset, inputSize),
              gasCalculator().initcodeCost(codeSize)),
          clampedAdd(
              gasCalculator().txCreateCost(),
              gasCalculator().createKeccakCost(codeSupplier.get().getSize())));
    }
  }

  @Override
  public Address targetContractAddress(final MessageFrame frame, final Code initcode) {
    final Address sender = frame.getRecipientAddress();
    final Bytes32 salt = Bytes32.leftPad(frame.getStackItem(1));
    final Bytes32 hash = keccak256(Bytes.concatenate(PREFIX, sender, salt, initcode.getCodeHash()));
    final Address address = Address.extract(hash);
    frame.warmUpAddress(address);
    return address;
  }

  @Override
  protected Code getInitCode(final MessageFrame frame, final EVM evm) {
    Bytes bytes = frame.getInitCodeByHash(frame.getStackItem(4));
    if (bytes == null) {
      return null;
    }
    Code code = CodeFactory.createCode(bytes, eofVersion, true);
    if (code.isValid() && code.getEofVersion() > 0) {
      return code;
    } else {
      return null;
    }
  }

  @Override
  protected Bytes getInputData(final MessageFrame frame) {
    final long inputOffset = clampedToLong(frame.getStackItem(2));
    final long inputSize = clampedToLong(frame.getStackItem(3));
    return frame.readMemory(inputOffset, inputSize);
  }

  @Override
  protected int getPcIncrement() {
    return 2;
  }
}

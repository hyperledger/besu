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
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
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
   */
  public TxCreateOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "TXCREATE", 5, 1, gasCalculator, 1);
  }

  @Override
  public long cost(final MessageFrame frame, final Supplier<Code> codeSupplier) {
    Code code = codeSupplier.get();
    if (code == null) {
      return 0;
    } else {
      int codeSize = code.getSize();
      final long inputOffset = getInputOffset(frame);
      final long inputSize = getInputSize(frame);
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
  public Address generateTargetContractAddress(final MessageFrame frame, final Code _code) {
    final Address sender = frame.getRecipientAddress();
    final Bytes32 salt = getSalt(frame);
    final Address address = calculateEOFAddress(sender, salt);
    frame.warmUpAddress(address);
    return address;
  }

  /**
   * Calculates the address for TXCREATE and EOFCREATE. `keccak256(0xff || address || salt)`
   *
   * @param sender the sender address
   * @param salt the salt
   * @return the contract address
   */
  public static Address calculateEOFAddress(final Address sender, final Bytes32 salt) {
    final Bytes32 hash = keccak256(Bytes.concatenate(PREFIX, sender, salt));
    return Address.extract(hash);
  }

  @Override
  protected Code getInitCode(final MessageFrame frame, final EVM evm) {
    Bytes bytes = frame.getInitCodeByHash(getInitcodeHash(frame));
    if (bytes == null) {
      return null;
    }
    // getCodeForCreation will not error on extra data past end of code
    Code code = evm.getCodeUncached(bytes);
    if (code.isValid() && code.getEofVersion() > 0) {
      return code;
    } else {
      return null;
    }
  }

  @Override
  protected Bytes getInputData(final MessageFrame frame) {
    final long inputOffset = getInputOffset(frame);
    final long inputSize = getInputSize(frame);
    return frame.readMemory(inputOffset, inputSize);
  }

  @Override
  protected int getPcIncrement() {
    return 2;
  }

  @Override
  protected long getInputOffset(final MessageFrame frame) {
    return clampedToLong(frame.getStackItem(2));
  }

  @Override
  protected long getInputSize(final MessageFrame frame) {
    return clampedToLong(frame.getStackItem(3));
  }

  @Override
  protected Wei getValue(final MessageFrame frame) {
    return Wei.wrap(frame.getStackItem(4));
  }

  @Override
  protected Bytes32 getSalt(final MessageFrame frame) {
    return Bytes32.leftPad(frame.getStackItem(1));
  }

  /**
   * The Initcode hash to create
   *
   * @param frame the message frame
   * @return the hashcode of the initcode in the initcode transaction
   */
  protected Bytes32 getInitcodeHash(final MessageFrame frame) {
    return Bytes32.leftPad(frame.getStackItem(0));
  }
}

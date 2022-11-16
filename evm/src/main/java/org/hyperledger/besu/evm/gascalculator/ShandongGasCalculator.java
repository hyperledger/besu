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
package org.hyperledger.besu.evm.gascalculator;

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ShandongGasCalculator extends LondonGasCalculator {

  public static final int MAX_INITCODE_SIZE = 2 * 24576;

  public ShandongGasCalculator() {}

  @Override
  public long createOperationGasCost(final MessageFrame frame) {
    final long initCodeOffset = clampedToLong(frame.getStackItem(1));
    final long initCodeLength = clampedToLong(frame.getStackItem(2));

    final long memoryGasCost = memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
    final long initCodeCost = clampedMultiply(wordSize(initCodeLength),2L);

    return clampedAdd(CREATE_OPERATION_GAS_COST, clampedAdd(memoryGasCost, initCodeCost));
  }

  private long wordSize(final long length) {
    return clampedAdd(length,31L) / 32;
  }
}

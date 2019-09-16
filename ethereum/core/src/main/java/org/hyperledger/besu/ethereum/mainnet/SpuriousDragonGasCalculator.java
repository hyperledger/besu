/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.uint.UInt256;

public class SpuriousDragonGasCalculator extends TangerineWhistleGasCalculator {

  private static final Gas EXP_OPERATION_BYTE_GAS_COST = Gas.of(50L);

  @Override
  public Gas callOperationGasCost(
      final MessageFrame frame,
      final Gas stipend,
      final UInt256 inputDataOffset,
      final UInt256 inputDataLength,
      final UInt256 outputDataOffset,
      final UInt256 outputDataLength,
      final Wei transferValue,
      final Account recipient) {
    final Gas inputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, inputDataOffset, inputDataLength);
    final Gas outputDataMemoryExpansionCost =
        memoryExpansionGasCost(frame, outputDataOffset, outputDataLength);
    final Gas memoryExpansionCost = inputDataMemoryExpansionCost.max(outputDataMemoryExpansionCost);

    Gas cost = callOperationBaseGasCost().plus(memoryExpansionCost);

    if (!transferValue.isZero()) {
      cost = cost.plus(callValueTransferGasCost());
    }

    if ((recipient == null || recipient.isEmpty()) && !transferValue.isZero()) {
      cost = cost.plus(newAccountGasCost());
    }

    return cost;
  }

  @Override
  protected Gas expOperationByteGasCost() {
    return EXP_OPERATION_BYTE_GAS_COST;
  }

  private static final Gas SELFDESTRUCT_OPERATION_GAS_COST = Gas.of(5_000L);

  private static final Gas SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT = Gas.of(30_000L);

  @Override
  public Gas selfDestructOperationGasCost(final Account recipient, final Wei inheritance) {
    if ((recipient == null || recipient.isEmpty()) && !inheritance.isZero()) {
      return SELFDESTRUCT_OPERATION_CREATES_NEW_ACCOUNT;
    } else {
      return SELFDESTRUCT_OPERATION_GAS_COST;
    }
  }
}

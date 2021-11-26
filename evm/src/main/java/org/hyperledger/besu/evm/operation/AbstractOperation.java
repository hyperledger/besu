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

import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * All {@link Operation} implementations should inherit from this class to get the setting of some
 * members for free.
 */
public abstract class AbstractOperation implements Operation {

  private final int opcode;
  private final String name;
  private final int stackItemsConsumed;
  private final int stackItemsProduced;
  private final int opSize;
  private final GasCalculator gasCalculator;

  protected AbstractOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final int opSize,
      final GasCalculator gasCalculator) {
    this.opcode = opcode & 0xff;
    this.name = name;
    this.stackItemsConsumed = stackItemsConsumed;
    this.stackItemsProduced = stackItemsProduced;
    this.opSize = opSize;
    this.gasCalculator = gasCalculator;
  }

  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public int getOpcode() {
    return opcode;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getStackItemsConsumed() {
    return stackItemsConsumed;
  }

  @Override
  public int getStackItemsProduced() {
    return stackItemsProduced;
  }

  @Override
  public int getOpSize() {
    return opSize;
  }
}

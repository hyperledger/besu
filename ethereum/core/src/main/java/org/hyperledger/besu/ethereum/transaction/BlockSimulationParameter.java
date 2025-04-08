/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.transaction;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

public class BlockSimulationParameter {

  static final BlockSimulationParameter EMPTY = new BlockSimulationParameter(List.of(), false);

  final List<BlockStateCall> blockStateCalls;
  private final boolean validation;

  public BlockSimulationParameter(final List<BlockStateCall> blockStateCalls) {
    this.blockStateCalls = blockStateCalls;
    this.validation = false;
  }

  public BlockSimulationParameter(final BlockStateCall blockStateCall) {
    this(List.of(blockStateCall));
  }

  public BlockSimulationParameter(final BlockStateCall blockStateCall, final boolean validation) {
    this(List.of(blockStateCall), validation);
  }

  public BlockSimulationParameter(
      final List<BlockStateCall> blockStateCalls, final boolean validation) {
    checkNotNull(blockStateCalls);
    this.blockStateCalls = blockStateCalls;
    this.validation = validation;
  }

  public List<BlockStateCall> getBlockStateCalls() {
    return blockStateCalls;
  }

  public boolean isValidation() {
    return validation;
  }
}

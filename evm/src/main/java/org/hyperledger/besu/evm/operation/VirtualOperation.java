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

/** The Virtual operation. */
public class VirtualOperation implements Operation {

  private final Operation delegate;

  /**
   * Instantiates a new Virtual operation.
   *
   * @param delegate the delegate
   */
  public VirtualOperation(final Operation delegate) {
    this.delegate = delegate;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    OperationResult result = delegate.execute(frame, evm);
    return new OperationResult(result.getGasCost(), result.getHaltReason(), 0);
  }

  @Override
  public int getOpcode() {
    return delegate.getOpcode();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public int getStackItemsConsumed() {
    return delegate.getStackItemsConsumed();
  }

  @Override
  public int getStackItemsProduced() {
    return delegate.getStackItemsProduced();
  }

  @Override
  public boolean isVirtualOperation() {
    return true;
  }
}

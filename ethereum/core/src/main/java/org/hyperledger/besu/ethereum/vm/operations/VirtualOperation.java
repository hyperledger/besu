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

package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation;

public class VirtualOperation implements Operation {

  private final Operation delegate;

  public VirtualOperation(final Operation delegate) {
    this.delegate = delegate;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return delegate.cost(frame);
  }

  @Override
  public void execute(final MessageFrame frame) {
    delegate.execute(frame);
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
  public boolean getUpdatesProgramCounter() {
    return delegate.getUpdatesProgramCounter();
  }

  @Override
  public int getOpSize() {
    return delegate.getOpSize();
  }

  @Override
  public boolean isVirtualOperation() {
    return true;
  }
}

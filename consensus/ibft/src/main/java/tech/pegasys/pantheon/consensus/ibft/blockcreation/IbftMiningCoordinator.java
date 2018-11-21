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
package tech.pegasys.pantheon.consensus.ibft.blockcreation;

import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class IbftMiningCoordinator implements MiningCoordinator {

  private final IbftBlockCreatorFactory blockCreatorFactory;

  public IbftMiningCoordinator(final IbftBlockCreatorFactory blockCreatorFactory) {
    this.blockCreatorFactory = blockCreatorFactory;
  }

  @Override
  public void enable() {}

  @Override
  public void disable() {}

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public void setMinTransactionGasPrice(final Wei minGasPrice) {}

  @Override
  public Wei getMinTransactionGasPrice() {
    return null;
  }

  @Override
  public void setExtraData(final BytesValue extraData) {
    blockCreatorFactory.setExtraData(extraData);
  }
}

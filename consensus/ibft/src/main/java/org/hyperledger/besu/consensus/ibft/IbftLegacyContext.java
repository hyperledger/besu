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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.PoaContext;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.ethereum.ConsensusContext;

/** Holds the BFT specific mutable state. */
public class IbftLegacyContext implements PoaContext {

  private final ValidatorProvider validatorProvider;
  private final EpochManager epochManager;
  private final BlockInterface blockInterface;

  public IbftLegacyContext(
      final ValidatorProvider validatorProvider,
      final EpochManager epochManager,
      final BlockInterface blockInterface) {
    this.validatorProvider = validatorProvider;
    this.epochManager = epochManager;
    this.blockInterface = blockInterface;
  }

  public ValidatorProvider getValidatorProvider() {
    return validatorProvider;
  }

  public EpochManager getEpochManager() {
    return epochManager;
  }

  @Override
  public BlockInterface getBlockInterface() {
    return blockInterface;
  }

  @Override
  public <C extends ConsensusContext> C as(final Class<C> klass) {
    return klass.cast(this);
  }
}

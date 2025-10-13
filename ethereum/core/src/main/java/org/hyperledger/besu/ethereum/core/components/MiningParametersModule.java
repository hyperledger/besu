/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core.components;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

@Module
public class MiningParametersModule {

  @Provides
  @Named("defaultMiningParameters")
  protected MiningConfiguration createImmutableMiningParams() {
    return ImmutableMiningConfiguration.builder().build();
  }

  @Provides
  @Named("noMining")
  protected MiningConfiguration createNoMining() {
    return ImmutableMiningConfiguration.builder()
        .mutableInitValues(
            ImmutableMiningConfiguration.MutableInitValues.builder().isMiningEnabled(false).build())
        .build();
  }

  @Provides
  @Named("zeroGas")
  MiningConfiguration createZeroGasMining(final @Named("emptyCoinbase") Address coinbase) {
    final MiningConfiguration miningConfiguration =
        ImmutableMiningConfiguration.builder()
            .mutableInitValues(
                ImmutableMiningConfiguration.MutableInitValues.builder()
                    .isMiningEnabled(true)
                    .minTransactionGasPrice(Wei.ZERO)
                    .coinbase(coinbase)
                    .build())
            .build();
    return miningConfiguration;
  }

  @Provides
  MiningConfiguration provideMiningParameters() {
    throw new IllegalStateException("unimplemented");
  }
}

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
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.EVM;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * This class, ProtocolModule, is a Dagger module that provides dependencies for the ProtocolSpec
 * and EVM. It includes the GenesisFileModule, which provides the genesis configuration for the
 * blockchain.
 *
 * <p>The class uses Dagger annotations to define these dependencies, which can be provided via the
 * command line when running the EVM tool. Each dependency has a corresponding provider method that
 * Dagger uses to inject the dependency's value where needed.
 */
@SuppressWarnings("WeakerAccess")
@Module(includes = GenesisFileModule.class)
public class ProtocolModule {

  /**
   * Default constructor for the ProtocolModule class. This constructor doesn't take any arguments
   * and doesn't perform any initialization.
   */
  public ProtocolModule() {}

  @Provides
  @Singleton
  ProtocolSpec getProtocolSpec(final ProtocolSchedule protocolSchedule) {
    return protocolSchedule.getByBlockHeader(
        BlockHeaderBuilder.createDefault()
            .timestamp(Long.MAX_VALUE)
            .number(Long.MAX_VALUE)
            .buildBlockHeader());
  }

  @Provides
  @Singleton
  EVM getEVM(final ProtocolSpec protocolSpec) {
    return protocolSpec.getEvm();
  }
}

/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.MetricsConfigurationModule;
import org.hyperledger.besu.metrics.MetricsSystemModule;

import java.util.function.Function;
import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(
    modules = {
      ProtocolModule.class,
      GenesisFileModule.class,
      DataStoreModule.class,
      BlockchainModule.class,
      EvmToolCommandOptionsModule.class,
      MetricsConfigurationModule.class,
      MetricsSystemModule.class,
    })
public interface EvmToolComponent {

  Function<BlockHeader, ProtocolSpec> getProtocolSpec();

  WorldUpdater getWorldUpdater();

  MutableWorldState getWorldState();

  Blockchain getBlockchain();
}

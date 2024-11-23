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
package org.hyperledger.besu.services;

import org.hyperledger.besu.datatypes.BlockOverrides;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.BlockSimulationResult;
import org.hyperledger.besu.ethereum.transaction.BlockSimulator;
import org.hyperledger.besu.ethereum.transaction.BlockStateCall;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockSimulationService;

import java.util.List;

public class BlockSimulatorServiceImpl implements BlockSimulationService {
  private final BlockSimulator blockSimulator;

  public BlockSimulatorServiceImpl(
      final WorldStateArchive worldStateArchive,
      final Blockchain blockchain,
      final MiningConfiguration miningConfiguration,
      final ProtocolSchedule protocolSchedule,
      final long rpcGasCap) {

    blockSimulator =
        new BlockSimulator(
            blockchain,
            worldStateArchive,
            protocolSchedule,
            rpcGasCap,
            () -> miningConfiguration.getCoinbase().orElseThrow(),
            miningConfiguration::getTargetGasLimit);
  }

  @Override
  public BlockContext simulate(
      final BlockHeader parentHeader,
      final List<? extends Transaction> transactions,
      final BlockOverrides blockOverrides) {

    org.hyperledger.besu.ethereum.core.BlockHeader parentHeaderCore =
        (org.hyperledger.besu.ethereum.core.BlockHeader) parentHeader;

    List<CallParameter> callParameters =
        transactions.stream().map(CallParameter::fromTransaction).toList();

    BlockStateCall blockStateCall = new BlockStateCall(callParameters, blockOverrides);

    BlockSimulationResult result = blockSimulator.simulate(parentHeaderCore, blockStateCall);

    if (result.getResult().isFailed()) {
      throw new IllegalArgumentException("Unable to create block.");
    }

    if (result.getBlock().isEmpty()) {
      throw new IllegalArgumentException("Unable to create block.");
    }
    return new BlockContext() {
      @Override
      public BlockHeader getBlockHeader() {
        return result.getBlock().get().getHeader();
      }

      @Override
      public BlockBody getBlockBody() {
        return result.getBlock().get().getBody();
      }
    };
  }
}

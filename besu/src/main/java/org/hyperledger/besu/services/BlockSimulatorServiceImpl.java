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

import org.hyperledger.besu.datatypes.AccountOverrideMap;
import org.hyperledger.besu.datatypes.BlockOverrides;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.BlockSimulator;
import org.hyperledger.besu.ethereum.transaction.BlockStateCall;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.BlockSimulationResult;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.BlockSimulationService;

import java.util.List;

public class BlockSimulatorServiceImpl implements BlockSimulationService {
  private final BlockSimulator blockSimulator;
  private final WorldStateArchive worldStateArchive;

  public BlockSimulatorServiceImpl(
      final WorldStateArchive worldStateArchive,
      final MiningConfiguration miningConfiguration,
      final TransactionSimulator transactionSimulator,
      final ProtocolSchedule protocolSchedule) {
    blockSimulator =
        new BlockSimulator(
            worldStateArchive, protocolSchedule, transactionSimulator, miningConfiguration);
    this.worldStateArchive = worldStateArchive;
  }

  /**
   * Simulate the processing of a block given a header, a list of transactions, and blockOverrides.
   *
   * @param header the header
   * @param transactions the transactions to include in the block
   * @param blockOverrides the blockSimulationOverride of the block
   * @return the block context
   */
  @Override
  public BlockSimulationResult simulate(
      final BlockHeader header,
      final List<? extends Transaction> transactions,
      final BlockOverrides blockOverrides) {
    BlockStateCall blockStateCall = createBlockStateCall(transactions, blockOverrides);
    var headerCore = (org.hyperledger.besu.ethereum.core.BlockHeader) header;

    var result = blockSimulator.process(headerCore, List.of(blockStateCall));
    return response(result.getFirst());
  }

  /**
   * This method is experimental and should be used with caution
   *
   * @param header the block header
   * @param transactions the transactions to include in the block
   * @param blockOverrides the blockSimulationOverride of the block
   * @return the block context
   */
  @Unstable
  @Override
  public BlockSimulationResult importBlockUnsafe(
      final BlockHeader header,
      final List<? extends Transaction> transactions,
      final BlockOverrides blockOverrides) {

    BlockStateCall blockStateCall = createBlockStateCall(transactions, blockOverrides);
    var headerCore = (org.hyperledger.besu.ethereum.core.BlockHeader) header;
    try (final MutableWorldState ws =
        worldStateArchive
            .getMutable(headerCore, true)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Public world state not available for block "
                            + headerCore.toLogString()))) {
      var results = blockSimulator.process(headerCore, List.of(blockStateCall), ws);
      var result = results.getFirst();
      ws.persist(result.getBlock().getHeader());
      return response(result);
    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  private BlockStateCall createBlockStateCall(
      final List<? extends Transaction> transactions, final BlockOverrides blockOverrides) {
    var callParameters = transactions.stream().map(CallParameter::fromTransaction).toList();
    return new BlockStateCall(callParameters, blockOverrides, new AccountOverrideMap(), true);
  }

  private BlockSimulationResult response(
      final org.hyperledger.besu.ethereum.transaction.BlockSimulationResult result) {
    return new BlockSimulationResult(
        result.getBlockHeader(),
        result.getBlockBody(),
        result.getReceipts(),
        result.getTransactionSimulations().stream()
            .map(
                simulation ->
                    new TransactionSimulationResult(simulation.transaction(), simulation.result()))
            .toList());
  }
}

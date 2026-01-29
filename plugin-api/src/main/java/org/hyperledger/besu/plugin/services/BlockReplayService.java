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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.BlockReplayResult;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Default implementation of the {@link BlockReplayService}.
 *
 * <p>This service replays blocks within a given range, allowing inspection of the execution process
 * using an {@link BlockAwareOperationTracer}. It can be used for debugging, tracing, or analyzing
 * block execution results in the context of the blockchain state.
 *
 * <p>The replay mechanism ensures that:
 *
 * <ul>
 *   <li>The correct protocol spec is applied per block, according to the protocol schedule.
 *   <li>The associated world state is retrieved for each block header.
 *   <li>Optional user-defined actions can be applied before and after block processing using {@link
 *       Consumer} callbacks on the {@link WorldView}.
 * </ul>
 */
@Unstable
public interface BlockReplayService extends BesuService {
  /**
   * Replays a range of blocks
   *
   * @param fromBlockNumber the beginning of the range (inclusive)
   * @param toBlockNumber the end of the range (inclusive)
   * @param beforeTracing Function which performs an operation on a WorldView before tracing
   * @param afterTracing Function which performs an operation on a WorldView after tracing
   * @param tracer an instance of OperationTracer
   * @return a list of BlockProcessingResult, one per block in the range
   */
  List<BlockReplayResult> replayBlocks(
      final long fromBlockNumber,
      final long toBlockNumber,
      final Consumer<WorldView> beforeTracing,
      final Consumer<WorldView> afterTracing,
      final BlockAwareOperationTracer tracer);
}

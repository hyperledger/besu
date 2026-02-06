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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;

/**
 * A BlockAwareOperationTracer implementation that aggregates multiple BlockAwareOperationTracer
 * instances and delegates all tracing calls to them in sequence.
 */
public class BlockAwareTracerAggregator implements BlockAwareOperationTracer {

  private final List<BlockAwareOperationTracer> tracers;

  /**
   * Create a BlockAwareTracerAggregator from a list of tracers.
   *
   * @param tracers the tracers to aggregate
   */
  public BlockAwareTracerAggregator(final List<BlockAwareOperationTracer> tracers) {
    this.tracers = Collections.unmodifiableList(tracers);
  }

  /**
   * Create a BlockAwareTracerAggregator from an array of tracers.
   *
   * @param tracers the tracers to aggregate
   */
  public BlockAwareTracerAggregator(final BlockAwareOperationTracer... tracers) {
    this.tracers = Collections.unmodifiableList(Arrays.asList(tracers));
  }

  /**
   * Create a BlockAwareTracerAggregator that combines an existing tracer with additional tracers.
   *
   * @param baseTracer the base tracer to extend
   * @param additionalTracers additional tracers to add
   * @return a new BlockAwareTracerAggregator combining all tracers
   */
  public static BlockAwareTracerAggregator combining(
      final BlockAwareOperationTracer baseTracer,
      final BlockAwareOperationTracer... additionalTracers) {
    if (baseTracer == BlockAwareOperationTracer.NO_TRACING) {
      return new BlockAwareTracerAggregator(additionalTracers);
    }

    final BlockAwareOperationTracer[] allTracers =
        new BlockAwareOperationTracer[additionalTracers.length + 1];
    allTracers[0] = baseTracer;
    System.arraycopy(additionalTracers, 0, allTracers, 1, additionalTracers.length);

    return new BlockAwareTracerAggregator(allTracers);
  }

  /**
   * Create a BlockAwareTracerAggregator from multiple tracers, filtering out NO_TRACING instances.
   *
   * @param tracers the tracers to aggregate
   * @return a BlockAwareTracerAggregator, or NO_TRACING if no actual tracers are provided
   */
  public static BlockAwareOperationTracer of(final BlockAwareOperationTracer... tracers) {
    final BlockAwareOperationTracer[] filteredTracers =
        Arrays.stream(tracers)
            .filter(tracer -> tracer != BlockAwareOperationTracer.NO_TRACING)
            .toArray(BlockAwareOperationTracer[]::new);

    if (filteredTracers.length == 0) {
      return BlockAwareOperationTracer.NO_TRACING;
    } else if (filteredTracers.length == 1) {
      return filteredTracers[0];
    } else {
      return new BlockAwareTracerAggregator(filteredTracers);
    }
  }

  // BlockAwareOperationTracer methods

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final Address miningBeneficiary) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceStartBlock(worldView, blockHeader, blockBody, miningBeneficiary);
    }
  }

  @Override
  public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceEndBlock(blockHeader, blockBody);
    }
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final ProcessableBlockHeader processableBlockHeader,
      final Address miningBeneficiary) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
    }
  }

  // OperationTracer methods (delegated)

  @Override
  public void tracePreExecution(final MessageFrame frame) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.tracePreExecution(frame);
    }
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.tracePostExecution(frame, operationResult);
    }
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.tracePrecompileCall(frame, gasRequirement, output);
    }
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceAccountCreationResult(frame, haltReason);
    }
  }

  @Override
  public void tracePrepareTransaction(final WorldView worldView, final Transaction transaction) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.tracePrepareTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceStartTransaction(final WorldView worldView, final Transaction transaction) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceStartTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceBeforeRewardTransaction(
      final WorldView worldView, final Transaction tx, final Wei miningReward) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceBeforeRewardTransaction(worldView, tx, miningReward);
    }
  }

  @Override
  public void traceEndTransaction(
      final WorldView worldView,
      final Transaction tx,
      final boolean status,
      final Bytes output,
      final List<Log> logs,
      final long gasUsed,
      final Set<Address> selfDestructs,
      final long timeNs) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceEndTransaction(
          worldView, tx, status, output, logs, gasUsed, selfDestructs, timeNs);
    }
  }

  @Override
  public void traceContextEnter(final MessageFrame frame) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceContextEnter(frame);
    }
  }

  @Override
  public void traceContextReEnter(final MessageFrame frame) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceContextReEnter(frame);
    }
  }

  @Override
  public void traceContextExit(final MessageFrame frame) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      tracer.traceContextExit(frame);
    }
  }

  @Override
  public boolean isExtendedTracing() {
    for (final BlockAwareOperationTracer tracer : tracers) {
      if (tracer.isExtendedTracing()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the list of aggregated tracers.
   *
   * @return an unmodifiable list of the tracers
   */
  public List<BlockAwareOperationTracer> getTracers() {
    return tracers;
  }

  /**
   * Find the first tracer of the specified type.
   *
   * @param <T> the tracer type
   * @param tracerClass the class of the tracer to find
   * @return an Optional containing the tracer if found
   */
  @SuppressWarnings("unchecked")
  public <T extends BlockAwareOperationTracer> Optional<T> findTracer(final Class<T> tracerClass) {
    for (final BlockAwareOperationTracer tracer : tracers) {
      if (tracerClass.isInstance(tracer)) {
        return Optional.of((T) tracer);
      }
      // If this is another BlockAwareTracerAggregator, search recursively
      if (tracer instanceof BlockAwareTracerAggregator) {
        final Optional<T> found = ((BlockAwareTracerAggregator) tracer).findTracer(tracerClass);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Check if this aggregator contains a tracer of the specified type.
   *
   * @param tracerClass the class of the tracer to check for
   * @return true if a tracer of the specified type is present
   */
  public boolean hasTracer(final Class<? extends BlockAwareOperationTracer> tracerClass) {
    return findTracer(tracerClass).isPresent();
  }
}

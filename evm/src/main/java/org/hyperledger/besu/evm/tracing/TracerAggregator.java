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
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;

/**
 * A standard OperationTracer implementation that aggregates multiple tracers and delegates all
 * tracing calls to them in sequence.
 *
 * <p>This provides a consistent way to compose multiple tracers throughout Besu, replacing ad-hoc
 * composite tracer implementations with a standardized solution.
 */
public class TracerAggregator implements OperationTracer {

  private final List<OperationTracer> tracers;

  /**
   * Create a TracerAggregator from a list of tracers.
   *
   * @param tracers the tracers to aggregate
   */
  public TracerAggregator(final List<OperationTracer> tracers) {
    this.tracers = Collections.unmodifiableList(tracers);
  }

  /**
   * Create a TracerAggregator from an array of tracers.
   *
   * @param tracers the tracers to aggregate
   */
  public TracerAggregator(final OperationTracer... tracers) {
    this.tracers = Collections.unmodifiableList(Arrays.asList(tracers));
  }

  /**
   * Create a TracerAggregator that combines an existing tracer with additional tracers. This is
   * useful for adding tracers to an existing tracing setup.
   *
   * @param baseTracer the base tracer to extend
   * @param additionalTracers additional tracers to add
   * @return a new TracerAggregator combining all tracers
   */
  public static TracerAggregator combining(
      final OperationTracer baseTracer, final OperationTracer... additionalTracers) {
    if (baseTracer == OperationTracer.NO_TRACING) {
      return new TracerAggregator(additionalTracers);
    }

    final OperationTracer[] allTracers = new OperationTracer[additionalTracers.length + 1];
    allTracers[0] = baseTracer;
    System.arraycopy(additionalTracers, 0, allTracers, 1, additionalTracers.length);

    return new TracerAggregator(allTracers);
  }

  /**
   * Create a TracerAggregator from multiple tracers, filtering out NO_TRACING instances.
   *
   * @param tracers the tracers to aggregate
   * @return a TracerAggregator, or OperationTracer.NO_TRACING if no actual tracers are provided
   */
  public static OperationTracer of(final OperationTracer... tracers) {
    final OperationTracer[] filteredTracers =
        Arrays.stream(tracers)
            .filter(tracer -> tracer != OperationTracer.NO_TRACING)
            .toArray(OperationTracer[]::new);

    if (filteredTracers.length == 0) {
      return OperationTracer.NO_TRACING;
    } else if (filteredTracers.length == 1) {
      return filteredTracers[0];
    } else {
      return new TracerAggregator(filteredTracers);
    }
  }

  @Override
  public void tracePreExecution(final MessageFrame frame) {
    for (final OperationTracer tracer : tracers) {
      tracer.tracePreExecution(frame);
    }
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    for (final OperationTracer tracer : tracers) {
      tracer.tracePostExecution(frame, operationResult);
    }
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    for (final OperationTracer tracer : tracers) {
      tracer.tracePrecompileCall(frame, gasRequirement, output);
    }
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    for (final OperationTracer tracer : tracers) {
      tracer.traceAccountCreationResult(frame, haltReason);
    }
  }

  @Override
  public void tracePrepareTransaction(final WorldView worldView, final Transaction transaction) {
    for (final OperationTracer tracer : tracers) {
      tracer.tracePrepareTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceStartTransaction(final WorldView worldView, final Transaction transaction) {
    for (final OperationTracer tracer : tracers) {
      tracer.traceStartTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceBeforeRewardTransaction(
      final WorldView worldView, final Transaction tx, final Wei miningReward) {
    for (final OperationTracer tracer : tracers) {
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
    for (final OperationTracer tracer : tracers) {
      tracer.traceEndTransaction(
          worldView, tx, status, output, logs, gasUsed, selfDestructs, timeNs);
    }
  }

  @Override
  public void traceContextEnter(final MessageFrame frame) {
    for (final OperationTracer tracer : tracers) {
      tracer.traceContextEnter(frame);
    }
  }

  @Override
  public void traceContextReEnter(final MessageFrame frame) {
    for (final OperationTracer tracer : tracers) {
      tracer.traceContextReEnter(frame);
    }
  }

  @Override
  public void traceContextExit(final MessageFrame frame) {
    for (final OperationTracer tracer : tracers) {
      tracer.traceContextExit(frame);
    }
  }

  @Override
  public boolean isExtendedTracing() {
    for (final OperationTracer tracer : tracers) {
      if (tracer.isExtendedTracing()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<TraceFrame> getTraceFrames() {
    // Return trace frames from the first tracer that provides them
    for (final OperationTracer tracer : tracers) {
      final List<TraceFrame> frames = tracer.getTraceFrames();
      if (!frames.isEmpty()) {
        return frames;
      }
    }
    return Collections.emptyList();
  }

  /**
   * Get the list of aggregated tracers.
   *
   * @return an unmodifiable list of the tracers
   */
  public List<OperationTracer> getTracers() {
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
  public <T extends OperationTracer> Optional<T> findTracer(final Class<T> tracerClass) {
    for (final OperationTracer tracer : tracers) {
      if (tracerClass.isInstance(tracer)) {
        return Optional.of((T) tracer);
      }
      // If this is another TracerAggregator, search recursively
      if (tracer instanceof TracerAggregator) {
        final Optional<T> found = ((TracerAggregator) tracer).findTracer(tracerClass);
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
  public boolean hasTracer(final Class<? extends OperationTracer> tracerClass) {
    return findTracer(tracerClass).isPresent();
  }

  /**
   * Static utility method to check if an operation tracer contains a tracer of the specified type,
   * either directly or within an aggregated tracer.
   *
   * @param operationTracer the tracer to check
   * @param tracerClass the class of the tracer to check for
   * @return true if a tracer of the specified type is present
   */
  public static boolean hasTracer(
      final OperationTracer operationTracer, final Class<? extends OperationTracer> tracerClass) {
    // Check if the tracer is directly of the specified type
    if (tracerClass.isInstance(operationTracer)) {
      return true;
    }

    // Check if it's a TracerAggregator that might contain the specified tracer type
    if (operationTracer instanceof TracerAggregator) {
      final TracerAggregator aggregator = (TracerAggregator) operationTracer;
      return aggregator.hasTracer(tracerClass);
    }

    return false;
  }
}

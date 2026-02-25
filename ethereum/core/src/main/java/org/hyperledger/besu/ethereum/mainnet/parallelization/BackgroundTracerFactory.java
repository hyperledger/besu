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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.ethereum.mainnet.SlowBlockTracer;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.TracerAggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Factory for creating background tracer instances used during parallel transaction execution.
 * Tracers with mutable state (e.g., EVMExecutionMetricsTracer, SlowBlockTracer) cannot be shared
 * between threads. This factory creates fresh EVMExecutionMetricsTracer instances for background
 * execution so that EVM opcode metrics are captured and can be merged back after conflict-free
 * parallel execution.
 *
 * <p>Also provides utilities for consolidating tracer results from successful parallel execution
 * back into the block's main tracer.
 */
public class BackgroundTracerFactory {

  private BackgroundTracerFactory() {}

  /**
   * Creates a tracer for background (parallel) transaction execution.
   *
   * @param blockProcessingContext the block processing context containing the original tracer
   * @return a background tracer instance, or null if no block tracer exists
   */
  public static OperationTracer createBackgroundTracer(
      final BlockProcessingContext blockProcessingContext) {
    if (blockProcessingContext == null) {
      return null;
    }

    final OperationTracer blockTracer = blockProcessingContext.getOperationTracer();
    if (blockTracer == null) {
      return null;
    }

    // Check if the block tracer contains any metrics tracer (EVMExecutionMetricsTracer directly,
    // or wrapped inside a SlowBlockTracer or TracerAggregator)
    if (hasMetricsTracer(blockTracer)) {
      // Create a new EVMExecutionMetricsTracer instance for background execution
      final EVMExecutionMetricsTracer backgroundMetricsTracer = new EVMExecutionMetricsTracer();

      // If the block tracer is a standalone metrics tracer or SlowBlockTracer, return background
      if (blockTracer instanceof EVMExecutionMetricsTracer
          || blockTracer instanceof SlowBlockTracer) {
        return backgroundMetricsTracer;
      }

      // If the block tracer is a TracerAggregator, create a new aggregator with
      // the background EVMExecutionMetricsTracer replacing metrics-containing tracers
      if (blockTracer instanceof TracerAggregator) {
        return createBackgroundTracerAggregator(
            (TracerAggregator) blockTracer, backgroundMetricsTracer);
      }
    }

    // For other tracer types that don't need separate instances, return the original
    return blockTracer;
  }

  /**
   * Creates a background TracerAggregator by replacing EVMExecutionMetricsTracer and
   * SlowBlockTracer instances with the provided background metrics tracer, while preserving all
   * other tracers. Uses a flag to avoid adding the background tracer twice if both types are
   * present.
   */
  static OperationTracer createBackgroundTracerAggregator(
      final TracerAggregator originalAggregator,
      final EVMExecutionMetricsTracer backgroundMetricsTracer) {

    final List<OperationTracer> originalTracers = originalAggregator.getTracers();
    final List<OperationTracer> backgroundTracers = new ArrayList<>(originalTracers.size());
    boolean metricsTracerAdded = false;
    for (final OperationTracer tracer : originalTracers) {
      if (tracer instanceof EVMExecutionMetricsTracer || tracer instanceof SlowBlockTracer) {
        if (!metricsTracerAdded) {
          backgroundTracers.add(backgroundMetricsTracer);
          metricsTracerAdded = true;
        }
        // Skip duplicate — don't add the background tracer twice
      } else if (tracer instanceof TracerAggregator) {
        backgroundTracers.add(
            createBackgroundTracerAggregator((TracerAggregator) tracer, backgroundMetricsTracer));
      } else {
        backgroundTracers.add(tracer);
      }
    }

    return TracerAggregator.of(backgroundTracers.toArray(new OperationTracer[0]));
  }

  /**
   * Consolidates tracer results from a successful parallel execution into the block's main tracer.
   * Merges background EVMExecutionMetricsTracer counters and increments the SlowBlockTracer
   * tx_count for confirmed parallel transactions.
   *
   * @param backgroundTracer the background tracer used during parallel execution
   * @param blockProcessingContext the block processing context containing the main tracer
   */
  public static void consolidateTracerResults(
      final OperationTracer backgroundTracer, final BlockProcessingContext blockProcessingContext) {
    if (blockProcessingContext == null) {
      return;
    }

    final OperationTracer blockTracer = blockProcessingContext.getOperationTracer();
    if (blockTracer == null) {
      return;
    }

    mergeTracerResults(backgroundTracer, blockTracer);

    // Increment tx_count on the SlowBlockTracer for this confirmed parallel tx
    findSlowBlockTracer(blockTracer)
        .ifPresent(sbt -> sbt.getExecutionStats().incrementTransactionCount());
  }

  /**
   * Merges tracer results from parallel execution into the block's main tracer. Currently focuses
   * on EVMExecutionMetricsTracer consolidation.
   */
  static void mergeTracerResults(
      final OperationTracer backgroundTracer, final OperationTracer blockTracer) {

    final Optional<EVMExecutionMetricsTracer> backgroundMetrics =
        findEVMExecutionMetricsTracer(backgroundTracer);
    final Optional<EVMExecutionMetricsTracer> blockMetrics =
        findEVMExecutionMetricsTracer(blockTracer);

    if (backgroundMetrics.isPresent() && blockMetrics.isPresent()) {
      blockMetrics.get().mergeFrom(backgroundMetrics.get());
    }
  }

  /**
   * Extracts an EVMExecutionMetricsTracer from a tracer, unwrapping SlowBlockTracer and
   * TracerAggregator as needed.
   *
   * @param tracer the tracer to search within
   * @return the EVMExecutionMetricsTracer if found
   */
  public static Optional<EVMExecutionMetricsTracer> findEVMExecutionMetricsTracer(
      final OperationTracer tracer) {
    if (tracer instanceof EVMExecutionMetricsTracer) {
      return Optional.of((EVMExecutionMetricsTracer) tracer);
    } else if (tracer instanceof SlowBlockTracer) {
      final EVMExecutionMetricsTracer inner =
          ((SlowBlockTracer) tracer).getEVMExecutionMetricsTracer();
      return inner != null ? Optional.of(inner) : Optional.empty();
    } else if (tracer instanceof TracerAggregator) {
      // Search for EVMExecutionMetricsTracer directly, and also inside any SlowBlockTracer
      final Optional<EVMExecutionMetricsTracer> direct =
          ((TracerAggregator) tracer).findTracer(EVMExecutionMetricsTracer.class);
      if (direct.isPresent()) {
        return direct;
      }
      final Optional<SlowBlockTracer> sbt =
          ((TracerAggregator) tracer).findTracer(SlowBlockTracer.class);
      if (sbt.isPresent()) {
        final EVMExecutionMetricsTracer inner = sbt.get().getEVMExecutionMetricsTracer();
        return inner != null ? Optional.of(inner) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Checks whether the given tracer contains an EVMExecutionMetricsTracer, either directly, inside
   * a SlowBlockTracer, or inside a TracerAggregator.
   */
  public static boolean hasMetricsTracer(final OperationTracer tracer) {
    if (tracer instanceof EVMExecutionMetricsTracer) {
      return true;
    } else if (tracer instanceof SlowBlockTracer) {
      return true; // SlowBlockTracer always wraps an EVMExecutionMetricsTracer
    } else if (tracer instanceof TracerAggregator) {
      return TracerAggregator.hasTracer(tracer, EVMExecutionMetricsTracer.class)
          || TracerAggregator.hasTracer(tracer, SlowBlockTracer.class);
    }
    return false;
  }

  /** Finds a SlowBlockTracer in the given tracer, checking directly and inside TracerAggregator. */
  public static Optional<SlowBlockTracer> findSlowBlockTracer(final OperationTracer tracer) {
    if (tracer instanceof SlowBlockTracer) {
      return Optional.of((SlowBlockTracer) tracer);
    } else if (tracer instanceof TracerAggregator) {
      return ((TracerAggregator) tracer).findTracer(SlowBlockTracer.class);
    }
    return Optional.empty();
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.SlowBlockTracer;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.TracerAggregator;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BackgroundTracerFactory}. */
class BackgroundTracerFactoryTest {

  @Test
  void createBackgroundTracer_nullContext_returnsNoTracing() {
    assertThat(BackgroundTracerFactory.createBackgroundTracer(null))
        .isSameAs(OperationTracer.NO_TRACING);
  }

  @Test
  void createBackgroundTracer_nullTracer_returnsNoTracing() {
    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(null);
    assertThat(BackgroundTracerFactory.createBackgroundTracer(bpc))
        .isSameAs(OperationTracer.NO_TRACING);
  }

  @Test
  void createBackgroundTracer_withEVMExecutionMetricsTracer_returnsNewInstance() {
    final EVMExecutionMetricsTracer original = new EVMExecutionMetricsTracer();
    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(original);

    final OperationTracer background = BackgroundTracerFactory.createBackgroundTracer(bpc);

    assertThat(background).isInstanceOf(EVMExecutionMetricsTracer.class);
    assertThat(background).isNotSameAs(original);
  }

  @Test
  void createBackgroundTracer_withSlowBlockTracer_returnsEVMExecutionMetricsTracer() {
    final SlowBlockTracer slowBlockTracer = new SlowBlockTracer(0);
    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(slowBlockTracer);

    final OperationTracer background = BackgroundTracerFactory.createBackgroundTracer(bpc);

    assertThat(background)
        .as("SlowBlockTracer should produce EVMExecutionMetricsTracer background, not NO_TRACING")
        .isInstanceOf(EVMExecutionMetricsTracer.class);
  }

  @Test
  void createBackgroundTracer_withTracerAggregator_replacesMetricsTracers() {
    final SlowBlockTracer slowBlockTracer = new SlowBlockTracer(0);
    final EVMExecutionMetricsTracer metricsTracer = new EVMExecutionMetricsTracer();
    final OperationTracer otherTracer = mock(OperationTracer.class);
    final OperationTracer aggregator =
        TracerAggregator.of(slowBlockTracer, metricsTracer, otherTracer);

    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(aggregator);

    final OperationTracer background = BackgroundTracerFactory.createBackgroundTracer(bpc);

    assertThat(TracerAggregator.hasTracer(background, EVMExecutionMetricsTracer.class))
        .as("Background aggregator should contain EVMExecutionMetricsTracer")
        .isTrue();
    assertThat(TracerAggregator.hasTracer(background, SlowBlockTracer.class))
        .as("Background aggregator should NOT contain SlowBlockTracer")
        .isFalse();
  }

  @Test
  void createBackgroundTracer_withUnknownTracer_returnsOriginal() {
    final OperationTracer unknownTracer = mock(OperationTracer.class);
    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(unknownTracer);

    final OperationTracer background = BackgroundTracerFactory.createBackgroundTracer(bpc);

    assertThat(background).isSameAs(unknownTracer);
  }

  @Test
  void hasMetricsTracer_directEVMExecutionMetricsTracer() {
    assertThat(BackgroundTracerFactory.hasMetricsTracer(new EVMExecutionMetricsTracer())).isTrue();
  }

  @Test
  void hasMetricsTracer_slowBlockTracer() {
    assertThat(BackgroundTracerFactory.hasMetricsTracer(new SlowBlockTracer(0))).isTrue();
  }

  @Test
  void hasMetricsTracer_aggregatorContainingSlowBlockTracer() {
    final OperationTracer aggregator =
        TracerAggregator.of(new SlowBlockTracer(0), mock(OperationTracer.class));
    assertThat(BackgroundTracerFactory.hasMetricsTracer(aggregator)).isTrue();
  }

  @Test
  void hasMetricsTracer_noMetricsTracer() {
    assertThat(BackgroundTracerFactory.hasMetricsTracer(mock(OperationTracer.class))).isFalse();
  }

  @Test
  void findEVMExecutionMetricsTracer_directInstance() {
    final EVMExecutionMetricsTracer tracer = new EVMExecutionMetricsTracer();
    assertThat(BackgroundTracerFactory.findEVMExecutionMetricsTracer(tracer)).contains(tracer);
  }

  @Test
  void findEVMExecutionMetricsTracer_insideSlowBlockTracer() {
    final SlowBlockTracer sbt = new SlowBlockTracer(0);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    // Initialize internal state so metricsTracer is non-null
    sbt.traceStartBlock(null, blockHeader, null, Address.ZERO);

    final Optional<EVMExecutionMetricsTracer> found =
        BackgroundTracerFactory.findEVMExecutionMetricsTracer(sbt);
    assertThat(found).isPresent();
    assertThat(found.get()).isSameAs(sbt.getEVMExecutionMetricsTracer());
  }

  @Test
  void findEVMExecutionMetricsTracer_insideSlowBlockTracerInAggregator() {
    final SlowBlockTracer sbt = new SlowBlockTracer(0);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    sbt.traceStartBlock(null, blockHeader, null, Address.ZERO);

    final OperationTracer aggregator = TracerAggregator.of(sbt, mock(OperationTracer.class));

    final Optional<EVMExecutionMetricsTracer> found =
        BackgroundTracerFactory.findEVMExecutionMetricsTracer(aggregator);
    assertThat(found).isPresent();
    assertThat(found.get()).isSameAs(sbt.getEVMExecutionMetricsTracer());
  }

  @Test
  void findSlowBlockTracer_directInstance() {
    final SlowBlockTracer sbt = new SlowBlockTracer(0);
    assertThat(BackgroundTracerFactory.findSlowBlockTracer(sbt)).contains(sbt);
  }

  @Test
  void findSlowBlockTracer_insideAggregator() {
    final SlowBlockTracer sbt = new SlowBlockTracer(0);
    final OperationTracer aggregator = TracerAggregator.of(sbt, mock(OperationTracer.class));
    assertThat(BackgroundTracerFactory.findSlowBlockTracer(aggregator)).contains(sbt);
  }

  @Test
  void findSlowBlockTracer_notPresent() {
    assertThat(BackgroundTracerFactory.findSlowBlockTracer(mock(OperationTracer.class))).isEmpty();
  }

  @Test
  void consolidateTracerResults_mergesMetricsAndIncrementsTxCount() {
    final SlowBlockTracer slowBlockTracer = new SlowBlockTracer(0);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    slowBlockTracer.traceStartBlock(null, blockHeader, null, Address.ZERO);

    final BlockProcessingContext bpc = mock(BlockProcessingContext.class);
    when(bpc.getOperationTracer()).thenReturn(slowBlockTracer);

    // Simulate a background tracer with some metrics
    final EVMExecutionMetricsTracer backgroundTracer = new EVMExecutionMetricsTracer();

    BackgroundTracerFactory.consolidateTracerResults(backgroundTracer, bpc);

    assertThat(slowBlockTracer.getExecutionStats().getTransactionCount())
        .as("tx_count should be incremented after consolidation")
        .isEqualTo(1);
  }

  @Test
  void consolidateTracerResults_nullContext_doesNotThrow() {
    final EVMExecutionMetricsTracer backgroundTracer = new EVMExecutionMetricsTracer();
    // Should not throw
    BackgroundTracerFactory.consolidateTracerResults(backgroundTracer, null);
  }
}

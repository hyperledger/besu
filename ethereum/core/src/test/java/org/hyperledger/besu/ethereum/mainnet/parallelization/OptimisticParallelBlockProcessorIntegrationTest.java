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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ImmutableBalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;

/**
 * Integration tests for optimistic (collision-detection based) parallel block processing. Extends
 * the abstract test class to verify that optimistic parallel execution produces identical state
 * roots to sequential execution and is deterministic across re-executions.
 *
 * <p>All common tests (sequential vs parallel, re-execution consistency) are inherited from
 * AbstractParallelBlockProcessorIntegrationTest.
 */
class OptimisticParallelBlockProcessorIntegrationTest
    extends AbstractParallelBlockProcessorIntegrationTest {

  private static final BalConfiguration OPTIMISTIC_CONFIG =
      ImmutableBalConfiguration.builder().isPerfectParallelizationEnabled(false).build();

  @Override
  protected String getVariantName() {
    return "Optimistic (Collision Detection)";
  }

  @Override
  protected BalConfiguration getBalConfiguration() {
    return OPTIMISTIC_CONFIG;
  }

  @Override
  protected ParallelTransactionPreprocessing createParallelPreprocessing(
      final MainnetTransactionProcessor transactionProcessor) {
    return new ParallelTransactionPreprocessing(
        transactionProcessor, Runnable::run, OPTIMISTIC_CONFIG);
  }
}

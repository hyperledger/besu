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
package org.hyperledger.besu.evmtool.benchmarks;

import java.util.Optional;

/**
 * Stores configuration for running Evmtool benchmarks.
 *
 * @param useNative whether to use native implementation during benchmark.
 * @param usePrecompileCache whether to cache precompiles during benchmarks.
 * @param asyncProfilerOptions whether to use Async Profiler during benchmarks.
 * @param testCasePattern whether to run selected test cases given a regexp patters.
 * @param execIterations run for an unbounded number of iterations.
 * @param execTime run for an unbounded amount of time.
 * @param warmIterations warm up for an unbounded number of iterations.
 * @param warmTime warm up for an unbounded amount of time.
 * @param attemptCacheBust if true, run each test case within each iteration
 */
public record BenchmarkConfig(
    boolean useNative,
    boolean usePrecompileCache,
    Optional<String> asyncProfilerOptions,
    Optional<String> testCasePattern,
    Optional<Integer> execIterations,
    Optional<Integer> execTime,
    Optional<Integer> warmIterations,
    Optional<Integer> warmTime,
    boolean attemptCacheBust) {}

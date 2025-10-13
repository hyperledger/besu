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
package org.hyperledger.besu.evmtool;

import static org.hyperledger.besu.evmtool.BenchmarkSubCommand.COMMAND_NAME;
import static picocli.CommandLine.ScopeType.INHERIT;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.evmtool.benchmarks.AltBN128Benchmark;
import org.hyperledger.besu.evmtool.benchmarks.BLS12Benchmark;
import org.hyperledger.besu.evmtool.benchmarks.BenchmarkExecutor;
import org.hyperledger.besu.evmtool.benchmarks.ECRecoverBenchmark;
import org.hyperledger.besu.evmtool.benchmarks.ModExpBenchmark;
import org.hyperledger.besu.evmtool.benchmarks.Secp256k1Benchmark;
import org.hyperledger.besu.util.LogConfigurator;

import java.io.PrintStream;
import java.util.EnumSet;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * This class represents the BenchmarkSubCommand. It is responsible for executing an Ethereum State
 * Test.
 */
@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Execute an Ethereum State Test.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class BenchmarkSubCommand implements Runnable {
  /**
   * The command name for the BenchmarkSubCommand. This constant is used as the name attribute in
   * the {@code CommandLine.Command} annotation.
   */
  public static final String COMMAND_NAME = "benchmark";

  private final PrintStream output;

  enum Benchmark {
    altBn128(new AltBN128Benchmark()),
    // blake2f
    EcRecover(new ECRecoverBenchmark()),
    ModExp(new ModExpBenchmark()),
    Secp256k1(new Secp256k1Benchmark()),
    // bls12
    Bls12(new BLS12Benchmark());

    final BenchmarkExecutor benchmarkExecutor;

    Benchmark(final BenchmarkExecutor benchmarkExecutor) {
      this.benchmarkExecutor = benchmarkExecutor;
    }
  }

  @Option(
      names = {"--native"},
      description = "Use the native libraries.",
      scope = INHERIT,
      negatable = true)
  Boolean nativeCode;

  @Parameters(description = "One or more of ${COMPLETION-CANDIDATES}.")
  EnumSet<Benchmark> benchmarks = EnumSet.noneOf(Benchmark.class);

  @ParentCommand EvmToolCommand parentCommand;

  /** Default constructor for the BenchmarkSubCommand class. This is required by PicoCLI. */
  public BenchmarkSubCommand() {
    // PicoCLI requires this
    this(System.out);
  }

  /**
   * Constructs a new BenchmarkSubCommand with the given output stream.
   *
   * @param output the output stream to be used
   */
  public BenchmarkSubCommand(final PrintStream output) {
    this.output = output;
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "DEBUG");
    System.out.println(BesuInfo.version());
    var benchmarksToRun = benchmarks.isEmpty() ? EnumSet.allOf(Benchmark.class) : benchmarks;
    for (var benchmark : benchmarksToRun) {
      System.out.println("Benchmarks for " + benchmark);
      benchmark.benchmarkExecutor.runBenchmark(output, nativeCode, parentCommand.getFork());
    }
  }
}

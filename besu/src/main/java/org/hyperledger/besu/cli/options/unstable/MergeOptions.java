/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.cli.options.unstable;

import java.util.Stack;

import net.consensys.quorum.mainnet.launcher.options.Options;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** Unstable support for eth1/2 merge */
public class MergeOptions implements Options {
  // To make it easier for tests to reset the value to default
  public static final boolean MERGE_ENABLED_DEFAULT_VALUE = false;

  @Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Enable experimental support for eth1/eth2 merge (default: ${DEFAULT-VALUE})",
      arity = "1",
      parameterConsumer = MergeConfigConsumer.class)
  @SuppressWarnings({"FieldCanBeFinal"})
  private static boolean mergeEnabled = MERGE_ENABLED_DEFAULT_VALUE;

  public static MergeOptions create() {
    return new MergeOptions();
  }

  public Boolean isMergeEnabled() {
    return mergeEnabled;
  }

  @SuppressWarnings({"JdkObsolete"})
  static class MergeConfigConsumer implements CommandLine.IParameterConsumer {
    @Override
    public void consumeParameters(
        final Stack<String> args,
        final CommandLine.Model.ArgSpec argSpec,
        final CommandLine.Model.CommandSpec commandSpec) {
      org.hyperledger.besu.config.experimental.MergeOptions.setMergeEnabled(
          Boolean.parseBoolean(args.pop()));
    }
  }
}

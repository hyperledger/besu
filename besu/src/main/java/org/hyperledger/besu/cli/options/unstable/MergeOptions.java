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

import static org.hyperledger.besu.config.MergeConfigOptions.setMergeEnabled;

import java.util.Stack;

import net.consensys.quorum.mainnet.launcher.options.Options;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** DEPRECATED in favor of genesis config. Unstable config for eth1/2 merge */
public class MergeOptions implements Options {
  // To make it easier for tests to reset the value to default
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MergeOptions.class);

  @Option(
      hidden = true,
      names = {"--Xmerge-support"},
      description = "Deprecated config parameter, do not use",
      arity = "1",
      parameterConsumer = MergeConfigConsumer.class)
  @SuppressWarnings({"FieldCanBeFinal"})
  private static boolean mergeEnabled = false;

  @SuppressWarnings({"JdkObsolete"})
  static class MergeConfigConsumer implements CommandLine.IParameterConsumer {
    @Override
    public void consumeParameters(
        final Stack<String> args,
        final CommandLine.Model.ArgSpec argSpec,
        final CommandLine.Model.CommandSpec commandSpec) {
      setMergeEnabled(Boolean.parseBoolean(args.pop()));
      LOG.warn(
          "--Xmerge-support={} parameter has been deprecated and will be removed in a future release.  "
              + "Merge support is implicitly enabled by the presence of terminalTotalDifficulty in the genesis config",
          mergeEnabled);
    }
  }
}

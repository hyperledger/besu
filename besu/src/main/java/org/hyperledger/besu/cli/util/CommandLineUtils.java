/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.util;

import org.hyperledger.besu.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

public class CommandLineUtils {
  /**
   * Check if options are passed that require an option to be true to have any effect and log a
   * warning with the list of affected options.
   *
   * <p>Note that in future version of PicoCLI some options dependency mechanism may be implemented
   * that could replace this. See https://github.com/remkop/picocli/issues/295
   *
   * @param logger the logger instance used to log the warning
   * @param commandLine the command line containing the options we want to check
   * @param mainOptionName the name of the main option to test dependency against. Only used for
   *     display.
   * @param isMainOptionCondition the condition to test the options dependencies, if true will test
   *     if not won't
   * @param dependentOptionsNames a list of option names that can't be used if condition is met.
   *     Example: if --miner-coinbase is in the list and condition is that --miner-enabled should
   *     not be false, we log a warning.
   */
  public static void checkOptionDependencies(
      final Logger logger,
      final CommandLine commandLine,
      final String mainOptionName,
      final boolean isMainOptionCondition,
      final List<String> dependentOptionsNames) {
    if (isMainOptionCondition) {
      String affectedOptions =
          commandLine.getCommandSpec().options().stream()
              .filter(
                  option ->
                      Arrays.stream(option.names()).anyMatch(dependentOptionsNames::contains)
                          && !option.stringValues().isEmpty())
              .map(option -> option.names()[0])
              .collect(
                  Collectors.collectingAndThen(
                      Collectors.toList(), StringUtils.joiningWithLastDelimiter(", ", " and ")));

      if (!affectedOptions.isEmpty()) {
        logger.warn(
            "{} will have no effect unless {} is defined on the command line.",
            affectedOptions,
            mainOptionName);
      }
    }
  }
}

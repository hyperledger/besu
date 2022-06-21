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
package org.hyperledger.besu.cli.util;

import org.hyperledger.besu.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import picocli.CommandLine;

public class CommandLineUtils {
  public static final String DEPENDENCY_WARNING_MSG =
      "{} has been ignored because {} was not defined on the command line.";
  public static final String MULTI_DEPENDENCY_WARNING_MSG =
      "{} ignored because none of {} was defined.";
  public static final String DEPRECATION_WARNING_MSG = "{} has been deprecated, use {} instead.";
  public static final String DEPRECATED_AND_USELESS_WARNING_MSG =
      "{} has been deprecated and is now useless, remove it.";

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
      final String affectedOptions = getAffectedOptions(commandLine, dependentOptionsNames);

      if (!affectedOptions.isEmpty()) {
        logger.warn(DEPENDENCY_WARNING_MSG, affectedOptions, mainOptionName);
      }
    }
  }

  /**
   * Check if options are passed that require an option to be true to have any effect and log a
   * warning with the list of affected options. Multiple main options may be passed to check
   * dependencies against.
   *
   * <p>Note that in future version of PicoCLI some options dependency mechanism may be implemented
   * that could replace this. See https://github.com/remkop/picocli/issues/295
   *
   * @param logger the logger instance used to log the warning
   * @param commandLine the command line containing the options we want to check display.
   * @param stringToLog the string that is going to be logged.
   * @param isMainOptionCondition the conditions to test dependent options against. If all
   *     conditions are true, dependent options will be checked.
   * @param dependentOptionsNames a list of option names that can't be used if condition is met.
   *     Example: if --min-gas-price is in the list and condition is that --miner-enabled should not
   *     be false, we log a warning.
   */
  public static void checkMultiOptionDependencies(
      final Logger logger,
      final CommandLine commandLine,
      final String stringToLog,
      final List<Boolean> isMainOptionCondition,
      final List<String> dependentOptionsNames) {
    if (isMainOptionCondition.stream().allMatch(isTrue -> isTrue)) {
      final String affectedOptions = getAffectedOptions(commandLine, dependentOptionsNames);

      if (!affectedOptions.isEmpty()) {
        logger.warn(stringToLog);
      }
    }
  }

  private static String getAffectedOptions(
      final CommandLine commandLine, final List<String> dependentOptionsNames) {
    return commandLine.getCommandSpec().options().stream()
        .filter(option -> Arrays.stream(option.names()).anyMatch(dependentOptionsNames::contains))
        .filter(CommandLineUtils::isOptionSet)
        .map(option -> option.names()[0])
        .collect(
            Collectors.collectingAndThen(
                Collectors.toList(), StringUtils.joiningWithLastDelimiter(", ", " and ")));
  }

  private static boolean isOptionSet(final CommandLine.Model.OptionSpec option) {
    final CommandLine commandLine = option.command().commandLine();
    try {
      return !option.stringValues().isEmpty()
          || !Strings.isNullOrEmpty(commandLine.getDefaultValueProvider().defaultValue(option));
    } catch (final Exception e) {
      return false;
    }
  }
}

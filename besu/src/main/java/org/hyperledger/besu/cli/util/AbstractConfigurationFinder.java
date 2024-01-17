/*
 * Copyright Hyperledger Besu Contributors.
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

import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

/**
 * Abstract class for finding configuration resources. This class provides a common structure for
 * classes that need to find configuration resources based on command line options and environment
 * variables.
 *
 * @param <T> the type of configuration resource this finder will return
 */
public abstract class AbstractConfigurationFinder<T> {

  /**
   * Finds the configuration resource based on command line options and environment variables.
   *
   * @param environment the environment variables
   * @param parseResult the command line parse result
   * @return an Optional containing the configuration resource, or an empty Optional if no
   *     configuration resource was found
   */
  protected abstract Optional<T> findConfiguration(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult);
  /**
   * Checks if the configuration resource is specified in both command line options and environment
   * variables.
   *
   * @param environment the environment variables
   * @param parseResult the command line parse result
   * @return true if the configuration resource is specified in both places, false otherwise
   */
  protected abstract boolean isConfigSpecifiedInBothSources(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult);

  /**
   * Throws an exception if the configuration resource is specified in both command line options and
   * environment variables.
   *
   * @param environment the environment variables
   * @param parseResult the command line parse result
   * @param commandLine the command line
   */
  protected abstract void throwExceptionForBothSourcesSpecified(
      final Map<String, String> environment,
      final CommandLine.ParseResult parseResult,
      final CommandLine commandLine);

  /**
   * Gets the configuration resource from the command line option.
   *
   * @param parseResult the command line parse result
   * @param commandLine the command line
   * @return an Optional containing the configuration resource, or an empty Optional if the
   *     configuration resource was not specified in the command line option
   */
  protected abstract Optional<T> getConfigFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine);

  /**
   * Gets the configuration resource from the environment variable.
   *
   * @param environment the environment variables
   * @param commandLine the command line
   * @return an Optional containing the configuration resource, or an empty Optional if the
   *     configuration resource was not specified in the environment variable
   */
  protected abstract Optional<T> getConfigFromEnvironment(
      final Map<String, String> environment, final CommandLine commandLine);
}

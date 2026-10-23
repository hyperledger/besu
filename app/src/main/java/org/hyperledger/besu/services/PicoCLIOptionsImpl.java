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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.PicoCLIOptions;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/** The Pico cli options service implementation to specify plugins. */
public class PicoCLIOptionsImpl implements PicoCLIOptions {

  private static final Logger LOG = LoggerFactory.getLogger(PicoCLIOptionsImpl.class);

  private final CommandLine commandLine;
  private final Set<String> additionalAllowedPrefixes;

  /**
   * Instantiates a new Pico cli options.
   *
   * @param commandLine the command line
   */
  public PicoCLIOptionsImpl(final CommandLine commandLine) {
    this(commandLine, Set.of());
  }

  /**
   * Instantiates a new Pico cli options that, besides the {@code --plugin-<namespace>-} and {@code
   * --Xplugin-<namespace>-} prefixes, also accepts options starting with any of the given prefixes.
   * This is only meant for built-in plugins that must keep the option names they had when they were
   * part of Besu core (for example {@code --graphql-}). It must never be handed to external
   * plugins.
   *
   * @param commandLine the command line
   * @param additionalAllowedPrefixes additional option name prefixes to accept, for example {@code
   *     "--graphql-"}
   */
  public PicoCLIOptionsImpl(
      final CommandLine commandLine, final Set<String> additionalAllowedPrefixes) {
    this.commandLine = commandLine;
    this.additionalAllowedPrefixes = Set.copyOf(additionalAllowedPrefixes);
  }

  @Override
  public void addPicoCLIOptions(final String namespace, final Object optionObject) {
    final String pluginPrefix = "--plugin-" + namespace + "-";
    final String unstablePrefix = "--Xplugin-" + namespace + "-";
    final CommandSpec mixin = CommandSpec.forAnnotatedObject(optionObject);
    boolean badOptionName = false;

    for (final OptionSpec optionSpec : mixin.options()) {
      for (final String optionName : optionSpec.names()) {
        if (!optionName.startsWith(pluginPrefix)
            && !optionName.startsWith(unstablePrefix)
            && additionalAllowedPrefixes.stream().noneMatch(optionName::startsWith)) {
          badOptionName = true;
          LOG.error(
              "Plugin option {} did not have the expected prefix of {}", optionName, pluginPrefix);
        }
      }
    }
    if (badOptionName) {
      throw new RuntimeException("Error loading CLI options");
    } else {
      commandLine.getCommandSpec().addMixin("Plugin " + namespace, mixin);
    }
  }
}

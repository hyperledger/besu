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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

public class PicoCLIOptionsImpl implements PicoCLIOptions {

  private static final Logger LOG = LoggerFactory.getLogger(PicoCLIOptionsImpl.class);

  private final CommandLine commandLine;

  public PicoCLIOptionsImpl(final CommandLine commandLine) {
    this.commandLine = commandLine;
  }

  @Override
  public void addPicoCLIOptions(final String namespace, final Object optionObject) {
    final String pluginPrefix = "--plugin-" + namespace + "-";
    final String unstablePrefix = "--Xplugin-" + namespace + "-";
    final CommandSpec mixin = CommandSpec.forAnnotatedObject(optionObject);
    boolean badOptionName = false;

    for (final OptionSpec optionSpec : mixin.options()) {
      for (final String optionName : optionSpec.names()) {
        if (!optionName.startsWith(pluginPrefix) && !optionName.startsWith(unstablePrefix)) {
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

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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class PluginsOptionsTest extends CommandTestAbstract {

  @Captor protected ArgumentCaptor<PluginConfiguration> pluginConfigurationArgumentCaptor;

  @Test
  public void shouldParsePluginOptionForSinglePlugin() {
    parseCommand("--plugins", "pluginA");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().getRequestedPlugins())
        .isEqualTo(List.of("pluginA"));
  }

  @Test
  public void shouldParsePluginOptionForMultiplePlugins() {
    parseCommand("--plugins", "pluginA,pluginB");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().getRequestedPlugins())
        .isEqualTo(List.of("pluginA", "pluginB"));
  }

  @Test
  public void shouldNotUsePluginOptionWhenNoPluginsSpecified() {
    parseCommand();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().getRequestedPlugins())
        .isEqualTo(List.of());
  }

  @Test
  public void shouldNotParseAnyPluginsWhenPluginOptionIsEmpty() {
    parseCommand("--plugins", "");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().getRequestedPlugins())
        .isEqualTo(List.of());
  }

  @Test
  public void shouldParsePluginsExternalEnabledOptionWhenFalse() {
    parseCommand("--Xplugins-external-enabled=false");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());

    assertThat(pluginConfigurationArgumentCaptor.getValue().isExternalPluginsEnabled())
        .isEqualTo(false);
  }

  @Test
  public void shouldParsePluginsExternalEnabledOptionWhenTrue() {
    parseCommand("--Xplugins-external-enabled=true");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());

    assertThat(pluginConfigurationArgumentCaptor.getValue().isExternalPluginsEnabled())
        .isEqualTo(true);
  }

  @Test
  public void shouldEnablePluginsExternalByDefault() {
    parseCommand();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().isExternalPluginsEnabled())
        .isEqualTo(true);
  }

  @Test
  public void shouldFailWhenPluginsIsDisabledAndPluginsExplicitlyRequested() {
    parseCommand("--Xplugins-external-enabled=false", "--plugins", "pluginA");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--plugins and --plugin-continue-on-error option can only be used when --Xplugins-external-enabled is true");
  }

  @Test
  public void shouldHaveContinueOnErrorFalseByDefault() {
    parseCommand();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());
    assertThat(pluginConfigurationArgumentCaptor.getValue().isContinueOnPluginError())
        .isEqualTo(false);
  }

  @Test
  public void shouldUseContinueOnErrorWhenTrue() {
    parseCommand("--plugin-continue-on-error=true");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();

    verify(getBesuPluginContext()).initialize(pluginConfigurationArgumentCaptor.capture());

    assertThat(pluginConfigurationArgumentCaptor.getValue().isContinueOnPluginError())
        .isEqualTo(true);
  }

  @Test
  public void shouldFailWhenPluginsIsDisabledAnHaltOnErrorTrue() {
    parseCommand("--Xplugins-external-enabled=false", "--plugin-continue-on-error=true");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains(
            "--plugins and --plugin-continue-on-error option can only be used when --Xplugins-external-enabled is true");
  }
}

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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.tests.acceptance.plugins.TestBesuEventsPlugin;
import org.hyperledger.besu.tests.acceptance.plugins.TestPicoCLIPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BesuPluginContextImplTest {
  private static final Path DEFAULT_PLUGIN_DIRECTORY = Paths.get(".");
  private static final String TEST_PICO_CLI_PLUGIN = "TestPicoCLIPlugin";
  private static final String TEST_PICO_CLI_PLUGIN_TEST_OPTION = "testPicoCLIPlugin.testOption";
  private static final String FAIL_REGISTER = "FAILREGISTER";
  private static final String FAIL_START = "FAILSTART";
  private static final String FAIL_STOP = "FAILSTOP";
  private static final String FAIL_BEFORE_EXTERNAL_SERVICES = "FAILBEFOREEXTERNALSERVICES";
  private static final String FAIL_BEFORE_MAIN_LOOP = "FAILBEFOREMAINLOOP";
  private static final String FAIL_AFTER_EXTERNAL_SERVICE_POST_MAIN_LOOP =
      "FAILAFTEREXTERNALSERVICEPOSTMAINLOOP";
  private static final String NON_EXISTENT_PLUGIN = "NonExistentPlugin";
  private static final String REGISTERED = "registered";
  private static final String STARTED = "started";
  private static final String STOPPED = "stopped";
  private static final String FAIL_START_STATE = "failstart";
  private static final String FAIL_STOP_STATE = "failstop";

  private BesuPluginContextImpl contextImpl;

  private static final PluginConfiguration DEFAULT_CONFIGURATION =
      PluginConfiguration.builder()
          .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
          .externalPluginsEnabled(true)
          .continueOnPluginError(true)
          .build();

  @BeforeAll
  public static void createFakePluginDir() throws IOException {
    if (System.getProperty("besu.plugins.dir") == null) {
      final Path pluginDir = Files.createTempDirectory("besuTest");
      pluginDir.toFile().deleteOnExit();
      System.setProperty("besu.plugins.dir", pluginDir.toAbsolutePath().toString());
    }
  }

  @AfterEach
  public void clearTestPluginState() {
    System.clearProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION);
  }

  @BeforeEach
  void setup() {
    contextImpl = new BesuPluginContextImpl();
  }

  @Test
  public void verifyEverythingGoesSmoothly() {
    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotEmpty();

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(REGISTERED);

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(STARTED);

    contextImpl.stopPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(STOPPED);
  }

  @Test
  public void registrationErrorsHandledSmoothly() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_REGISTER);

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);

    contextImpl.beforeExternalServices();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);

    contextImpl.startPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);

    contextImpl.stopPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  public void startErrorsHandledSmoothly() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_START);

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins())
        .extracting("class")
        .contains(TestPicoCLIPlugin.class);

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(REGISTERED);

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(FAIL_START_STATE);
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);

    contextImpl.stopPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  public void stopErrorsHandledSmoothly() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_STOP);

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins())
        .extracting("class")
        .contains(TestPicoCLIPlugin.class);

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(REGISTERED);

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(STARTED);

    contextImpl.stopPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(FAIL_STOP_STATE);
  }

  @Test
  public void lifecycleExceptions() throws Throwable {
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    final ThrowableAssert.ThrowingCallable registerPlugins = () -> contextImpl.registerPlugins();

    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);

    registerPlugins.call();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);

    contextImpl.stopPlugins();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);
  }

  @Test
  public void shouldRegisterAllPluginsWhenNoPluginsOption() {
    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo(REGISTERED);
  }

  @Test
  public void shouldRegisterOnlySpecifiedPluginWhenPluginsOptionIsSet() {
    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(createConfigurationForSpecificPlugin(TEST_PICO_CLI_PLUGIN));
    contextImpl.registerPlugins();

    final Optional<TestPicoCLIPlugin> requestedPlugin =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);

    assertThat(requestedPlugin).isPresent();
    assertThat(requestedPlugin.get().getState()).isEqualTo(REGISTERED);

    final Optional<TestPicoCLIPlugin> nonRequestedPlugin =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestBesuEventsPlugin.class);

    assertThat(nonRequestedPlugin).isEmpty();
  }

  @Test
  public void shouldNotRegisterUnspecifiedPluginsWhenPluginsOptionIsSet() {
    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.initialize(createConfigurationForSpecificPlugin(TEST_PICO_CLI_PLUGIN));
    contextImpl.registerPlugins();

    final Optional<TestPicoCLIPlugin> nonRequestedPlugin =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestBesuEventsPlugin.class);
    assertThat(nonRequestedPlugin).isEmpty();
  }

  @Test
  void shouldThrowExceptionIfExplicitlySpecifiedPluginNotFound() {
    contextImpl.initialize(createConfigurationForSpecificPlugin(NON_EXISTENT_PLUGIN));
    String exceptionMessage =
        assertThrows(NoSuchElementException.class, () -> contextImpl.registerPlugins())
            .getMessage();
    final String expectedMessage =
        "The following requested plugins were not found: " + NON_EXISTENT_PLUGIN;
    assertThat(exceptionMessage).isEqualTo(expectedMessage);
    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
  }

  @Test
  void shouldNotRegisterAnyPluginsIfExternalPluginsDisabled() {
    PluginConfiguration config =
        PluginConfiguration.builder()
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .externalPluginsEnabled(false)
            .build();
    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins().isEmpty()).isTrue();
  }

  @Test
  void shouldRegisterPluginsIfExternalPluginsEnabled() {
    contextImpl.initialize(DEFAULT_CONFIGURATION);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins().isEmpty()).isFalse();
  }

  @Test
  void shouldHaltOnRegisterErrorWhenFlagIsFalse() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_REGISTER);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(false)
            .build();

    contextImpl.initialize(config);

    String errorMessage =
        String.format("Error registering plugin of type %s", TestPicoCLIPlugin.class.getName());
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> contextImpl.registerPlugins())
        .withMessageContaining(errorMessage);
  }

  @Test
  void shouldNotHaltOnRegisterErrorWhenFlagIsTrue() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_REGISTER);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(true)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  void shouldHaltOnBeforeExternalServicesErrorWhenFlagIsFalse() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_BEFORE_EXTERNAL_SERVICES);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(false)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();

    String errorMessage =
        String.format(
            "Error calling `beforeExternalServices` on plugin of type %s",
            TestPicoCLIPlugin.class.getName());
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> contextImpl.beforeExternalServices())
        .withMessageContaining(errorMessage);
  }

  @Test
  void shouldNotHaltOnBeforeExternalServicesErrorWhenFlagIsTrue() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_BEFORE_EXTERNAL_SERVICES);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(true)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    contextImpl.beforeExternalServices();

    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  void shouldHaltOnBeforeMainLoopErrorWhenFlagIsFalse() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_START);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(false)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    contextImpl.beforeExternalServices();

    String errorMessage =
        String.format("Error starting plugin of type %s", TestPicoCLIPlugin.class.getName());
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> contextImpl.startPlugins())
        .withMessageContaining(errorMessage);
  }

  @Test
  void shouldNotHaltOnBeforeMainLoopErrorWhenFlagIsTrue() {
    System.setProperty(TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_BEFORE_MAIN_LOOP);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(true)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();

    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  void shouldHaltOnAfterExternalServicePostMainLoopErrorWhenFlagIsFalse() {
    System.setProperty(
        TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_AFTER_EXTERNAL_SERVICE_POST_MAIN_LOOP);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(false)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();

    String errorMessage =
        String.format(
            "Error calling `afterExternalServicePostMainLoop` on plugin of type %s",
            TestPicoCLIPlugin.class.getName());
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> contextImpl.afterExternalServicesMainLoop())
        .withMessageContaining(errorMessage);
  }

  @Test
  void shouldNotHaltOnAfterExternalServicePostMainLoopErrorWhenFlagIsTrue() {
    System.setProperty(
        TEST_PICO_CLI_PLUGIN_TEST_OPTION, FAIL_AFTER_EXTERNAL_SERVICE_POST_MAIN_LOOP);

    PluginConfiguration config =
        PluginConfiguration.builder()
            .requestedPlugins(List.of(new PluginInfo(TEST_PICO_CLI_PLUGIN)))
            .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
            .continueOnPluginError(true)
            .build();

    contextImpl.initialize(config);
    contextImpl.registerPlugins();
    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    contextImpl.afterExternalServicesMainLoop();

    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  private PluginConfiguration createConfigurationForSpecificPlugin(final String pluginName) {
    return PluginConfiguration.builder()
        .requestedPlugins(List.of(new PluginInfo(pluginName)))
        .pluginsDir(DEFAULT_PLUGIN_DIRECTORY)
        .build();
  }

  private Optional<TestPicoCLIPlugin> findTestPlugin(
      final List<BesuPlugin> plugins, final Class<?> type) {
    return plugins.stream()
        .filter(p -> type.equals(p.getClass()))
        .map(p -> (TestPicoCLIPlugin) p)
        .findFirst();
  }
}

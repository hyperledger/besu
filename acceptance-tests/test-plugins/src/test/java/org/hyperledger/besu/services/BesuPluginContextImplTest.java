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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.tests.acceptance.plugins.TestBesuEventsPlugin;
import org.hyperledger.besu.tests.acceptance.plugins.TestPicoCLIPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BesuPluginContextImplTest {

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
    System.clearProperty("testPicoCLIPlugin.testOption");
  }

  @Test
  public void verifyEverythingGoesSmoothly() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getRegisteredPlugins()).isNotEmpty();

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    Assertions.assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("registered");

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("started");

    contextImpl.stopPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("stopped");
  }

  @Test
  public void registrationErrorsHandledSmoothly() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    System.setProperty("testPicoCLIPlugin.testOption", "FAILREGISTER");

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
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
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    System.setProperty("testPicoCLIPlugin.testOption", "FAILSTART");

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getRegisteredPlugins())
        .extracting("class")
        .contains(TestPicoCLIPlugin.class);

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("registered");

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("failstart");
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);

    contextImpl.stopPlugins();
    assertThat(contextImpl.getRegisteredPlugins()).isNotInstanceOfAny(TestPicoCLIPlugin.class);
  }

  @Test
  public void stopErrorsHandledSmoothly() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    System.setProperty("testPicoCLIPlugin.testOption", "FAILSTOP");

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getRegisteredPlugins())
        .extracting("class")
        .contains(TestPicoCLIPlugin.class);

    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("registered");

    contextImpl.beforeExternalServices();
    contextImpl.startPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("started");

    contextImpl.stopPlugins();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("failstop");
  }

  @Test
  public void lifecycleExceptions() throws Throwable {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    final ThrowableAssert.ThrowingCallable registerPlugins =
        () -> contextImpl.registerPlugins(new File(".").toPath());

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
  public void shouldRegisterAllPluginsWhenDetectionTypeIsAll() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    final PluginConfiguration config =
        new PluginConfiguration(
            List.of(), // Explicitly include our test plugin
            PluginConfiguration.DetectionType.ALL,
            Paths.get("."));

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(config);
    final Optional<TestPicoCLIPlugin> testPluginOptional =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestPicoCLIPlugin.class);
    Assertions.assertThat(testPluginOptional).isPresent();
    final TestPicoCLIPlugin testPicoCLIPlugin = testPluginOptional.get();
    assertThat(testPicoCLIPlugin.getState()).isEqualTo("registered");
  }

  @Test
  public void shouldRegisterOnlySpecifiedPluginWhenDetectionTypeIsExplicit() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    final PluginConfiguration config =
        new PluginConfiguration(
            List.of(new PluginInfo("TestPicoCLIPlugin")), // Explicitly include our test plugin
            PluginConfiguration.DetectionType.EXPLICIT,
            Paths.get("."));

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(config);

    final Optional<TestPicoCLIPlugin> nonRequestedPlugin =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestBesuEventsPlugin.class);

    Assertions.assertThat(nonRequestedPlugin).isEmpty();
  }

  @Test
  public void shouldNotRegisterUnspecifiedPluginsWhenDetectionTypeIsExplicit() {
    final BesuPluginContextImpl contextImpl = new BesuPluginContextImpl();
    final PluginConfiguration config =
        new PluginConfiguration(
            List.of(new PluginInfo("TestPicoCLIPlugin")), // Explicitly include our test plugin
            PluginConfiguration.DetectionType.EXPLICIT,
            Paths.get("."));

    assertThat(contextImpl.getRegisteredPlugins()).isEmpty();
    contextImpl.registerPlugins(config);

    final Optional<TestPicoCLIPlugin> nonRequestedPlugin =
        findTestPlugin(contextImpl.getRegisteredPlugins(), TestBesuEventsPlugin.class);
    Assertions.assertThat(nonRequestedPlugin).isEmpty();
  }

  private Optional<TestPicoCLIPlugin> findTestPlugin(
      final List<BesuPlugin> plugins, final Class<?> type) {
    return plugins.stream()
        .filter(p -> type.equals(p.getClass()))
        .map(p -> (TestPicoCLIPlugin) p)
        .findFirst();
  }
}

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
package tech.pegasys.pantheon.plugins.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.pantheon.plugins.PantheonPlugin;
import tech.pegasys.pantheon.plugins.TestPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class PantheonPluginContextImplTest {

  @BeforeClass
  public static void createFakePluginDir() throws IOException {
    if (System.getProperty("pantheon.plugins.dir") == null) {
      final Path pluginDir = Files.createTempDirectory("pantheonTest");
      pluginDir.toFile().deleteOnExit();
      System.setProperty("pantheon.plugins.dir", pluginDir.toAbsolutePath().toString());
    }
  }

  @After
  public void clearTestPluginState() {
    System.clearProperty("testPlugin.testOption");
  }

  @Test
  public void verifyEverythingGoesSmoothly() {
    final PantheonPluginContextImpl contextImpl = new PantheonPluginContextImpl();

    assertThat(contextImpl.getPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getPlugins()).isNotEmpty();

    final Optional<TestPlugin> testPluginOptional = findTestPlugin(contextImpl.getPlugins());
    assertThat(testPluginOptional).isPresent();
    final TestPlugin testPlugin = testPluginOptional.get();
    assertThat(testPlugin.getState()).isEqualTo("registered");

    contextImpl.startPlugins();
    assertThat(testPlugin.getState()).isEqualTo("started");

    contextImpl.stopPlugins();
    assertThat(testPlugin.getState()).isEqualTo("stopped");
  }

  @Test
  public void registrationErrorsHandledSmoothly() {
    final PantheonPluginContextImpl contextImpl = new PantheonPluginContextImpl();
    System.setProperty("testPlugin.testOption", "FAILREGISTER");

    assertThat(contextImpl.getPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getPlugins()).isEmpty();

    contextImpl.startPlugins();
    assertThat(contextImpl.getPlugins()).isEmpty();

    contextImpl.stopPlugins();
    assertThat(contextImpl.getPlugins()).isEmpty();
  }

  @Test
  public void startErrorsHandledSmoothly() {
    final PantheonPluginContextImpl contextImpl = new PantheonPluginContextImpl();
    System.setProperty("testPlugin.testOption", "FAILSTART");

    assertThat(contextImpl.getPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getPlugins()).isNotEmpty();

    final Optional<TestPlugin> testPluginOptional = findTestPlugin(contextImpl.getPlugins());
    assertThat(testPluginOptional).isPresent();
    final TestPlugin testPlugin = testPluginOptional.get();
    assertThat(testPlugin.getState()).isEqualTo("registered");

    contextImpl.startPlugins();
    assertThat(testPlugin.getState()).isEqualTo("failstart");
    assertThat(contextImpl.getPlugins()).isEmpty();

    contextImpl.stopPlugins();
    assertThat(contextImpl.getPlugins()).isEmpty();
  }

  @Test
  public void stopErrorsHandledSmoothly() {
    final PantheonPluginContextImpl contextImpl = new PantheonPluginContextImpl();
    System.setProperty("testPlugin.testOption", "FAILSTOP");

    assertThat(contextImpl.getPlugins()).isEmpty();
    contextImpl.registerPlugins(new File(".").toPath());
    assertThat(contextImpl.getPlugins()).isNotEmpty();

    final Optional<TestPlugin> testPluginOptional = findTestPlugin(contextImpl.getPlugins());
    assertThat(testPluginOptional).isPresent();
    final TestPlugin testPlugin = testPluginOptional.get();
    assertThat(testPlugin.getState()).isEqualTo("registered");

    contextImpl.startPlugins();
    assertThat(testPlugin.getState()).isEqualTo("started");

    contextImpl.stopPlugins();
    assertThat(testPlugin.getState()).isEqualTo("failstop");
  }

  @Test
  public void lifecycleExceptions() throws Throwable {
    final PantheonPluginContextImpl contextImpl = new PantheonPluginContextImpl();
    final ThrowableAssert.ThrowingCallable registerPlugins =
        () -> contextImpl.registerPlugins(new File(".").toPath());

    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);

    registerPlugins.call();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);

    contextImpl.startPlugins();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);

    contextImpl.stopPlugins();
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(registerPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::startPlugins);
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(contextImpl::stopPlugins);
  }

  private Optional<TestPlugin> findTestPlugin(final List<PantheonPlugin> plugins) {
    return plugins.stream()
        .filter(p -> p instanceof TestPlugin)
        .map(p -> (TestPlugin) p)
        .findFirst();
  }
}

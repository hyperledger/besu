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
 *
 */
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class BadCLIOptionsPluginTest extends AcceptanceTestBase {
  private BesuNode node;

  @Before
  public void setUp() throws Exception {
    System.setProperty("TEST_BAD_CLI", "true");

    node =
        besu.createPluginsNode(
            "node1", Collections.singletonList("testPlugins"), Collections.emptyList());
    cluster.start(node);
  }

  @After
  public void tearDown() {
    System.setProperty("TEST_BAD_CLI", "false");
  }

  @Test
  public void shouldNotRegister() {
    final Path registrationFile = node.homeDirectory().resolve("plugins/badCLIOptions.init");
    waitForFile(registrationFile);
    assertThat(node.homeDirectory().resolve("plugins/badCliOptions.register")).doesNotExist();
  }

  @Test
  public void shouldNotStart() {
    // depend on the good PicoCLIOptions to tell us when it should be up
    final Path registrationFile = node.homeDirectory().resolve("plugins/pluginLifecycle.started");
    waitForFile(registrationFile);

    assertThat(node.homeDirectory().resolve("plugins/badCliOptions.start")).doesNotExist();
  }

  @Test
  @Ignore("No way to do a graceful shutdown of Besu at the moment.")
  public void shouldNotStop() {
    cluster.stopNode(node);
    waitForFile(node.homeDirectory().resolve("plugins/pluginLifecycle.stopped"));
    assertThat(node.homeDirectory().resolve("plugins/badCliOptions.start")).doesNotExist();
    assertThat(node.homeDirectory().resolve("plugins/badCliOptions.stop")).doesNotExist();
  }

  private void waitForFile(final Path path) {
    final File file = path.toFile();
    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (file.exists()) {
                try (final Stream<String> s = Files.lines(path)) {
                  return s.count() > 0;
                }
              } else {
                return false;
              }
            });
  }
}

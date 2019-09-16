/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.node;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;

public interface BesuNodeRunner {

  static BesuNodeRunner instance() {
    if (Boolean.getBoolean("acctests.runBesuAsProcess")) {
      return new ProcessBesuNodeRunner();
    } else {
      return new ThreadBesuNodeRunner();
    }
  }

  void startNode(BesuNode node);

  void stopNode(BesuNode node);

  void shutdown();

  boolean isActive(String nodeName);

  default void waitForPortsFile(final Path dataDir) {
    final File file = new File(dataDir.toFile(), "besu.ports");
    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (file.exists()) {
                try (final Stream<String> s = Files.lines(file.toPath())) {
                  return s.count() > 0;
                }
              } else {
                return false;
              }
            });
  }
}

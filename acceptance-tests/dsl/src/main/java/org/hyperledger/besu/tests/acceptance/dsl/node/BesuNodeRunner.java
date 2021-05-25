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
package org.hyperledger.besu.tests.acceptance.dsl.node;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;

public interface BesuNodeRunner {

  static BesuNodeRunner instance() {
    if (isProcessBesuNodeRunner()) {
      return new ProcessBesuNodeRunner();
    } else {
      return new ThreadBesuNodeRunner();
    }
  }

  static boolean isProcessBesuNodeRunner() {
    return Boolean.getBoolean("acctests.runBesuAsProcess");
  }

  void startNode(BesuNode node);

  void stopNode(BesuNode node);

  void shutdown();

  boolean isActive(String nodeName);

  default void waitForFile(final Path dataDir, final String fileName) {
    final File file = new File(dataDir.toFile(), fileName);
    Awaitility.waitAtMost(60, TimeUnit.SECONDS)
        .until(
            () -> {
              try (final Stream<String> s = Files.lines(file.toPath())) {
                return s.count() > 0;
              } catch (NoSuchFileException __) {
                return false;
              }
            });
  }

  /**
   * Starts a capture of System.out and System.err. Once getConsole is called the capture will end.
   */
  void startConsoleCapture();

  /**
   * If no capture was started an empty string is returned. After the call the original System.err
   * and out are restored.
   *
   * @return The console output since startConsoleCapture() was called.
   */
  String getConsoleContents();
}

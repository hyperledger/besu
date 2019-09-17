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
package org.hyperledger.besu.tests.acceptance.dsl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class StaticNodesUtils {

  public static Path createStaticNodesFile(final Path directory, final List<String> staticNodes) {
    try {
      final Path tempFile = Files.createTempFile(directory, "", "");
      tempFile.toFile().deleteOnExit();

      final Path staticNodesFile = tempFile.getParent().resolve("static-nodes.json");
      Files.move(tempFile, staticNodesFile);
      staticNodesFile.toFile().deleteOnExit();

      final String json =
          staticNodes.stream()
              .map(s -> String.format("\"%s\"", s))
              .collect(Collectors.joining(",", "[", "]"));
      Files.write(staticNodesFile, json.getBytes(UTF_8));

      return staticNodesFile;
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}

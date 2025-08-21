/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.cli.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BootnodeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(BootnodeResolver.class);

  private BootnodeResolver() {}

  public static List<String> resolve(final List<String> bootNodesList) {
    final List<String> resolved = new ArrayList<>();
    if (bootNodesList == null || bootNodesList.isEmpty()) {
      return resolved;
    }

    for (final String node : bootNodesList) {
      if (node == null || node.isBlank()) {
        continue;
      }

      if (node.startsWith("http://") || node.startsWith("https://")) {
        // Remote list
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(new URL(node).openStream(), UTF_8))) {
          reader.lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(resolved::add);
          LOG.debug("Resolved bootnodes from URL: {}", node);
        } catch (final IOException e) {
          throw new BootnodeResolutionException(
              "Failed to fetch bootnodes from URL: " + node + "; " + e.getMessage(), e);
        }

      } else if (node.startsWith("file://")) {

        try {
          final URI uri = new URI(node);
          final Path path = Paths.get(uri);
          Files.readAllLines(path, UTF_8).stream()
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .forEach(resolved::add);
          LOG.debug("Resolved bootnodes from file URI: {}", node);
        } catch (final Exception e) {
          throw new BootnodeResolutionException(
              "Failed to read bootnodes from file URI: " + node + "; " + e.getMessage(), e);
        }

      } else {
        // Local file path or raw enode
        final Path p = Paths.get(node);
        if (Files.exists(p)) {
          try {
            Files.readAllLines(p, UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .forEach(resolved::add);
            LOG.debug("Resolved bootnodes from local file: {}", node);
            continue;
          } catch (final IOException e) {
            throw new BootnodeResolutionException(
                "Failed to read bootnodes from file: " + node + "; " + e.getMessage(), e);
          }
        }

        // Fallback: treat as raw enode string
        resolved.add(node);
        LOG.debug("Using raw bootnode string: {}", node);
      }
    }

    LOG.debug("Total resolved bootnodes: {}", resolved.size());
    return resolved;
  }

  public static final class BootnodeResolutionException extends RuntimeException {
    public BootnodeResolutionException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}

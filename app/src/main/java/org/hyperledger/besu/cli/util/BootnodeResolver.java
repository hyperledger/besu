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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for resolving bootnodes from various sources such as URLs, files, or raw strings.
 */
public final class BootnodeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(BootnodeResolver.class);

  private BootnodeResolver() {}

  /**
   * Resolves a list of bootnodes from the provided sources.
   *
   * <p>The sources can be URLs (http, https, file), local file paths, or raw bootnode strings.
   * Comments (lines starting with '#') and empty lines are ignored.
   *
   * @param bootNodesList A list of bootnode sources (e.g., URLs, file paths, or raw strings).
   * @return A list of resolved bootnode strings.
   * @throws BootnodeResolutionException If an error occurs while resolving bootnodes.
   */
  public static List<String> resolve(final List<String> bootNodesList) {
    final List<String> resolved = new ArrayList<>();
    if (bootNodesList == null || bootNodesList.isEmpty()) {
      return resolved;
    }

    for (final String node : bootNodesList) {
      if (node == null || node.isBlank()) {
        continue;
      }

      // Try parsing as URI to branch by scheme (http/https/file). Fallback to path/raw.
      URI uri = null;
      try {
        uri = URI.create(node);
      } catch (final IllegalArgumentException ignored) {
        // Not a URI; may be a local path or a raw enode. Handled below.
      }

      final String scheme = (uri != null) ? uri.getScheme() : null;

      if ("http".equalsIgnoreCase(scheme)
          || "https".equalsIgnoreCase(scheme)
          || "file".equalsIgnoreCase(scheme)) {
        // Remote list (URL)
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(uri.toURL().openStream(), UTF_8))) {

          final List<String> lines =
              reader
                  .lines()
                  .map(String::trim)
                  .filter(l -> !l.isEmpty())
                  .filter(l -> !l.startsWith("#"))
                  .toList();

          resolved.addAll(lines);

          LOG.debug("Resolved bootnodes from URL: {}", node);

          LOG.trace("Bootnodes fetched from {}: {}", node, lines);

        } catch (final IOException e) {
          throw new BootnodeResolutionException(
              "Failed to fetch bootnodes from URL: " + node + "; " + e.getMessage(), e);
        }

      } else {

        final Path p = Paths.get(node);
        if (Files.exists(p)) {
          try {
            final List<String> lines = readPathLines(p);
            resolved.addAll(lines);
            LOG.debug("Resolved bootnodes from local file: {}", node);
            if (LOG.isTraceEnabled()) {
              LOG.trace("Bootnodes fetched from {}: {}", node, lines);
            }
            continue;
          } catch (final IOException e) {
            throw new BootnodeResolutionException(
                "Failed to read bootnodes from file: " + node + "; " + e.getMessage(), e);
          }
        }

        resolved.add(node);
        LOG.debug("Using raw bootnode string: {}", node);
      }
    }

    LOG.debug("Total resolved bootnodes: {}", resolved.size());
    return resolved;
  }

  /**
   * Reads and processes lines from a file at the specified path.
   *
   * <p>Trims each line, removes empty lines, and ignores lines starting with '#'.
   *
   * @param path The path to the file.
   * @return A list of processed lines from the file.
   * @throws IOException If an error occurs while reading the file.
   */
  private static List<String> readPathLines(final Path path) throws IOException {
    try (var lines = Files.lines(path, UTF_8)) {
      return lines
          .map(String::trim)
          .filter(l -> !l.isEmpty())
          .filter(l -> !l.startsWith("#"))
          .toList();
    }
  }

  /** Exception thrown when there is an error resolving bootnodes. */
  public static final class BootnodeResolutionException extends RuntimeException {
    /**
     * Constructs a new BootnodeResolutionException with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause The cause of the exception.
     */
    public BootnodeResolutionException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}

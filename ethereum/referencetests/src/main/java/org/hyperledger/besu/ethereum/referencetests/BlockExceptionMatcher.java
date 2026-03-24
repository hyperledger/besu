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
package org.hyperledger.besu.ethereum.referencetests;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maps execution-spec-tests exception keys (e.g. {@code "BlockException.GAS_USED_OVERFLOW"}) to the
 * Besu error-message patterns emitted when that condition is triggered.
 *
 * <p>The mapping is loaded at class-load time from the bundled resource file {@code
 * block-exception-mapping.json}, which mirrors the Python implementation at: <a
 * href="https://github.com/ethereum/execution-specs/blob/mainnet/packages/testing/src/execution_testing/client_clis/clis/besu.py">...</a>
 *
 * <p>To update the mapping, edit {@code block-exception-mapping.json} only — no Java changes
 * needed.
 */
public final class BlockExceptionMatcher {

  private static final Map<String, String> SUBSTRING_MAP;
  private static final Map<String, Pattern> REGEX_MAP;

  static {
    final Map<String, String> substrings = new HashMap<>();
    final Map<String, Pattern> regexes = new HashMap<>();

    try (InputStream in =
        BlockExceptionMatcher.class
            .getClassLoader()
            .getResourceAsStream("block-exception-mapping.json")) {

      if (in == null) {
        throw new IllegalStateException("block-exception-mapping.json not found on classpath");
      }

      final JsonNode root = new ObjectMapper().readTree(in);

      for (var e : root.path("substring").properties()) {
        substrings.put(e.getKey(), e.getValue().asText());
      }
      for (var e : root.path("regex").properties()) {
        regexes.put(e.getKey(), Pattern.compile(e.getValue().asText()));
      }

    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load block-exception-mapping.json", ex);
    }

    SUBSTRING_MAP = Collections.unmodifiableMap(substrings);
    REGEX_MAP = Collections.unmodifiableMap(regexes);
  }

  private BlockExceptionMatcher() {}

  /**
   * Returns whether {@code actualErrorMessage} matches the error pattern associated with {@code
   * exceptionKey}.
   *
   * @param exceptionKey the exception key from the fixture (e.g. {@code
   *     "BlockException.GAS_USED_OVERFLOW"})
   * @param actualErrorMessage the error message produced by Besu during block processing
   * @return {@code true} if the message matches the expected pattern, {@code false} if the key is
   *     unknown or the message does not match
   */
  public static boolean matches(final String exceptionKey, final String actualErrorMessage) {
    final String substringPattern = SUBSTRING_MAP.get(exceptionKey);
    if (substringPattern != null) {
      return actualErrorMessage.contains(substringPattern);
    }
    final Pattern regexPattern = REGEX_MAP.get(exceptionKey);
    if (regexPattern != null) {
      return regexPattern.matcher(actualErrorMessage).find();
    }
    return false;
  }

  /**
   * Returns a human-readable description of the expected pattern for the given exception key, or
   * empty if the key is unknown.
   */
  public static Optional<String> describeExpected(final String exceptionKey) {
    final String s = SUBSTRING_MAP.get(exceptionKey);
    if (s != null) return Optional.of("contains \"" + s + "\"");
    final Pattern p = REGEX_MAP.get(exceptionKey);
    if (p != null) return Optional.of("matches /" + p.pattern() + "/");
    return Optional.empty();
  }
}

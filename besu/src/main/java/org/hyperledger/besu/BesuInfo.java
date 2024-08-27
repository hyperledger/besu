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
package org.hyperledger.besu;

import org.hyperledger.besu.util.platform.PlatformDetector;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represent Besu information such as version, OS etc. Used with --version option and during Besu
 * start.
 */
public final class BesuInfo {
  private static final String CLIENT = "besu";
  private static final String OS = PlatformDetector.getOS();
  private static final String VM = PlatformDetector.getVM();
  private static final String VERSION;
  private static final String COMMIT;

  static {
    String projectVersion = BesuInfo.class.getPackage().getImplementationVersion();
    if (projectVersion == null) {
      // protect against unset project version (e.g. unit tests being run, etc)
      VERSION = null;
      COMMIT = null;
    } else {
      Pattern pattern =
          Pattern.compile("(?<version>\\d+\\.\\d+\\.?\\d?-?\\w*)-(?<commit>[0-9a-fA-F]{8})");
      Matcher matcher = pattern.matcher(projectVersion);
      if (matcher.find()) {
        VERSION = matcher.group("version");
        COMMIT = matcher.group("commit");
      } else {
        throw new RuntimeException("Invalid project version: " + projectVersion);
      }
    }
  }

  private BesuInfo() {}

  /**
   * Generate version-only Besu version
   *
   * @return Besu version in format such as "v23.1.0" or "v23.1.1-dev-ac23d311"
   */
  public static String shortVersion() {
    return VERSION;
  }

  /**
   * Generate full Besu version
   *
   * @return Besu version in format such as "besu/v23.1.1-dev-ac23d311/osx-x86_64/graalvm-java-17"
   *     or "besu/v23.1.0/osx-aarch_64/corretto-java-19"
   */
  public static String version() {
    return String.format("%s/v%s-%s/%s/%s", CLIENT, VERSION, COMMIT, OS, VM);
  }

  /**
   * Generate node name including identity.
   *
   * @param maybeIdentity optional node identity to include in the version string.
   * @return Version with optional identity if provided.
   */
  public static String nodeName(final Optional<String> maybeIdentity) {
    return maybeIdentity
        .map(
            identity ->
                String.format("%s/%s/v%s-%s/%s/%s", CLIENT, identity, VERSION, COMMIT, OS, VM))
        .orElse(version());
  }

  /**
   * Generate the commit hash for this besu version
   *
   * @return the commit hash for this besu version
   */
  public static String commit() {
    return COMMIT;
  }
}

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

public final class BesuInfo {
  private static final String CLIENT = "besu";
  private static final String VERSION = BesuInfo.class.getPackage().getImplementationVersion();
  private static final String OS = PlatformDetector.getOS();
  private static final String VM = PlatformDetector.getVM();

  private BesuInfo() {}

  public static String version() {
    return String.format("%s/v%s/%s/%s", CLIENT, VERSION, OS, VM);
  }

  public static String nodeName(final Optional<String> maybeIdentity) {
    return maybeIdentity
        .map(identity -> String.format("%s/%s/v%s/%s/%s", CLIENT, identity, VERSION, OS, VM))
        .orElse(version());
  }
}

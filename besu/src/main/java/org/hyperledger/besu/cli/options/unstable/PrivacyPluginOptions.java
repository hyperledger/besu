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
package org.hyperledger.besu.cli.options.unstable;

import static picocli.CommandLine.Option;

public class PrivacyPluginOptions {

  public static PrivacyPluginOptions create() {
    return new PrivacyPluginOptions();
  }

  @Option(
      names = "--Xprivacy-plugin-enabled",
      description =
          "Enables the use of a plugin to implement your own privacy strategy (default: ${DEFAULT-VALUE})",
      hidden = true)
  private final Boolean isPrivacyPluginEnabled = false;

  public boolean isPrivacyPluginEnabled() {
    return isPrivacyPluginEnabled;
  }
}

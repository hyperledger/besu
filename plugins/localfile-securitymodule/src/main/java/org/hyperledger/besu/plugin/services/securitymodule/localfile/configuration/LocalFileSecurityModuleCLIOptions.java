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
package org.hyperledger.besu.plugin.services.securitymodule.localfile.configuration;

import static org.hyperledger.besu.plugin.services.securitymodule.localfile.LocalFileSecurityModulePlugin.PICOCLI_NAMESPACE;

import java.io.File;

import picocli.CommandLine.Option;

public class LocalFileSecurityModuleCLIOptions {
  private static final String CLI_OPTION_NAME =
      "--plugin-" + PICOCLI_NAMESPACE + "-private-key-file";
  private File privateKeyFile = null;

  @Option(
      names = {CLI_OPTION_NAME},
      paramLabel = "<PATH>",
      description =
          "The node's private key file (default: a file named \"key\" in the Besu data folder)")
  public void setPrivateKeyFile(final File privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }

  public File getPrivateKeyFile() {
    return privateKeyFile;
  }
}

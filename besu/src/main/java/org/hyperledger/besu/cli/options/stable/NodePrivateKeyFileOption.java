/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP;

import java.io.File;

import picocli.CommandLine;

public class NodePrivateKeyFileOption {

  public static NodePrivateKeyFileOption create() {
    return new NodePrivateKeyFileOption();
  }

  @CommandLine.Option(
      names = {"--node-private-key-file"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description =
          "The node's private key file (default: a file named \"key\" in the Besu data directory)")
  private final File nodePrivateKeyFile = null;

  public File getNodePrivateKeyFile() {
    return nodePrivateKeyFile;
  }
}

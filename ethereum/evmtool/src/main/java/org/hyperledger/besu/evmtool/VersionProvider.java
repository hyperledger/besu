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
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.BesuInfo;

import picocli.CommandLine;

/**
 * The VersionProvider class is responsible for providing the version of the Besu EVM tool. It
 * implements the IVersionProvider interface from the picocli library.
 *
 * <p>The getVersion method returns a string array containing the version of the Besu EVM tool.
 */
public class VersionProvider implements CommandLine.IVersionProvider {

  /**
   * Default constructor for the VersionProvider class. This constructor does not perform any
   * operations.
   */
  public VersionProvider() {}

  /**
   * This method returns the version of the Besu EVM tool.
   *
   * @return A string array containing the version of the Besu EVM tool.
   */
  @Override
  public String[] getVersion() {
    // This version string is used in the execution spec tests to identify the client.
    // If modified, update the `detect_binary_pattern` variable in the following repository:
    // https://github.com/ethereum/execution-spec-tests/blob/main/src/ethereum_clis/clis/besu.py
    return new String[] {"Besu evm " + BesuInfo.shortVersion()};
  }
}

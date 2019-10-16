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
package org.hyperledger.besu.cli.subcommands.networkcreate.generate;

import org.hyperledger.besu.cli.subcommands.networkcreate.model.Node;

import java.nio.file.Path;
import javax.annotation.Nullable;

@FunctionalInterface
public interface Generatable {

  /**
   * Generate resources related to the object implementing this interface.
   *
   * <p>For instance for a config it can be the directory, For a network, it's mainly the genesis
   * file For a node it's the directory and key
   *
   * @param outputDirectoryPath where to generate object resources
   * @param directoryHandler the handler to generate filesystem resources
   * @param node the related node. Optional, only for context.
   * @return the path where object resources where generated, can be a directory or a file
   */
  Path generate(
      final Path outputDirectoryPath,
      final DirectoryHandler directoryHandler,
      @Nullable final Node node);
}

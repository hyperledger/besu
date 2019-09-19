/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.subcommands.networkcreate.generate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.Files.createDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
// TODO Handle errors
public class DirectoryHandler {

  public void create(final Path outputDirectoryPath) {
    checkNotNull(outputDirectoryPath);
    final File outputDirectory = outputDirectoryPath.toFile();

    if (outputDirectory.exists()
        && outputDirectory.isDirectory()
        && Objects.requireNonNull(outputDirectory.list()).length > 0) {
      throw new IllegalArgumentException("Output directory must be empty.");
    } else if (!outputDirectory.exists()) {
      try {
        createDirectory(outputDirectoryPath);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create directory.");
      }
    }
  }

  public String getSafeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
  }
}

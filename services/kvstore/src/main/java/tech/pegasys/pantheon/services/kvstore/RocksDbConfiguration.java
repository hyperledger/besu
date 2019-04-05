/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.services.kvstore;

import java.nio.file.Path;

import picocli.CommandLine;

public class RocksDbConfiguration {

  private final Path databaseDir;
  private final int maxOpenFiles;

  public RocksDbConfiguration(final Path databaseDir, final int maxOpenFiles) {
    this.databaseDir = databaseDir;
    this.maxOpenFiles = maxOpenFiles;
  }

  public Path getDatabaseDir() {
    return databaseDir;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public static class Builder {

    Path databaseDir;

    @CommandLine.Option(
        names = {"--Xrocksdb-max-open-files"},
        hidden = true,
        defaultValue = "1024",
        paramLabel = "<INTEGER>",
        description = "Max number of files RocksDB will open (default: ${DEFAULT-VALUE})")
    int maxOpenFiles;

    public Builder databaseDir(final Path databaseDir) {
      this.databaseDir = databaseDir;
      return this;
    }

    public Builder maxOpenFiles(final int maxOpenFiles) {
      this.maxOpenFiles = maxOpenFiles;
      return this;
    }

    public RocksDbConfiguration build() {
      return new RocksDbConfiguration(databaseDir, maxOpenFiles);
    }
  }
}

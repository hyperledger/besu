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
package org.hyperledger.besu.cli.subcommands.storage;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.rocksdb.RocksDBException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** The RocksDB subcommand. */
@Command(
    name = "rocksdb",
    description = "Print RocksDB information",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {RocksDbSubCommand.RocksDbUsage.class, RocksDbSubCommand.RocksDbStats.class})
public class RocksDbSubCommand implements Runnable {

  @SuppressWarnings("unused")
  @ParentCommand
  private StorageSubCommand storageSubCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  /** Default Constructor. */
  public RocksDbSubCommand() {}

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  @Command(
      name = "usage",
      description = "Print disk usage",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class RocksDbUsage implements Runnable {

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @SuppressWarnings("unused")
    @ParentCommand
    private RocksDbSubCommand rocksDbSubCommand;

    @Override
    public void run() {

      final PrintWriter out = spec.commandLine().getOut();

      final String dbPath =
          rocksDbSubCommand
              .storageSubCommand
              .besuCommand
              .dataDir()
              .resolve(DATABASE_PATH)
              .toString();

      RocksDbHelper.printTableHeader(out);

      final List<RocksDbHelper.ColumnFamilyUsage> columnFamilyUsages = new ArrayList<>();
      RocksDbHelper.forEachColumnFamily(
          dbPath,
          (rocksdb, cfHandle) -> {
            try {
              columnFamilyUsages.add(
                  RocksDbHelper.getAndPrintUsageForColumnFamily(rocksdb, cfHandle, out));
            } catch (RocksDBException e) {
              throw new RuntimeException(e);
            }
          });
      RocksDbHelper.printTotals(out, columnFamilyUsages);
    }
  }

  @Command(
      name = "x-stats",
      description = "Print rocksdb stats",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class RocksDbStats implements Runnable {

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @SuppressWarnings("unused")
    @ParentCommand
    private RocksDbSubCommand rocksDbSubCommand;

    @Override
    public void run() {

      final PrintWriter out = spec.commandLine().getOut();

      final String dbPath =
          rocksDbSubCommand
              .storageSubCommand
              .besuCommand
              .dataDir()
              .resolve(DATABASE_PATH)
              .toString();

      out.println("Column Family Stats...");
      RocksDbHelper.forEachColumnFamily(
          dbPath,
          (rocksdb, cfHandle) -> {
            try {
              RocksDbHelper.printStatsForColumnFamily(rocksdb, cfHandle, out);
            } catch (RocksDBException e) {
              throw new RuntimeException(e);
            }
          });
    }
  }
}

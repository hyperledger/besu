/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.subcommands.operator;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** The RocksDB subcommand. */
@Command(
    name = "x-rocksdb",
    description = "Print RocksDB information",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {RocksDbSubCommand.RocksDbUsage.class})
public class RocksDbSubCommand implements Runnable {

  @ParentCommand private OperatorSubCommand parentCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }

  @Command(
      name = "usage",
      description = "Prints disk usage",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class RocksDbUsage implements Runnable {

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @SuppressWarnings("unused")
    @ParentCommand
    private RocksDbSubCommand parentCommand;

    @Override
    public void run() {

      final PrintWriter out = spec.commandLine().getOut();

      final String dbPath =
          parentCommand
              .parentCommand
              .parentCommand
              .dataDir()
              .toString()
              .concat("/")
              .concat(DATABASE_PATH);

      RocksDB.loadLibrary();
      Options options = new Options();
      options.setCreateIfMissing(true);

      // Open the RocksDB database with multiple column families
      List<byte[]> cfNames;
      try {
        cfNames = RocksDB.listColumnFamilies(options, dbPath);
      } catch (RocksDBException e) {
        throw new RuntimeException(e);
      }
      List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
      List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
      for (byte[] cfName : cfNames) {
        cfDescriptors.add(new ColumnFamilyDescriptor(cfName));
      }
      try (final RocksDB rocksdb = RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles)) {
        for (ColumnFamilyHandle cfHandle : cfHandles) {
          RocksDbUsageHelper.printUsageForColumnFamily(rocksdb, cfHandle, out);
        }
      } catch (RocksDBException e) {
        throw new RuntimeException(e);
      } finally {
        for (ColumnFamilyHandle cfHandle : cfHandles) {
          cfHandle.close();
        }
      }
    }
  }
}

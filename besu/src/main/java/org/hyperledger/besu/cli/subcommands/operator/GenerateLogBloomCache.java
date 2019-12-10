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
 *
 */

package org.hyperledger.besu.cli.subcommands.operator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.plugin.data.BlockHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "generate-log-bloom-cache",
    description = "Generate cached values of block log bloom filters.",
    mixinStandardHelpOptions = true)
public class GenerateLogBloomCache implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  public static final int BLOCKS_PER_FILE = 100_000;
  public static final String CACHE_DIRECTORY_NAME = "caches";

  @Option(
      names = "--start-block",
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description =
          "The block to start generating indexes.  Must be an increment of "
              + BLOCKS_PER_FILE
              + " (default: ${DEFAULT-VALUE})",
      arity = "1..1")
  private final Long startBlock = 0L;

  @Option(
      names = "--end-block",
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description =
          "The block to start generating indexes (exclusive). (default: last block divisible by "
              + BLOCKS_PER_FILE
              + ")",
      arity = "1..1")
  private final Long endBlock = -1L;

  @ParentCommand private OperatorSubCommand parentCommand;

  @Override
  public void run() {
    checkPreconditions();
    generateLogBloomCache();
  }

  private void checkPreconditions() {
    checkNotNull(parentCommand.parentCommand.dataDir());
    checkState(
        startBlock % BLOCKS_PER_FILE == 0,
        "Start block must be an even increment of %s",
        BLOCKS_PER_FILE);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void generateLogBloomCache() {
    final Path cacheDir = parentCommand.parentCommand.dataDir().resolve(CACHE_DIRECTORY_NAME);
    cacheDir.toFile().mkdirs();
    generateLogBloomCache(
        startBlock,
        endBlock,
        cacheDir,
        createBesuController().getProtocolContext().getBlockchain());
  }

  public static void generateLogBloomCache(
      final long start, final long stop, final Path cacheDir, final Blockchain blockchain) {
    final long stopBlock =
        stop < 0
            ? (blockchain.getChainHeadBlockNumber() / BLOCKS_PER_FILE) * BLOCKS_PER_FILE
            : stop;
    LOG.debug("Start block: {} Stop block: {} Path: {}", start, stopBlock, cacheDir);
    checkArgument(start % BLOCKS_PER_FILE == 0, "Start block must be at the beginning of a file");
    checkArgument(stopBlock % BLOCKS_PER_FILE == 0, "End block must be at the beginning of a file");
    try {
      FileOutputStream fos = null;
      for (long blockNum = start; blockNum < stopBlock; blockNum++) {
        if (blockNum % BLOCKS_PER_FILE == 0 || fos == null) {
          LOG.info("Indexing block {}", blockNum);
          if (fos != null) {
            fos.close();
          }
          fos = new FileOutputStream(createCacheFile(blockNum, cacheDir));
        }
        final BlockHeader header = blockchain.getBlockHeader(blockNum).orElseThrow();
        final byte[] logs = header.getLogsBloom().getByteArray();
        checkNotNull(logs);
        checkState(logs.length == 256, "BloomBits are not the correct length");
        fos.write(logs);
      }
    } catch (final Exception e) {
      LOG.error("Unhandled indexing exception", e);
    }
  }

  private static File createCacheFile(final long blockNumber, final Path cacheDir) {
    return cacheDir.resolve("logBloom-" + (blockNumber / BLOCKS_PER_FILE) + ".index").toFile();
  }

  private BesuController<?> createBesuController() {
    return parentCommand.parentCommand.buildController();
  }
}

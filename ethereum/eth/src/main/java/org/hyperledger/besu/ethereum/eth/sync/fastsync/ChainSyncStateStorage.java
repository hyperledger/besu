/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

import com.google.common.io.MoreFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe storage for ChainSyncState with atomic file operations. Only the chain downloader
 * should write to this storage.
 */
public class ChainSyncStateStorage {
  private static final Logger LOG = LoggerFactory.getLogger(ChainSyncStateStorage.class);
  private static final String STATE_FILE_NAME = "chain-sync-state.rlp";
  private static final byte FORMAT_VERSION = 1;

  private final File stateFile;
  private final File tempFile;
  private final Object writeLock = new Object();

  public ChainSyncStateStorage(final Path dataDirectory) {
    this.stateFile = dataDirectory.resolve(STATE_FILE_NAME).toFile();
    this.tempFile = dataDirectory.resolve(STATE_FILE_NAME + ".tmp").toFile();
  }

  /**
   * Loads the chain sync state from storage.
   *
   * @param headerReader function to deserialize block headers
   * @return the loaded state, or null if no state exists
   */
  public synchronized ChainSyncState loadState(final Function<RLPInput, BlockHeader> headerReader) {
    synchronized (writeLock) {
      if (!stateFile.exists()) {
        LOG.debug("No chain sync state file found");
        return null;
      }

      try {
        final byte[] data = Files.readAllBytes(stateFile.toPath());
        final BytesValueRLPInput input =
            new BytesValueRLPInput(org.apache.tuweni.bytes.Bytes.wrap(data), false);

        input.enterList();

        // Read version
        final byte version = input.readByte();
        if (version != FORMAT_VERSION) {
          LOG.warn("Unknown chain sync state format version: {}", version);
          return null;
        }

        // Read pivot block header
        final BlockHeader pivotBlockHeader = headerReader.apply(input);

        // Read progress fields
        final long headerDownloadStopBlock = input.readLongScalar();
        final long bodiesDownloadStartBlock = input.readLongScalar();
        final boolean headersDownloadComplete = input.readByte() == 1;

        input.leaveList();

        LOG.debug(
            "Loaded chain sync state: pivot={}, stopBlock={}, startBlock={}, complete={}",
            pivotBlockHeader.getNumber(),
            headerDownloadStopBlock,
            bodiesDownloadStartBlock,
            headersDownloadComplete);

        return new ChainSyncState(
            pivotBlockHeader,
            headerDownloadStopBlock,
            bodiesDownloadStartBlock,
            headersDownloadComplete);

      } catch (final IOException e) {
        throw new IllegalStateException(
            "Unable to read chain sync state file: " + stateFile.getAbsolutePath(), e);
      }
    }
  }

  /**
   * Stores the chain sync state atomically. Uses temp file + rename for atomicity.
   *
   * @param state the state to store
   */
  public synchronized void storeState(final ChainSyncState state) {
    synchronized (writeLock) {
      try {
        // Clean up any leftover temp file
        if (tempFile.exists()) {
          tempFile.delete();
        }

        // Write to temp file
        final BytesValueRLPOutput output = new BytesValueRLPOutput();
        output.startList();

        // Write version
        output.writeByte(FORMAT_VERSION);

        // Write pivot block header
        state.getPivotBlockHeader().writeTo(output);

        // Write progress fields
        output.writeLongScalar(state.getHeaderDownloadStopBlock());
        output.writeLongScalar(state.getBodiesDownloadStartBlock());
        output.writeByte((byte) (state.isHeadersDownloadComplete() ? 1 : 0));

        output.endList();

        // Write to temp file
        Files.write(tempFile.toPath(), output.encoded().toArrayUnsafe());

        // Atomic rename
        Files.move(
            tempFile.toPath(),
            stateFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);

        LOG.debug(
            "Stored chain sync state: pivot={}, stopBlock={}, startBlock={}, complete={}",
            state.getPivotBlockNumber(),
            state.getHeaderDownloadStopBlock(),
            state.getBodiesDownloadStartBlock(),
            state.isHeadersDownloadComplete());

      } catch (final IOException e) {
        throw new IllegalStateException(
            "Unable to store chain sync state file: " + stateFile.getAbsolutePath(), e);
      }
    }
  }

  /** Deletes the chain sync state file. */
  public synchronized void deleteState() {
    synchronized (writeLock) {
      try {
        if (stateFile.exists()) {
          MoreFiles.deleteRecursively(stateFile.toPath());
        }
        if (tempFile.exists()) {
          MoreFiles.deleteRecursively(tempFile.toPath());
        }
      } catch (final IOException e) {
        LOG.error("Failed to delete chain sync state file", e);
      }
    }
  }
}

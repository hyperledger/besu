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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.Files;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supports persisting fast sync state to disk to enable resuming after a restart.
 *
 * <p>Note that a {@link FastSyncState} with a block number selected but no pivot block header is
 * not stored. If we haven't yet retrieved and confirmed the actual block header we can't have
 * started downloading data so should pick a new pivot block when resuming. Once we have the pivot
 * block header we want to continue with that pivot block so the world state downloaded matches up.
 */
public class FastSyncStateStorage {
  private static final Logger LOG = LoggerFactory.getLogger(FastSyncStateStorage.class);
  private static final String PIVOT_BLOCK_HEADER_FILENAME = "pivotBlockHeader.rlp";
  private static final byte FORMAT_VERSION = 2; // Incremented for backward header progress tracking
  private final File pivotBlockHeaderFile;

  public FastSyncStateStorage(final Path fastSyncDataDir) {
    pivotBlockHeaderFile = fastSyncDataDir.resolve(PIVOT_BLOCK_HEADER_FILENAME).toFile();
  }

  public boolean isFastSyncInProgress() {
    return pivotBlockHeaderFile.isFile();
  }

  public FastSyncState loadState(final BlockHeaderFunctions blockHeaderFunctions) {
    try {
      if (!isFastSyncInProgress()) {
        return FastSyncState.EMPTY_SYNC_STATE;
      }
      final Bytes rlp = Bytes.wrap(Files.toByteArray(pivotBlockHeaderFile));
      final BytesValueRLPInput input = new BytesValueRLPInput(rlp, false);

      if (isVersionedFormat(input)) {
        // Versioned format
        input.enterList();
        final byte version = input.readByte();

        if (version == 2) {
          final Optional<BlockHeader> lowestBlockHeader;
          // Version 2: [version, header, sourceIsTrusted, lowestBlockHeader,
          // backwardDownloadComplete]
          final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
          if (input.nextIsList()) {
            lowestBlockHeader = Optional.of(BlockHeader.readFrom(input, blockHeaderFunctions));
          } else {
            lowestBlockHeader = Optional.empty();
            input.skipNext();
          }
          final boolean sourceIsTrusted = input.readByte() != 0;
          final boolean backwardDownloadComplete = input.readByte() != 0;
          input.leaveList();

          final FastSyncState state = new FastSyncState(header, sourceIsTrusted);
          lowestBlockHeader.ifPresent(state::setLowestBlockHeaderDownloaded);
          state.setBackwardHeaderDownloadComplete(backwardDownloadComplete);
          return state;

        } else if (version == 1) {
          // Version 1: [version, header, sourceIsTrusted]
          final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
          final boolean sourceIsTrusted = input.readByte() != 0;
          input.leaveList();
          return new FastSyncState(header, sourceIsTrusted);
        } else {
          throw new IllegalStateException("Unsupported fast sync state format version: " + version);
        }
      } else {
        // Legacy format (no version byte, just the header)
        // Read as a simple header for backward compatibility
        input.reset();
        final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
        return new FastSyncState(header, false); // Default sourceIsTrusted to false
      }
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to read fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }

  public void storeState(final FastSyncState state) {
    if (!state.hasPivotBlockHeader()) {
      if (!pivotBlockHeaderFile.delete() && pivotBlockHeaderFile.exists()) {
        LOG.error(
            "Unable to delete fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
      }
      return;
    }
    try {
      final BytesValueRLPOutput output = new BytesValueRLPOutput();
      output.startList();
      output.writeByte(FORMAT_VERSION);
      state.getPivotBlockHeader().get().writeTo(output);
      if (state.getLowestBlockHeaderDownloaded().isPresent()) {
        state.getLowestBlockHeaderDownloaded().get().writeTo(output);
      } else {
        output.writeByte((byte) 0);
      }
      output.writeByte((byte) (state.isSourceTrusted() ? 1 : 0));
      // Store backward header download progress (version 2 fields)

      output.writeByte((byte) (state.isBackwardHeaderDownloadComplete() ? 1 : 0));
      output.endList();
      Files.write(output.encoded().toArrayUnsafe(), pivotBlockHeaderFile);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to store fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }

  /**
   * Determines whether the RLP input follows the versioned format.
   *
   * @param input The RLP input to check
   * @return true if the input is in the new format, false if it's in the legacy format
   */
  private boolean isVersionedFormat(final BytesValueRLPInput input) {
    int listSize = input.enterList();
    // Versioned format has 3 or 5 items (v1: 3 items, v2: 5 items)
    // Legacy format has variable items depending on header structure
    boolean isVersionedFormat = listSize == 3 || listSize == 5;
    input.reset();
    return isVersionedFormat;
  }
}

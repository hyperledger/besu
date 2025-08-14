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
  private static final byte FORMAT_VERSION = 1;
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
        // Versioned format: list containing [version, header, sourceIsTrusted]
        input.enterList();
        final byte version = input.readByte();
        if (version != FORMAT_VERSION) {
          throw new IllegalStateException("Unsupported fast sync state format version: " + version);
        }
        final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
        final boolean sourceIsTrusted = input.readByte() != 0;
        input.leaveList();
        return new FastSyncState(header, sourceIsTrusted);
      } else {
        // Legacy format: just the header itself
        return new FastSyncState(BlockHeader.readFrom(input, blockHeaderFunctions), false);
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
      output.writeByte((byte) (state.isSourceTrusted() ? 1 : 0));
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
    // Versioned format has 3 items: version, header, sourceIsTrusted
    boolean isVersionedFormat = listSize == 3;
    input.reset();
    return isVersionedFormat;
  }
}

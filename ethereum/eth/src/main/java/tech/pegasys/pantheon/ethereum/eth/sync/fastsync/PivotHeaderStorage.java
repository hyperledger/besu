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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.Files;

public class PivotHeaderStorage {

  private static final String PIVOT_BLOCK_HEADER_FILENAME = "pivotBlockHeader.rlp";
  private final File pivotBlockHeaderFile;

  public PivotHeaderStorage(final Path fastSyncDataDir) {
    pivotBlockHeaderFile = fastSyncDataDir.resolve(PIVOT_BLOCK_HEADER_FILENAME).toFile();
  }

  public boolean isFastSyncInProgress() {
    return pivotBlockHeaderFile.isFile();
  }

  public Optional<BlockHeader> loadPivotBlockHeader(final BlockHashFunction blockHashFunction) {
    try {
      if (!isFastSyncInProgress()) {
        return Optional.empty();
      }
      final BytesValue rlp = BytesValue.wrap(Files.toByteArray(pivotBlockHeaderFile));
      return Optional.of(
          BlockHeader.readFrom(new BytesValueRLPInput(rlp, false), blockHashFunction));
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to read fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }

  public void storePivotBlockHeader(final BlockHeader pivotBlockHeader) {
    try {
      final BytesValueRLPOutput output = new BytesValueRLPOutput();
      pivotBlockHeader.writeTo(output);
      Files.write(output.encoded().getArrayUnsafe(), pivotBlockHeaderFile);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Unable to store fast sync status file: " + pivotBlockHeaderFile.getAbsolutePath());
    }
  }
}

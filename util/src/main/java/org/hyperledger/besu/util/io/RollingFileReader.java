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

package org.hyperledger.besu.util.io;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.function.BiFunction;

import org.xerial.snappy.Snappy;

public class RollingFileReader implements Closeable {
  private final BiFunction<Integer, Boolean, Path> filenameGenerator;
  private final boolean compressed;
  private int currentPosition;
  private int fileNumber;
  private RandomAccessFile in;
  private final RandomAccessFile index;
  private boolean done = false;

  public RollingFileReader(
      final BiFunction<Integer, Boolean, Path> filenameGenerator, final boolean compressed)
      throws IOException {
    this.filenameGenerator = filenameGenerator;
    this.compressed = compressed;
    final Path firstInputFile = filenameGenerator.apply(fileNumber, compressed);
    in = new RandomAccessFile(firstInputFile.toFile(), "r");
    index = new RandomAccessFile(RollingFileWriter.dataFileToIndex(firstInputFile).toFile(), "r");
    fileNumber = index.readInt();
    currentPosition = index.readUnsignedShort();
  }

  public byte[] readBytes() throws IOException {
    byte[] raw;
    try {
      final int start = currentPosition;
      final int nextFile = index.readUnsignedShort();
      currentPosition = index.readInt();
      if (nextFile == fileNumber) {
        final int len = currentPosition - start;
        raw = new byte[len];
        in.read(raw);
      } else {
        raw = new byte[(int) (in.length() - in.getFilePointer())];
        in.read(raw);
        in.close();
        fileNumber = nextFile;
        in = new RandomAccessFile(filenameGenerator.apply(fileNumber, compressed).toFile(), "r");
        if (currentPosition != 0) {
          in.seek(currentPosition);
        }
      }
    } catch (final EOFException e) {
      // this happens when we read the last value, where there is no next index.
      raw = new byte[(int) (in.length() - in.getFilePointer())];
      in.read(raw);
      done = true;
    }
    return compressed ? Snappy.uncompress(raw) : raw;
  }

  public void seek(final long position) throws IOException {
    index.seek(position * 6);
    final int oldFile = fileNumber;
    fileNumber = index.readUnsignedShort();
    currentPosition = index.readInt();
    if (oldFile != fileNumber) {
      in = new RandomAccessFile(filenameGenerator.apply(fileNumber, compressed).toFile(), "r");
    }
    in.seek(currentPosition);
  }

  @Override
  public void close() throws IOException {
    in.close();
    index.close();
  }

  public boolean isDone() {
    return done;
  }
}

/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.util.era1.Era1Type;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.bouncycastle.util.Pack;
import org.xerial.snappy.SnappyFramedOutputStream;

/** A helper class for writing ERA1 files */
public class Era1FileWriter implements Closeable {
  private static final List<Era1Type> COMPRESSED_ERA1_TYPES =
      List.of(
          Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER,
          Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY,
          Era1Type.COMPRESSED_EXECUTION_BLOCK_RECEIPTS);
  private final FileOutputStream writer;
  private final SnappyFactory snappyFactory;

  private long position = 0;

  /**
   * Constructs a new Era1FileWriter with the supplied parameters
   *
   * @param fileOutputStream The FileOutputStream to be written to
   * @param snappyFactory A SnappyFactory to produce objects to aid in snappy compression
   */
  public Era1FileWriter(
      final FileOutputStream fileOutputStream, final SnappyFactory snappyFactory) {
    this.writer = fileOutputStream;
    this.snappyFactory = snappyFactory;
  }

  /**
   * Writes an ERA1 file section, compressing the content using snappy compression if required
   *
   * @param era1Type The type of the ERA1 file section
   * @param content The raw bytes of content
   * @throws IOException If writing to file fails
   */
  public void writeSection(final Era1Type era1Type, final byte[] content) throws IOException {
    byte[] actualContent;
    if (COMPRESSED_ERA1_TYPES.contains(era1Type)) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (SnappyFramedOutputStream snappyOutputStream =
          snappyFactory.createFramedOutputStream(byteArrayOutputStream)) {
        snappyOutputStream.write(content);
      }
      actualContent = byteArrayOutputStream.toByteArray();
    } else {
      actualContent = content;
    }

    byte[] typeCode = era1Type.getTypeCode();
    writer.write(typeCode);
    byte[] length = convertLengthToLittleEndianByteArray(actualContent.length);
    writer.write(length);
    writer.write(actualContent);

    position += typeCode.length + length.length + actualContent.length;
  }

  /**
   * Gets the current position of the writer in the file being written
   *
   * @return The current position of the writer in the file being written
   */
  public long getPosition() {
    return position;
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }

  private byte[] convertLengthToLittleEndianByteArray(final int length) {
    byte[] lengthBytes = Pack.intToLittleEndian(length);
    return new byte[] {lengthBytes[0], lengthBytes[1], lengthBytes[2], lengthBytes[3], 0, 0};
  }
}

/*
 * Copyright contributors to Hyperledger Besu
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

package org.hyperledger.besu.evm.code;

import java.io.ByteArrayInputStream;

import org.apache.tuweni.bytes.Bytes;

public class EOFLayout {

  static final int SECTION_TERMINATOR = 0x00;
  static final int SECTION_TYPES = 0x01;
  static final int SECTION_CODE = 0x02;
  static final int SECTION_DATA = 0x03;

  static final int MAX_SUPPORTED_VERSION = 1;

  private final Bytes container;
  private final int version;
  private final CodeSection[] codeSections;
  private final String invalidReason;

  private EOFLayout(final Bytes container, final int version, final CodeSection[] codeSections) {
    this.container = container;
    this.version = version;
    this.codeSections = codeSections;
    this.invalidReason = null;
  }

  private EOFLayout(final Bytes container, final int version, final String invalidReason) {
    this.container = container;
    this.version = version;
    this.codeSections = null;
    this.invalidReason = invalidReason;
  }

  private static EOFLayout invalidLayout(
      final Bytes container, final int version, final String invalidReason) {
    return new EOFLayout(container, version, invalidReason);
  }

  private static String readKind(final ByteArrayInputStream inputStream, final int expectedKind) {
    int kind = inputStream.read();
    if (kind == -1) {
      return "Improper section headers";
    }
    if (kind != expectedKind) {
      return "Expected kind " + expectedKind + " but read kind " + kind;
    }
    return null;
  }

  public static EOFLayout parseEOF(final Bytes container) {
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(container.toArrayUnsafe());

    if (inputStream.available() < 3) {
      return invalidLayout(container, -1, "EOF Container too small");
    }
    if (inputStream.read() != 0xEF) {
      return invalidLayout(container, -1, "EOF header byte 0 incorrect");
    }
    if (inputStream.read() != 0x0) {
      return invalidLayout(container, -1, "EOF header byte 1 incorrect");
    }

    final int version = inputStream.read();
    if (version > MAX_SUPPORTED_VERSION || version < 1) {
      return invalidLayout(container, version, "Unsupported EOF Version " + version);
    }

    String error = readKind(inputStream, SECTION_TYPES);
    if (error != null) {
      return invalidLayout(container, version, error);
    }
    int typesLength = readUnsignedShort(inputStream);
    if (typesLength < 0) {
      return invalidLayout(container, version, "Invalid Types section size");
    }

    error = readKind(inputStream, SECTION_CODE);
    if (error != null) {
      return invalidLayout(container, version, error);
    }
    int codeSectionCount = readUnsignedShort(inputStream);
    if (codeSectionCount < 0) {
      return invalidLayout(container, version, "Invalid Code section count");
    }
    int[] codeSectionSizes = new int[codeSectionCount];
    for (int i = 0; i < codeSectionCount; i++) {
      int size = readUnsignedShort(inputStream);
      if (size <= 0) {
        return invalidLayout(container, version, "Invalid Code section size for section " + i);
      }
      codeSectionSizes[i] = size;
    }

    error = readKind(inputStream, SECTION_DATA);
    if (error != null) {
      return invalidLayout(container, version, error);
    }
    int dataSize = readUnsignedShort(inputStream);
    if (dataSize < 0) {
      return invalidLayout(container, version, "Invalid Data section size");
    }

    error = readKind(inputStream, SECTION_TERMINATOR);
    if (error != null) {
      return invalidLayout(container, version, error);
    }
    int[][] typeData = new int[codeSectionCount][3];
    for (int i = 0; i < codeSectionCount; i++) {
      // input stream keeps spitting out -1 if we run out of data, so no exceptions
      typeData[i][0] = inputStream.read();
      typeData[i][1] = inputStream.read();
      typeData[i][2] = readUnsignedShort(inputStream);
    }
    if (typeData[codeSectionCount - 1][2] == -1) {
      return invalidLayout(container, version, "Incomplete type section");
    }
    if (typeData[0][0] != 0 || typeData[0][1] != 0) {
      return invalidLayout(
          container, version, "Code section does not have zero inputs and outputs");
    }
    CodeSection[] codeSections = new CodeSection[codeSectionCount];
    for (int i = 0; i < codeSectionCount; i++) {
      int codeSectionSize = codeSectionSizes[i];
      byte[] code = new byte[codeSectionSize];
      if (inputStream.read(code, 0, codeSectionSize) != codeSectionSize) {
        return invalidLayout(container, version, "Incomplete code section " + i);
      }
      codeSections[i] =
          new CodeSection(Bytes.wrap(code), typeData[i][0], typeData[i][1], typeData[i][2]);
    }

    if (inputStream.skip(dataSize) != dataSize) {
      return invalidLayout(container, version, "Incomplete data section");
    }
    if (inputStream.read() != -1) {
      return invalidLayout(container, version, "Dangling data after end of all sections");
    }

    return new EOFLayout(container, version, codeSections);
  }

  static int readUnsignedShort(final ByteArrayInputStream inputStream) {
    if (inputStream.available() < 2) {
      return -1;
    } else {
      return inputStream.read() << 8 | inputStream.read();
    }
  }

  public Bytes getContainer() {
    return container;
  }

  public int getVersion() {
    return version;
  }

  public CodeSection[] getCodeSections() {
    return codeSections;
  }

  public String getInvalidReason() {
    return invalidReason;
  }

  public boolean isValid() {
    return invalidReason == null;
  }
}

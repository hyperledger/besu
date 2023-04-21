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

/** The EOF layout. */
public class EOFLayout {

  public static final byte EOF_PREFIX_BYTE = (byte) 0xEF;

  /** header terminator */
  static final int SECTION_TERMINATOR = 0x00;
  /** type data (stack heights, inputs/outputs) */
  static final int SECTION_TYPES = 0x01;
  /** code */
  static final int SECTION_CODE = 0x02;
  /** data */
  static final int SECTION_DATA = 0x03;
  /** sub-EOF containers for create */
  static final int SECTION_CONTAINER = 0x04;

  /** The Max supported section. */
  static final int MAX_SUPPORTED_VERSION = 1;

  private final Bytes container;
  private final int version;
  private final CodeSection[] codeSections;
  private final EOFLayout[] containers;
  private final String invalidReason;

  private EOFLayout(
      final Bytes container,
      final int version,
      final CodeSection[] codeSections,
      final EOFLayout[] containers) {
    this.container = container;
    this.version = version;
    this.codeSections = codeSections;
    this.containers = containers;
    this.invalidReason = null;
  }

  private EOFLayout(final Bytes container, final int version, final String invalidReason) {
    this.container = container;
    this.version = version;
    this.codeSections = null;
    this.containers = null;
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

  private static boolean checkKind(final ByteArrayInputStream inputStream, final int expectedKind) {
    inputStream.mark(1);
    int kind = inputStream.read();
    inputStream.reset();
    return kind == expectedKind;
  }

  /**
   * Parse EOF.
   *
   * @param container the container
   * @return the eof layout
   */
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
    if (typesLength <= 0) {
      return invalidLayout(container, version, "Invalid Types section size");
    }

    error = readKind(inputStream, SECTION_CODE);
    if (error != null) {
      return invalidLayout(container, version, error);
    }
    int codeSectionCount = readUnsignedShort(inputStream);
    if (codeSectionCount <= 0) {
      return invalidLayout(container, version, "Invalid Code section count");
    }
    if (codeSectionCount * 4 != typesLength) {
      return invalidLayout(
          container,
          version,
          "Type section length incompatible with code section count - 0x"
              + Integer.toHexString(codeSectionCount)
              + " * 4 != 0x"
              + Integer.toHexString(typesLength));
    }
    if (codeSectionCount > 1024) {
      return invalidLayout(
          container,
          version,
          "Too many code sections - 0x" + Integer.toHexString(codeSectionCount));
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

    int containerSectionCount;
    int[] containerSectionSizes;
    if (checkKind(inputStream, SECTION_CONTAINER)) {
      error = readKind(inputStream, SECTION_CONTAINER);
      if (error != null) {
        return invalidLayout(container, version, error);
      }
      containerSectionCount = readUnsignedShort(inputStream);
      if (containerSectionCount <= 0) {
        return invalidLayout(container, version, "Invalid container section count");
      }
      if (containerSectionCount * 4 != typesLength) {
        return invalidLayout(
            container,
            version,
            "Type section length incompatible with container section count - 0x"
                + Integer.toHexString(containerSectionCount)
                + " * 4 != 0x"
                + Integer.toHexString(typesLength));
      }
      if (containerSectionCount > 256) {
        return invalidLayout(
            container,
            version,
            "Too many container sections - 0x" + Integer.toHexString(containerSectionCount));
      }
      containerSectionSizes = new int[containerSectionCount];
      for (int i = 0; i < containerSectionCount; i++) {
        int size = readUnsignedShort(inputStream);
        if (size <= 0) {
          return invalidLayout(
              container, version, "Invalid container section size for section " + i);
        }
        containerSectionSizes[i] = size;
      }
    } else {
      containerSectionCount = 0;
      containerSectionSizes = new int[0];
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
    if (typeData[0][0] != 0 || (typeData[0][1] & 0x7f) != 0) {
      return invalidLayout(
          container, version, "Code section does not have zero inputs and outputs");
    }
    CodeSection[] codeSections = new CodeSection[codeSectionCount];
    int pos = // calculate pos in stream...
        3 // header and version
            + 3 // type header
            + 3
            + (codeSectionCount * 2) // code section size
            + 3 // data section header
            + 1 // padding
            + (codeSectionCount * 4); // type data
    if (containerSectionCount > 0) {
      pos +=
          3 // subcontainer header
              + (containerSectionCount * 2); // subcontainer sizes
    }

    for (int i = 0; i < codeSectionCount; i++) {
      int codeSectionSize = codeSectionSizes[i];
      if (inputStream.skip(codeSectionSize) != codeSectionSize) {
        return invalidLayout(container, version, "Incomplete code section " + i);
      }
      if (typeData[i][0] > 0x7f) {
        return invalidLayout(
            container,
            version,
            "Type data input stack too large - 0x" + Integer.toHexString(typeData[i][0]));
      }
      if (typeData[i][1] > 0x80) {
        return invalidLayout(
            container,
            version,
            "Type data output stack too large - 0x" + Integer.toHexString(typeData[i][1]));
      }
      if (typeData[i][2] > 0x3ff) {
        return invalidLayout(
            container,
            version,
            "Type data max stack too large - 0x" + Integer.toHexString(typeData[i][2]));
      }
      codeSections[i] =
          new CodeSection(codeSectionSize, typeData[i][0], typeData[i][1], typeData[i][2], pos);
      pos += codeSectionSize;
    }

    if (inputStream.skip(dataSize) != dataSize) {
      return invalidLayout(container, version, "Incomplete data section");
    }
    pos += dataSize;

    EOFLayout[] subContainers = new EOFLayout[containerSectionCount];
    for (int i = 0; i < containerSectionCount; i++) {
      int subcontianerSize = containerSectionSizes[i];
      Bytes subcontainer = container.slice(pos, subcontianerSize);
      pos += subcontianerSize;
      if (subcontianerSize != inputStream.skip(subcontianerSize)) {
        return invalidLayout(container, version, "incomplete subcontainer");
      }
      EOFLayout subLayout = EOFLayout.parseEOF(subcontainer);
      if (!subLayout.isValid()) {
        System.out.println(subLayout.getInvalidReason());
        return invalidLayout(container, version, "invalid subcontainer");
      }
      subContainers[i] = subLayout;
    }

    if (inputStream.read() != -1) {
      return invalidLayout(container, version, "Dangling data after end of all sections");
    }

    return new EOFLayout(container, version, codeSections, subContainers);
  }

  /**
   * Read unsigned short int.
   *
   * @param inputStream the input stream
   * @return the int
   */
  static int readUnsignedShort(final ByteArrayInputStream inputStream) {
    if (inputStream.available() < 2) {
      return -1;
    } else {
      return inputStream.read() << 8 | inputStream.read();
    }
  }

  /**
   * Gets container.
   *
   * @return the container
   */
  public Bytes getContainer() {
    return container;
  }

  /**
   * Gets version.
   *
   * @return the version
   */
  public int getVersion() {
    return version;
  }

  /**
   * Get code section count.
   *
   * @return the code section count
   */
  public int getCodeSectionCount() {
    return codeSections == null ? 0 : codeSections.length;
  }

  /**
   * Get code sections.
   *
   * @param i the index
   * @return the Code section
   */
  public CodeSection getCodeSection(final int i) {
    return codeSections[i];
  }

  /**
   * Get sub container section count.
   *
   * @return the sub container count
   */
  public int getSubcontainerCount() {
    return containers == null ? 0 : containers.length;
  }

  /**
   * Get code sections.
   *
   * @param i the index
   * @return the Code section
   */
  public EOFLayout getSubcontainer(final int i) {
    return containers[i];
  }

  /**
   * Gets invalid reason.
   *
   * @return the invalid reason
   */
  public String getInvalidReason() {
    return invalidReason;
  }

  /**
   * Is valid.
   *
   * @return the boolean
   */
  public boolean isValid() {
    return invalidReason == null;
  }
}

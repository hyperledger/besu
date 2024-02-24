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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

import org.apache.tuweni.bytes.Bytes;

/**
 * The EOF layout.
 *
 * @param container The literal EOF bytes fo the whole container
 * @param version The parsed version id. zero if unparseable.
 * @param codeSections The parsed Code sections. Null if invalid.
 * @param containers The parsed subcontainers. Null if invalid.
 * @param dataLength The length of the data as reported by the container. For subcontainers this may
 *     be larger than the data in the data field. Zero if invalid.
 * @param data The data hard coded in the container. Empty if invalid.
 * @param invalidReason If the raw container is invalid, the reason it is invalid. Null if valid.
 */
public record EOFLayout(
    Bytes container,
    int version,
    CodeSection[] codeSections,
    EOFLayout[] containers,
    int dataLength,
    Bytes data,
    String invalidReason) {

  /** The EOF prefix byte as a (signed) java byte. */
  public static final byte EOF_PREFIX_BYTE = (byte) 0xEF;

  /** header terminator */
  static final int SECTION_TERMINATOR = 0x00;

  /** type data (stack heights, inputs/outputs) */
  static final int SECTION_TYPES = 0x01;

  /** code */
  static final int SECTION_CODE = 0x02;

  /** sub-EOF containers for create */
  static final int SECTION_CONTAINER = 0x03;

  /** data */
  static final int SECTION_DATA = 0x04;

  /** The Max supported section. */
  static final int MAX_SUPPORTED_VERSION = 1;

  private EOFLayout(
      final Bytes container,
      final int version,
      final CodeSection[] codeSections,
      final EOFLayout[] containers,
      final int dataSize,
      final Bytes data) {
    this(container, version, codeSections, containers, dataSize, data, null);
  }

  private EOFLayout(final Bytes container, final int version, final String invalidReason) {
    this(container, version, null, null, 0, Bytes.EMPTY, invalidReason);
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
    return parseEOF(container, false);
  }

  /**
   * Parse EOF.
   *
   * @param container the container
   * @param inSubcontainer Is this a subcontainer, i.e. not for deployment.
   * @return the eof layout
   */
  static EOFLayout parseEOF(final Bytes container, final boolean inSubcontainer) {
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
    if (typesLength <= 0 || typesLength % 4 != 0) {
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
      if (i == 0 && typeData[0][1] != 0x80) {
        return invalidLayout(
            container,
            version,
            "Code section at zero expected non-returning flag, but had return stack of "
                + typeData[0][1]);
      }
      pos += codeSectionSize;
    }

    EOFLayout[] subContainers = new EOFLayout[containerSectionCount];
    for (int i = 0; i < containerSectionCount; i++) {
      int subcontianerSize = containerSectionSizes[i];
      Bytes subcontainer = container.slice(pos, subcontianerSize);
      pos += subcontianerSize;
      if (subcontianerSize != inputStream.skip(subcontianerSize)) {
        return invalidLayout(container, version, "incomplete subcontainer");
      }
      EOFLayout subLayout = EOFLayout.parseEOF(subcontainer, true);
      if (!subLayout.isValid()) {
        String invalidSubReason = subLayout.invalidReason;
        return invalidLayout(
            container,
            version,
            invalidSubReason.contains("invalid subcontainer")
                ? invalidSubReason
                : "invalid subcontainer - " + invalidSubReason);
      }
      subContainers[i] = subLayout;
    }

    long loadedDataCount = inputStream.skip(dataSize);
    if (!inSubcontainer && loadedDataCount != dataSize) {
      return invalidLayout(container, version, "Incomplete data section");
    }
    Bytes data = container.slice(pos, (int) loadedDataCount);

    if (inputStream.read() != -1) {
      return invalidLayout(container, version, "Dangling data after end of all sections");
    }

    return new EOFLayout(container, version, codeSections, subContainers, dataSize, data);
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
   * Is valid.
   *
   * @return the boolean
   */
  public boolean isValid() {
    return invalidReason == null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof EOFLayout eofLayout)) return false;
    return version == eofLayout.version
        && container.equals(eofLayout.container)
        && Arrays.equals(codeSections, eofLayout.codeSections)
        && Arrays.equals(containers, eofLayout.containers)
        && Objects.equals(invalidReason, eofLayout.invalidReason);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(container, version, invalidReason);
    result = 31 * result + Arrays.hashCode(codeSections);
    result = 31 * result + Arrays.hashCode(containers);
    return result;
  }

  @Override
  public String toString() {
    return "EOFLayout{"
        + "container="
        + container
        + ", version="
        + version
        + ", codeSections="
        + (codeSections == null ? "null" : Arrays.asList(codeSections).toString())
        + ", containers="
        + (containers == null ? "null" : Arrays.asList(containers).toString())
        + ", invalidReason='"
        + invalidReason
        + '\''
        + '}';
  }

  byte[] newContainerWithAuxData(final Bytes auxData) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(container.size() + auxData.size());
      DataOutputStream dataOutput = new DataOutputStream(baos);
      dataOutput.write(new byte[] {(byte) 0xef, 0x00, 0x01});

      dataOutput.writeByte(SECTION_TYPES);
      dataOutput.write(codeSections.length * 4);

      dataOutput.writeByte(SECTION_CODE);
      dataOutput.write(codeSections.length);
      for (var codeSection : codeSections) {
        dataOutput.writeShort(codeSection.length);
      }

      dataOutput.writeByte(SECTION_DATA);
      dataOutput.writeShort(data.size() + auxData.size());

      if (containers != null && containers.length > 0) {
        dataOutput.writeByte(SECTION_CONTAINER);
        dataOutput.write(containers.length);
        for (var subcontainer : containers) {
          dataOutput.writeShort(subcontainer.getContainer().size());
        }
      }

      dataOutput.writeByte(SECTION_TERMINATOR);

      for (var codeSection : codeSections) {
        dataOutput.writeByte(codeSection.inputs);
        dataOutput.writeByte(codeSection.outputs);
        dataOutput.writeShort(codeSection.maxStackHeight);
      }

      byte[] container = container().toArrayUnsafe();
      for (var codeSection : codeSections) {
        dataOutput.write(container, codeSection.entryPoint, codeSection.length);
      }

      dataOutput.write(data().toArrayUnsafe());
      dataOutput.write(auxData.toArrayUnsafe());

      for (var subcontainer : containers) {
        dataOutput.write(subcontainer.getContainer().toArrayUnsafe());
      }

      return baos.toByteArray();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * Re-writes the container with optional auxiliary data.
   *
   * @param auxData the auxiliary data
   * @return Null if there was an error (validation or otherwise) , or the bytes of the re-written
   *     container.
   */
  @Nullable
  public Bytes writeContainer(@Nullable final Bytes auxData) {
    // do not write invalid containers
    if (invalidReason != null) {
      return null;
    }

    try {
      ByteArrayOutputStream baos =
          new ByteArrayOutputStream(container.size() + dataLength - data.size());
      DataOutputStream out = new DataOutputStream(baos);

      // EOF header
      out.writeByte(EOF_PREFIX_BYTE);
      out.writeByte(0);
      out.writeByte(version);

      // Types header
      out.writeByte(SECTION_TYPES);
      out.writeShort(codeSections.length * 4);

      // Code header
      out.writeByte(SECTION_CODE);
      out.writeShort(codeSections.length);
      for (CodeSection cs : codeSections) {
        out.writeShort(cs.length);
      }

      // Subcontainers header
      if (containers != null && containers.length > 0) {
        out.writeByte(SECTION_CONTAINER);
        for (EOFLayout container : containers) {
          out.write(container.container().size());
        }
      }

      // Data header
      out.writeByte(SECTION_DATA);
      if (auxData == null) {
        out.writeShort(data.size());
      } else {
        int newSize = data.size() + auxData.size();
        if (newSize < dataLength) {
          // aux data must cover claimed data lengths.
          return null;
        }
        out.writeShort(newSize);
      }

      // header end
      out.writeByte(0);

      // Types information
      for (CodeSection cs : codeSections) {
        out.writeByte(cs.inputs);
        if (cs.returning) {
          out.writeByte(cs.outputs);
        } else {
          out.writeByte(0x80);
        }
        out.writeShort(cs.maxStackHeight);
      }

      // Code sections
      for (CodeSection cs : codeSections) {
        out.write(container.slice(cs.entryPoint, cs.length).toArray());
      }

      // Subcontainers
      for (EOFLayout container : containers) {
        out.write(container.container.toArrayUnsafe());
      }

      // data
      out.write(data.toArrayUnsafe());
      if (auxData != null) {
        out.write(auxData.toArrayUnsafe());
      }

      return Bytes.wrap(baos.toByteArray());
    } catch (IOException ioe) {
      // ByteArrayOutputStream should never throw, so somethings gone very wrong.  Wrap as runtime
      // and re-throw.
      throw new RuntimeException(ioe);
    }
  }
}

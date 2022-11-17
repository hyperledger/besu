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
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class EOFLayout {

  static final int SECTION_TERMINATOR = 0x00;
  static final int SECTION_CODE = 0x01;
  static final int SECTION_DATA = 0x02;
  static final int SECTION_TYPES = 0x03;

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

  private EOFLayout(final Bytes container, final String invalidReason) {
    this.container = container;
    this.version = -1;
    this.codeSections = null;
    this.invalidReason = invalidReason;
  }

  private static EOFLayout invalidLayout(final Bytes container, final String invalidReason) {
    return new EOFLayout(container, invalidReason);
  }

  public static EOFLayout parseEOF(final Bytes container) {
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(container.toArrayUnsafe());
    List<Integer> codeSectionSizes = new ArrayList<>();
    int dataSize = 0;
    int typeSize = 0;

    if (inputStream.available() < 3) {
      return invalidLayout(container, "EOF Container too small");
    }
    if (inputStream.read() != 0xEF) {
      return invalidLayout(container, "EOF header byte 0 incorrect");
    }
    if (inputStream.read() != 0x0) {
      return invalidLayout(container, "EOF header byte 1 incorrect");
    }

    final int version = inputStream.read();
    if (version > MAX_SUPPORTED_VERSION || version < 1) {
      return invalidLayout(container, "Unsupported EOF Version " + version);
    }

    // parse section headers
    SECTION_HEADER_LOOP:
    while (true) {
      int kind = inputStream.read();
      switch (kind) {
        case -1:
          return invalidLayout(container, "Improper section headers");
        case SECTION_TERMINATOR:
          break SECTION_HEADER_LOOP;
        case SECTION_CODE:
          if (dataSize > 0) {
            return invalidLayout(container, "Code section cannot follow data section");
          }
          int codeSectionSize = readUnsignedShort(inputStream);
          if (codeSectionSize < 0) {
            return invalidLayout(container, "Improper section headers");
          } else if (codeSectionSize == 0) {
            return invalidLayout(container, "Empty section contents");
          }
          codeSectionSizes.add(codeSectionSize);
          break;
        case SECTION_DATA:
          if (dataSize != 0) {
            return invalidLayout(container, "Duplicate section number 2");
          }
          dataSize = readUnsignedShort(inputStream);
          if (dataSize < 0) {
            return invalidLayout(container, "Improper section headers");
          } else if (dataSize == 0) {
            return invalidLayout(container, "Empty section contents");
          }
          break;
        case SECTION_TYPES:
          if (typeSize != 0) {
            return invalidLayout(container, "Duplicate section number 3");
          }
          if (!codeSectionSizes.isEmpty()) {
            return invalidLayout(container, "Code section cannot precede Type Section");
          }
          typeSize = readUnsignedShort(inputStream);
          if (typeSize == 0) {
            return invalidLayout(container, "Type section cannot be zero length");
          }
          if (typeSize % 2 == 1) {
            return invalidLayout(container, "Type section cannot be odd length");
          }
          break;
        default:
          return invalidLayout(container, "EOF Section kind " + kind + " not supported");
      }
    }

    if (codeSectionSizes.isEmpty()) {
      return invalidLayout(container, "Missing code (kind=1) section");
    }

    // parse input/output table
    int[] inputs;
    int[] outputs;
    int codeSectionCount = codeSectionSizes.size();
    if (typeSize == 0) {
      if (codeSectionCount > 1) {
        return invalidLayout(container, "Multiple code sections but not enough type entries");
      }

      inputs = new int[] {0};
      outputs = new int[] {0};
    } else if (typeSize > 2048) {
      return invalidLayout(container, "Too many code sections");
    } else {
      if (codeSectionCount != typeSize / 2) {
        return new EOFLayout(
            container,
            "Type data length ("
                + typeSize
                + ") does not match code size count ("
                + codeSectionSizes.size()
                + " * 2)");
      }

      inputs = new int[codeSectionCount];
      outputs = new int[codeSectionCount];
      inputs[0] = inputStream.read();
      outputs[0] = inputStream.read();
      if (inputs[0] != 0 || outputs[0] != 0) {
        return invalidLayout(container, "First section input and output must be zero");
      }
      for (int i = 1; i < codeSectionCount; i++) {
        inputs[i] = inputStream.read();
        outputs[i] = inputStream.read();
      }
    }

    // assemble code sections
    CodeSection[] codeSections = new CodeSection[codeSectionCount];
    for (int i = 0; i < codeSectionSizes.size(); i++) {
      int thisSectionSize = codeSectionSizes.get(i);
      byte[] codeBytes = new byte[thisSectionSize];
      if (thisSectionSize != inputStream.read(codeBytes, 0, thisSectionSize)) {
        return invalidLayout(container, "Missing or incomplete section data");
      }
      Bytes code = Bytes.wrap(codeBytes);
      codeSections[i] = new CodeSection(code, inputs[i], outputs[i]);
    }

    // check remaining container validity
    if (dataSize != inputStream.skip(dataSize)) {
      return invalidLayout(container, "Missing or incomplete section data");
    }
    if (inputStream.available() > 0) {
      return invalidLayout(container, "Dangling data at end of container");
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

//// java17 convert to record

final class CodeSection {
  final Bytes code;
  final int inputs;
  final int outputs;

  public CodeSection(final Bytes code, final int inputs, final int outputs) {
    this.code = code;
    this.inputs = inputs;
    this.outputs = outputs;
  }

  public Bytes getCode() {
    return code;
  }

  public int getInputs() {
    return inputs;
  }

  public int getOutputs() {
    return outputs;
  }
}

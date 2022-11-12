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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class EOFLayout {

  static final int SECTION_CODE = 0x01;
  static final int SECTION_DATA = 0x02;

  static final int MAX_SUPPORTED_SECTION = SECTION_DATA;
  static final int MAX_SUPPORTED_VERSION = 1;

  private final Bytes container;
  private final int version;
  private final Bytes[] sections;
  private final String invalidReason;

  private EOFLayout(final Bytes container, final int version, final Bytes[] sections) {
    this.container = container;
    this.version = version;
    this.sections = sections;
    this.invalidReason = null;
  }

  private EOFLayout(final Bytes container, final String invalidReason) {
    this.container = container;
    this.version = -1;
    this.sections = null;
    this.invalidReason = invalidReason;
  }

  public static EOFLayout parseEOF(final Bytes container) {
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(container.toArrayUnsafe());
    if (inputStream.available() < 3) {
      return new EOFLayout(container, "EOF Container too small");
    }
    if (inputStream.read() != 0xEF) {
      return new EOFLayout(container, "EOF header byte 0 incorrect");
    }
    if (inputStream.read() != 0x0) {
      return new EOFLayout(container, "EOF header byte 1 incorrect");
    }

    final int version = inputStream.read();
    if (version > MAX_SUPPORTED_VERSION || version < 1) {
      return new EOFLayout(container, "Unsupported EOF Version " + version);
    }
    final List<EOFSectionInfo> sectionInfos = new ArrayList<>(3);
    EOFSectionInfo sectionInfo = EOFSectionInfo.getSectionInfo(inputStream);
    while (sectionInfo != null && sectionInfo.kind() != 0) {
      sectionInfos.add(sectionInfo);
      sectionInfo = EOFSectionInfo.getSectionInfo(inputStream);
    }
    if (sectionInfo == null) {
      return new EOFLayout(container, "Improper section headers");
    }

    int remaining = inputStream.available();
    int pos = container.size() - remaining;

    final Bytes[] sections = new Bytes[MAX_SUPPORTED_SECTION + 1];
    for (final var info : sectionInfos) {
      final int kind = info.kind();
      final int size = info.size();
      if (kind > MAX_SUPPORTED_SECTION) {
        return new EOFLayout(container, "EOF Section kind " + kind + " not supported");
      }
      if (sections[kind] != null) {
        return new EOFLayout(container, "Duplicate section number " + kind);
      }
      if (size == 0) {
        return new EOFLayout(container, "Empty section contents");
      }
      if (size > remaining) {
        return new EOFLayout(container, "Missing or incomplete section data");
      }
      if (kind == 1 && sections[2] != null) {
        return new EOFLayout(container, "Code section cannot follow data section");
      }

      sections[kind] = container.slice(pos, size);
      pos += size;
      remaining -= size;
    }
    if (pos < container.size()) {
      return new EOFLayout(container, "Dangling data at end of container");
    }
    if (sections[1] == null) {
      return new EOFLayout(container, "Missing code (kind=1) section");
    } else if (sections[1].size() < 1) {
      return new EOFLayout(container, "Code section empty");
    }
    return new EOFLayout(container, version, sections);
  }

  public Bytes getContainer() {
    return container;
  }

  public int getVersion() {
    return version;
  }

  public Bytes[] getSections() {
    return sections;
  }

  public String getInvalidReason() {
    return invalidReason;
  }

  public boolean isValid() {
    return invalidReason == null;
  }
}

// TODO should be a record
final class EOFSectionInfo {
  private final int kind;
  private final int size;

  private EOFSectionInfo(final int kind, final int size) {
    this.kind = kind;
    this.size = size;
  }

  static EOFSectionInfo getSectionInfo(final InputStream in) {
    try {
      final int kind = in.read();
      if (kind < 0) {
        return null;
      } else if (kind == 0) {
        return new EOFSectionInfo(kind, 0);
      } else {
        final int msb = in.read() << 8;
        final int lsb = in.read();
        return new EOFSectionInfo(kind, msb + lsb);
      }
    } catch (final IOException ioe) {
      return null;
    }
  }

  public int kind() {
    return kind;
  }

  public int size() {
    return size;
  }
}

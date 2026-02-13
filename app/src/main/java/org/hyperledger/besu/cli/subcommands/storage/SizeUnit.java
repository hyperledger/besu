/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.subcommands.storage;

/** Size unit for formatting byte sizes. */
public enum SizeUnit {
  /** Bytes. */
  B(1L, "B"),
  /** Kibibytes (1024 bytes). */
  KIB(1024L, "KiB"),
  /** Mebibytes (1024 KiB). */
  MIB(1024L * 1024L, "MiB"),
  /** Gibibytes (1024 MiB). */
  GIB(1024L * 1024L * 1024L, "GiB");

  private final long divisor;
  private final String suffix;

  SizeUnit(final long divisor, final String suffix) {
    this.divisor = divisor;
    this.suffix = suffix;
  }

  /**
   * Gets the divisor for this unit.
   *
   * @return the divisor
   */
  public long getDivisor() {
    return divisor;
  }

  /**
   * Gets the display suffix for this unit.
   *
   * @return the suffix
   */
  public String getSuffix() {
    return suffix;
  }
}

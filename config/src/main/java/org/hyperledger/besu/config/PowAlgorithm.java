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
package org.hyperledger.besu.config;

/** An enumeration of supported Proof-of-work algorithms. */
public enum PowAlgorithm {
  /** Unsupported pow algorithm. */
  UNSUPPORTED,
  /** Ethash pow algorithm. */
  ETHASH;

  /**
   * From string pow algorithm.
   *
   * @param str pow algorithm as string
   * @return the pow algorithm interface. null if not valid enum.
   */
  public static PowAlgorithm fromString(final String str) {
    for (final PowAlgorithm powAlgorithm : PowAlgorithm.values()) {
      if (powAlgorithm.name().equalsIgnoreCase(str)) {
        return powAlgorithm;
      }
    }
    return null;
  }
}

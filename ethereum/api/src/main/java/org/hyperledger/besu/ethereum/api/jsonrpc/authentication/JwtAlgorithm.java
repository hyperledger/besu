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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

/** The enum Jwt algorithm. */
public enum JwtAlgorithm {
  /** Rs 256 jwt algorithm. */
  RS256,
  /** Rs 384 jwt algorithm. */
  RS384,
  /** Rs 512 jwt algorithm. */
  RS512,
  /** Es 256 jwt algorithm. */
  ES256,
  /** Es 384 jwt algorithm. */
  ES384,
  /** Hs 256 jwt algorithm. */
  HS256,
  /** Es 512 jwt algorithm. */
  ES512;

  /**
   * From string jwt algorithm.
   *
   * @param str the str
   * @return the jwt algorithm
   */
  public static JwtAlgorithm fromString(final String str) {
    for (final JwtAlgorithm alg : JwtAlgorithm.values()) {
      if (alg.name().equalsIgnoreCase(str)) {
        return alg;
      }
    }
    return null;
  }
}

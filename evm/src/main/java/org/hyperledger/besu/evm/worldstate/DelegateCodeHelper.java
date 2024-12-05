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
package org.hyperledger.besu.evm.worldstate;

import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.bytes.Bytes;

public class DelegateCodeHelper {
  public static final Bytes DELEGATED_CODE_DESIGNATOR = Bytes.fromHexString("ef01");
  public static final Bytes DELEGATED_CODE_PREFIX = Bytes.fromHexString("ef0100");
  public static final int DELEGATED_CODE_SIZE = DELEGATED_CODE_PREFIX.size() + Address.SIZE;

  /**
   * Returns if the provided code is delegated code.
   *
   * @param code the code to check.
   * @return {@code true} if the code is delegated code, {@code false} otherwise.
   */
  public static boolean hasDelegatedCode(final Bytes code) {
    return code != null
        && code.size() == DELEGATED_CODE_SIZE
        && code.slice(0, DELEGATED_CODE_PREFIX.size()).equals(DELEGATED_CODE_PREFIX);
  }

  public static Bytes getDelegatedCodeForRead() {
    return DELEGATED_CODE_DESIGNATOR;
  }
}

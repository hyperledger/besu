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

/** Helper class for 7702 delegated code interactions */
public class CodeDelegationHelper {
  /**
   * The designator that is returned when a ExtCode* operation calls a contract with delegated code
   */
  public static final Bytes DELEGATED_CODE_DESIGNATOR = Bytes.fromHexString("ef01");

  /** The prefix that is used to identify delegated code */
  public static final Bytes CODE_DELEGATION_PREFIX = Bytes.fromHexString("ef0100");

  /** The size of the delegated code */
  public static final int DELEGATED_CODE_SIZE = CODE_DELEGATION_PREFIX.size() + Address.SIZE;

  /** create a new DelegateCodeHelper */
  public CodeDelegationHelper() {
    // empty
  }

  /**
   * Returns if the provided code is delegated code.
   *
   * @param code the code to check.
   * @return {@code true} if the code is delegated code, {@code false} otherwise.
   */
  public static boolean hasCodeDelegation(final Bytes code) {
    return code != null
        && code.size() == DELEGATED_CODE_SIZE
        && code.slice(0, CODE_DELEGATION_PREFIX.size()).equals(CODE_DELEGATION_PREFIX);
  }

  /**
   * Returns the delegated code designator
   *
   * @return the hardcoded designator for delegated code: ef01
   */
  public static Bytes getCodeDelegationForRead() {
    return DELEGATED_CODE_DESIGNATOR;
  }
}

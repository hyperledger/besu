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
package org.hyperledger.besu.evm.code;

import javax.annotation.Nullable;

/** Code Validation */
public interface EOFValidator {

  /**
   * Validates the code and stack for the EOF Layout, with optional deep consideration of the
   * containers.
   *
   * @param layout The parsed EOFLayout of the code
   * @return either null, indicating no error, or a String describing the validation error.
   */
  @Nullable
  String validate(final EOFLayout layout);

  /**
   * Performs code validation of the EOF layout.
   *
   * @param eofLayout The EOF Layout
   * @return validation code, null otherwise.
   */
  @Nullable
  String validateCode(final EOFLayout eofLayout);

  /**
   * Performs stack validation of the EOF layout. Presumes that code validation has been perfromed
   *
   * @param eofLayout The EOF Layout
   * @return validation code, null otherwise.
   */
  @Nullable
  String validateStack(final EOFLayout eofLayout);
}

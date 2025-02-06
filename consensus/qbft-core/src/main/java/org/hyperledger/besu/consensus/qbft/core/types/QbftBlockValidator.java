/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.types;

import java.util.Optional;

/** Validates a block. */
public interface QbftBlockValidator {

  /**
   * Validates a block.
   *
   * @param block the block to validate
   * @return the validation result
   */
  ValidationResult validateBlock(QbftBlock block);

  /**
   * The result of a block validation.
   *
   * @param success whether the validation was successful
   * @param errorMessage the error message if the validation was not successful
   */
  record ValidationResult(boolean success, Optional<String> errorMessage) {}
}

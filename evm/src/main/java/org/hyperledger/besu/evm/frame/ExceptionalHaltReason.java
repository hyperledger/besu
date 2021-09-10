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
 */
package org.hyperledger.besu.evm.frame;

public enum ExceptionalHaltReason {
  NONE(""),
  INSUFFICIENT_GAS("Out of gas"),
  INSUFFICIENT_STACK_ITEMS("Stack underflow"),
  INVALID_JUMP_DESTINATION("Bad jump destination"),
  INVALID_OPERATION("Bad instruction"),
  INVALID_RETURN_DATA_BUFFER_ACCESS("Out of bounds"),
  TOO_MANY_STACK_ITEMS("Out of stack"),
  ILLEGAL_STATE_CHANGE("Illegal state change"),
  OUT_OF_BOUNDS("Out of bounds");

  String description;

  ExceptionalHaltReason(final String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}

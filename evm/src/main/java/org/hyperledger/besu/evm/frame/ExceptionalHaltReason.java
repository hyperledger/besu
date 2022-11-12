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
package org.hyperledger.besu.evm.frame;

public interface ExceptionalHaltReason {

  ExceptionalHaltReason NONE = DefaultExceptionalHaltReason.NONE;
  ExceptionalHaltReason INSUFFICIENT_GAS = DefaultExceptionalHaltReason.INSUFFICIENT_GAS;
  ExceptionalHaltReason INSUFFICIENT_STACK_ITEMS =
      DefaultExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
  ExceptionalHaltReason INVALID_JUMP_DESTINATION =
      DefaultExceptionalHaltReason.INVALID_JUMP_DESTINATION;
  ExceptionalHaltReason INVALID_OPERATION = DefaultExceptionalHaltReason.INVALID_OPERATION;
  ExceptionalHaltReason INVALID_RETURN_DATA_BUFFER_ACCESS =
      DefaultExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS;
  ExceptionalHaltReason TOO_MANY_STACK_ITEMS = DefaultExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
  ExceptionalHaltReason ILLEGAL_STATE_CHANGE = DefaultExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
  ExceptionalHaltReason OUT_OF_BOUNDS = DefaultExceptionalHaltReason.OUT_OF_BOUNDS;
  ExceptionalHaltReason CODE_TOO_LARGE = DefaultExceptionalHaltReason.CODE_TOO_LARGE;
  ExceptionalHaltReason INVALID_CODE = DefaultExceptionalHaltReason.INVALID_CODE;
  ExceptionalHaltReason PRECOMPILE_ERROR = DefaultExceptionalHaltReason.PRECOMPILE_ERROR;

  String name();

  String getDescription();

  enum DefaultExceptionalHaltReason implements ExceptionalHaltReason {
    NONE(""),
    INSUFFICIENT_GAS("Out of gas"),
    INSUFFICIENT_STACK_ITEMS("Stack underflow"),
    INVALID_JUMP_DESTINATION("Bad jump destination"),
    INVALID_OPERATION("Bad instruction"),
    INVALID_RETURN_DATA_BUFFER_ACCESS("Out of bounds"),
    TOO_MANY_STACK_ITEMS("Out of stack"),
    ILLEGAL_STATE_CHANGE("Illegal state change"),
    OUT_OF_BOUNDS("Out of bounds"),
    CODE_TOO_LARGE("Code is too large"),
    INVALID_CODE("Code is invalid"),
    PRECOMPILE_ERROR("Precompile error");

    final String description;

    DefaultExceptionalHaltReason(final String description) {
      this.description = description;
    }

    @Override
    public String getDescription() {
      return description;
    }
  }
}

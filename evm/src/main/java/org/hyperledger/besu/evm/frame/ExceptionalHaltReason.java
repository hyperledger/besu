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

/** The interface Exceptional halt reason. */
public interface ExceptionalHaltReason {

  /** The constant NONE. */
  ExceptionalHaltReason NONE = DefaultExceptionalHaltReason.NONE;
  /** The constant INSUFFICIENT_GAS. */
  ExceptionalHaltReason INSUFFICIENT_GAS = DefaultExceptionalHaltReason.INSUFFICIENT_GAS;
  /** The constant INSUFFICIENT_STACK_ITEMS. */
  ExceptionalHaltReason INSUFFICIENT_STACK_ITEMS =
      DefaultExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
  /** The constant INVALID_JUMP_DESTINATION. */
  ExceptionalHaltReason INVALID_JUMP_DESTINATION =
      DefaultExceptionalHaltReason.INVALID_JUMP_DESTINATION;
  /** The constant INVALID_OPERATION. */
  ExceptionalHaltReason INVALID_OPERATION = DefaultExceptionalHaltReason.INVALID_OPERATION;
  /** The constant INVALID_RETURN_DATA_BUFFER_ACCESS. */
  ExceptionalHaltReason INVALID_RETURN_DATA_BUFFER_ACCESS =
      DefaultExceptionalHaltReason.INVALID_RETURN_DATA_BUFFER_ACCESS;
  /** The constant TOO_MANY_STACK_ITEMS. */
  ExceptionalHaltReason TOO_MANY_STACK_ITEMS = DefaultExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
  /** The constant ILLEGAL_STATE_CHANGE. */
  ExceptionalHaltReason ILLEGAL_STATE_CHANGE = DefaultExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
  /** The constant OUT_OF_BOUNDS. */
  ExceptionalHaltReason OUT_OF_BOUNDS = DefaultExceptionalHaltReason.OUT_OF_BOUNDS;
  /** The constant CODE_TOO_LARGE. */
  ExceptionalHaltReason CODE_TOO_LARGE = DefaultExceptionalHaltReason.CODE_TOO_LARGE;
  /** The constant INVALID_CODE. */
  ExceptionalHaltReason INVALID_CODE = DefaultExceptionalHaltReason.INVALID_CODE;
  /** The constant PRECOMPILE_ERROR. */
  ExceptionalHaltReason PRECOMPILE_ERROR = DefaultExceptionalHaltReason.PRECOMPILE_ERROR;

  /** The constant CODE_SECTION_MISSING. */
  ExceptionalHaltReason CODE_SECTION_MISSING = DefaultExceptionalHaltReason.CODE_SECTION_MISSING;
  /** The constant INCORRECT_CODE_SECTION_RETURN_OUTPUTS. */
  ExceptionalHaltReason INCORRECT_CODE_SECTION_RETURN_OUTPUTS =
      DefaultExceptionalHaltReason.INCORRECT_CODE_SECTION_RETURN_OUTPUTS;
  /** The constant TOO_FEW_INPUTS_FOR_CODE_SECTION. */
  ExceptionalHaltReason TOO_FEW_INPUTS_FOR_CODE_SECTION =
      DefaultExceptionalHaltReason.TOO_FEW_INPUTS_FOR_CODE_SECTION;
  /** The constant JUMPF_STACK_MISMATCH. */
  ExceptionalHaltReason JUMPF_STACK_MISMATCH = DefaultExceptionalHaltReason.JUMPF_STACK_MISMATCH;
  /** The constant EOF_CREATE_VERSION_INCOMPATIBLE. */
  ExceptionalHaltReason EOF_CREATE_VERSION_INCOMPATIBLE =
      DefaultExceptionalHaltReason.EOF_CREATE_VERSION_INCOMPATIBLE;

  /**
   * Name string.
   *
   * @return the string
   */
  String name();

  /**
   * Gets description.
   *
   * @return the description
   */
  String getDescription();

  /** The enum Default exceptional halt reason. */
  enum DefaultExceptionalHaltReason implements ExceptionalHaltReason {
    /** None default exceptional halt reason. */
    NONE(""),
    /** The Insufficient gas. */
    INSUFFICIENT_GAS("Out of gas"),
    /** The Insufficient stack items. */
    INSUFFICIENT_STACK_ITEMS("Stack underflow"),
    /** The Invalid jump destination. */
    INVALID_JUMP_DESTINATION("Bad jump destination"),
    /** The Invalid operation. */
    INVALID_OPERATION("Bad instruction"),
    /** The Invalid return data buffer access. */
    INVALID_RETURN_DATA_BUFFER_ACCESS("Out of bounds"),
    /** The Too many stack items. */
    TOO_MANY_STACK_ITEMS("Out of stack"),
    /** The Illegal state change. */
    ILLEGAL_STATE_CHANGE("Illegal state change"),
    /** The Out of bounds. */
    OUT_OF_BOUNDS("Out of bounds"),
    /** The Code too large. */
    CODE_TOO_LARGE("Code is too large"),
    /** The Invalid code. */
    INVALID_CODE("Code is invalid"),
    /** The Precompile error. */
    PRECOMPILE_ERROR("Precompile error"),
    /** The Code section missing. */
    CODE_SECTION_MISSING("No code section at requested index"),
    /** The Insufficient code section return data. */
    INSUFFICIENT_CODE_SECTION_RETURN_DATA("The stack for a return "),
    /** The Incorrect code section return outputs. */
    INCORRECT_CODE_SECTION_RETURN_OUTPUTS(
        "The return of a code section does not have the correct number of outputs"),
    /** The Too few inputs for code section. */
    TOO_FEW_INPUTS_FOR_CODE_SECTION("Not enough stack items for a function call"),
    /** The Jumpf stack mismatch. */
    JUMPF_STACK_MISMATCH(
        "The stack height for a JUMPF does not match the requirements of the target section"),
    /** The Eof version incompatible. */
    EOF_CREATE_VERSION_INCOMPATIBLE(
        "EOF Code is attempting to create EOF code of an earlier version");

    /** The Description. */
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

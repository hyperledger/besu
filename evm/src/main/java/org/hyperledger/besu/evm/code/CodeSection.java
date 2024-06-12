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

import java.util.Objects;

//// java17 convert to record

/** The code section */
public final class CodeSection {

  /** The length. */
  final int length;

  /** The Inputs. */
  final int inputs;

  /** The Outputs. */
  final int outputs;

  /** The Max stack height. */
  final int maxStackHeight;

  /** The byte offset from the beginning of the container that the section starts at */
  final int entryPoint;

  /** Is this a returing code section (i.e. contains RETF or JUMPF into a returning section)? */
  final boolean returning;

  /**
   * Instantiates a new Code section.
   *
   * @param length the length
   * @param inputs the inputs
   * @param outputs the outputs
   * @param maxStackHeight the max stack height
   * @param entryPoint the entry point
   */
  public CodeSection(
      final int length,
      final int inputs,
      final int outputs,
      final int maxStackHeight,
      final int entryPoint) {
    this.length = length;
    this.inputs = inputs;
    if (outputs == 0x80) {
      this.outputs = 0;
      returning = false;
    } else {
      this.outputs = outputs;
      returning = true;
    }
    this.maxStackHeight = maxStackHeight;
    this.entryPoint = entryPoint;
  }

  /**
   * Gets Length.
   *
   * @return the length
   */
  public int getLength() {
    return length;
  }

  /**
   * Gets inputs.
   *
   * @return the inputs
   */
  public int getInputs() {
    return inputs;
  }

  /**
   * Gets outputs.
   *
   * @return the outputs
   */
  public int getOutputs() {
    return outputs;
  }

  /**
   * Does this code seciton have a RETF return anywhere?
   *
   * @return returning
   */
  public boolean isReturning() {
    return returning;
  }

  /**
   * Gets max stack height.
   *
   * @return the max stack height
   */
  public int getMaxStackHeight() {
    return maxStackHeight;
  }

  /**
   * Get Entry Point
   *
   * @return the entry point.
   */
  public int getEntryPoint() {
    return entryPoint;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CodeSection that = (CodeSection) o;
    return length == that.length
        && inputs == that.inputs
        && outputs == that.outputs
        && maxStackHeight == that.maxStackHeight;
  }

  @Override
  public int hashCode() {
    return Objects.hash(length, inputs, outputs, maxStackHeight);
  }
}

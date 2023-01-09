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
 *
 */

package org.hyperledger.besu.evm.code;

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

/** The Code section. */
//// java17 convert to record
public final class CodeSection {
  /** The Code. */
  final Bytes code;
  /** The Inputs. */
  final int inputs;
  /** The Outputs. */
  final int outputs;
  /** The Max stack height. */
  final int maxStackHeight;

  /**
   * Instantiates a new Code section.
   *
   * @param code the code
   * @param inputs the inputs
   * @param outputs the outputs
   * @param maxStackHeight the max stack height
   */
  public CodeSection(
      final Bytes code, final int inputs, final int outputs, final int maxStackHeight) {
    this.code = code;
    this.inputs = inputs;
    this.outputs = outputs;
    this.maxStackHeight = maxStackHeight;
  }

  /**
   * Gets code.
   *
   * @return the code
   */
  public Bytes getCode() {
    return code;
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
   * Gets max stack height.
   *
   * @return the max stack height
   */
  public int getMaxStackHeight() {
    return maxStackHeight;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CodeSection that = (CodeSection) o;
    return inputs == that.inputs
        && outputs == that.outputs
        && maxStackHeight == that.maxStackHeight
        && code.equals(that.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, inputs, outputs, maxStackHeight);
  }
}

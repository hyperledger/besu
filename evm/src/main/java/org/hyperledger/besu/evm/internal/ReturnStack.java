/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.evm.internal;

import java.util.Objects;

/** The type Return stack. */
public class ReturnStack extends FlexStack<ReturnStack.ReturnStackItem> {

  /** The type Return stack item. */
  // Java17 convert to record
  public static final class ReturnStackItem {

    /** The Code section index. */
    final int codeSectionIndex;
    /** The Pc. */
    final int pc;
    /** The Stack height. */
    final int stackHeight;

    /**
     * Instantiates a new Return stack item.
     *
     * @param codeSectionIndex the code section index
     * @param pc the pc
     * @param stackHeight the stack height
     */
    public ReturnStackItem(final int codeSectionIndex, final int pc, final int stackHeight) {
      this.codeSectionIndex = codeSectionIndex;
      this.pc = pc;
      this.stackHeight = stackHeight;
    }

    /**
     * Gets code section index.
     *
     * @return the code section index
     */
    public int getCodeSectionIndex() {
      return codeSectionIndex;
    }

    /**
     * Gets pc.
     *
     * @return the pc
     */
    public int getPC() {
      return pc;
    }

    /**
     * Gets stack height.
     *
     * @return the stack height
     */
    public int getStackHeight() {
      return stackHeight;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ReturnStackItem that = (ReturnStackItem) o;
      return codeSectionIndex == that.codeSectionIndex
          && pc == that.pc
          && stackHeight == that.stackHeight;
    }

    @Override
    public int hashCode() {
      return Objects.hash(codeSectionIndex, pc, stackHeight);
    }

    @Override
    public String toString() {
      return "ReturnStackItem{"
          + "codeSectionIndex="
          + codeSectionIndex
          + ", pc="
          + pc
          + ", stackHeight="
          + stackHeight
          + '}';
    }
  }

  /**
   * Max return stack size specified in <a
   * href="https://eips.ethereum.org/EIPS/eip-4750">EIP-4750</a>
   */
  static final int MAX_RETURN_STACK = 1024;

  /** Instantiates a new Return stack. */
  public ReturnStack() {
    super(MAX_RETURN_STACK, ReturnStackItem.class);
  }
}

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

public class ReturnStack extends FixedStack<ReturnStack.ReturnStackItem> {

  // Java17 convert to record
  public static final class ReturnStackItem {

    final int codeSectionIndex;
    final int pc;
    final int stackHeight;

    public ReturnStackItem(final int codeSectionIndex, final int pc, final int stackHeight) {
      this.codeSectionIndex = codeSectionIndex;
      this.pc = pc;
      this.stackHeight = stackHeight;
    }

    public int getCodeSectionIndex() {
      return codeSectionIndex;
    }

    public int getPC() {
      return pc;
    }

    public int getStackHeight() {
      return stackHeight;
    }
  }

  /**
   * Max return stack size specified in <a
   * href="https://eips.ethereum.org/EIPS/eip-4750">EIP-4750</a>
   */
  static final int MAX_RETURN_STACK = 1024;

  public ReturnStack() {
    super(MAX_RETURN_STACK, ReturnStackItem.class);
  }
}

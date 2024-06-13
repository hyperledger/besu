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
package org.hyperledger.besu.evm.internal;

/** The type Return stack. */
public class ReturnStack extends FlexStack<ReturnStack.ReturnStackItem> {

  /**
   * The type Return stack item.
   *
   * @param codeSectionIndex the code section index
   * @param pc the pc
   */
  public record ReturnStackItem(int codeSectionIndex, int pc) {}

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

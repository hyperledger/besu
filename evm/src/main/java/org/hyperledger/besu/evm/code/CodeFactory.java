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

import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;

/** The Code factory. */
public class CodeFactory {

  /** Maximum size of the code stream that can be produced. */
  protected final int maxContainerSize;

  /**
   * Create a code factory.
   *
   * @param maxContainerSize Maximum size of a container that will be parsed.
   */
  public CodeFactory(final int maxContainerSize) {
    this.maxContainerSize = maxContainerSize;
  }

  /**
   * Create Code.
   *
   * @param bytes the bytes
   * @return the code
   */
  public Code createCode(final Bytes bytes) {
    return new Code(bytes);
  }

  /**
   * Create Code for a create transaction.
   *
   * @param bytes the bytes
   * @param createTransaction This is in a create transaction
   * @return the code
   */
  public Code createCode(final Bytes bytes, final boolean createTransaction) {
    return new Code(bytes);
  }
}

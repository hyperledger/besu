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

/**
 * Overflow exception for {@link FixedStack} and {@link FlexStack}. The main need for a separate
 * class is to remove the stack trace generation as the exception is not used to signal a debuggable
 * failure but instead an expected edge case the EVM should handle.
 */
public class OverflowException extends RuntimeException {

  /** Default constructor. */
  public OverflowException() {}

  /**
   * Overload the stack trace fill in so no stack is filled in. This is done for performance reasons
   * as this exception signals an expected corner case not a debuggable failure.
   *
   * @return the exception, with no stack trace filled in.
   */
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}

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
package org.hyperledger.besu.services.pipeline.exception;

/** This class allows throwing an exception in case of failure of an async task in the pipeline */
public class AsyncOperationException extends RuntimeException {

  /**
   * Constructor of the exception that takes the message and the cause of it.
   *
   * @param message of the exception
   * @param cause of the exception
   */
  public AsyncOperationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

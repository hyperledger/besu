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
package org.hyperledger.besu.plugin.services.securitymodule;

/** SecurityModuleException can be thrown by operations of SecurityModule */
public class SecurityModuleException extends RuntimeException {
  public SecurityModuleException() {}

  public SecurityModuleException(final String message) {
    super(message);
  }

  public SecurityModuleException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public SecurityModuleException(final Throwable cause) {
    super(cause);
  }

  public SecurityModuleException(
      final String message,
      final Throwable cause,
      final boolean enableSuppression,
      final boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}

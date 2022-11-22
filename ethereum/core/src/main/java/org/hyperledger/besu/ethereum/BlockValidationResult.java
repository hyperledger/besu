/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
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

package org.hyperledger.besu.ethereum;

import java.util.Optional;

public class BlockValidationResult {

  public final Optional<String> errorMessage;
  public final Optional<Throwable> cause;
  public final boolean success;

  public BlockValidationResult() {
    this.success = true;
    this.errorMessage = Optional.empty();
    this.cause = Optional.empty();
  }

  public BlockValidationResult(final String errorMessage) {
    this.success = false;
    this.errorMessage = Optional.of(errorMessage);
    this.cause = Optional.empty();
  }

  public BlockValidationResult(final String errorMessage, final Throwable cause) {
    this.success = false;
    this.errorMessage = Optional.of(errorMessage);
    this.cause = Optional.of(cause);
  }

  public boolean isSuccessful() {
    return this.success;
  }

  public boolean isFailed() {
    return !isSuccessful();
  }

  public Optional<Throwable> causedBy() {
    return cause;
  }
}

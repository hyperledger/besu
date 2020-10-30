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
package org.hyperledger.besu.ethereum.mainnet;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.MoreObjects;

public final class ValidationResult<T> {

  private final Optional<T> invalidReason;
  private final Optional<String> errorMessage;

  private ValidationResult(final Optional<T> invalidReason, final Optional<String> errorMessage) {
    this.invalidReason = invalidReason;
    this.errorMessage = errorMessage;
  }

  public boolean isValid() {
    return invalidReason.isEmpty();
  }

  public T getInvalidReason() throws NoSuchElementException {
    return invalidReason.get();
  }

  public String getErrorMessage() {
    return errorMessage.orElse(getInvalidReason().toString());
  }

  public void ifValid(final Runnable action) {
    if (isValid()) {
      action.run();
    }
  }

  public <R> R either(final Supplier<R> whenValid, final Function<T, R> whenInvalid) {
    return invalidReason.map(whenInvalid).orElseGet(whenValid);
  }

  public static <T> ValidationResult<T> valid() {
    return new ValidationResult<>(Optional.empty(), Optional.empty());
  }

  public static <T> ValidationResult<T> invalid(final T reason, final String errorMessage) {
    return new ValidationResult<>(Optional.of(reason), Optional.of(errorMessage));
  }

  public static <T> ValidationResult<T> invalid(final T reason) {
    return new ValidationResult<>(Optional.of(reason), Optional.empty());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidationResult<?> that = (ValidationResult<?>) o;
    return Objects.equals(invalidReason, that.invalidReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invalidReason);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("invalidReason", invalidReason)
        .add("errorMessage", errorMessage)
        .toString();
  }
}

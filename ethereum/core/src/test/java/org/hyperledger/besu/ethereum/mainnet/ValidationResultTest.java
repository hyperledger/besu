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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;

public class ValidationResultTest {

  private final Runnable action = mock(Runnable.class);

  @Test
  public void validResulthouldBeValid() {
    assertThat(ValidationResult.valid().isValid()).isTrue();
  }

  @Test
  public void invalidResultsShouldBeInvalid() {
    assertThat(ValidationResult.invalid("foo").isValid()).isFalse();
  }

  @Test
  public void shouldRunIfValidActionWhenValid() {
    ValidationResult.valid().ifValid(action);

    verify(action).run();
  }

  @Test
  public void shouldNotRunIfValidActionWhenInvalid() {
    ValidationResult.invalid("foo").ifValid(action);

    verifyNoInteractions(action);
  }

  @Test
  public void eitherShouldReturnWhenValidSupplierWhenValid() {
    assertThat(
            ValidationResult.valid()
                .either(
                    () -> Boolean.TRUE,
                    error -> {
                      throw new IllegalStateException(
                          "Should not have executed whenInvalid function");
                    }))
        .isTrue();
  }

  @Test
  public void eitherShouldUseWhenInvalidFunctionWhenInvalid() {
    final ValidationResult<String> result = ValidationResult.invalid("foo");
    assertThat(
            result.<Boolean>either(
                () -> {
                  throw new IllegalStateException("Should not have executed whenInvalid function");
                },
                error -> Boolean.TRUE))
        .isTrue();
  }
}

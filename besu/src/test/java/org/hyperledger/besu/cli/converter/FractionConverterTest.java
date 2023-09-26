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
package org.hyperledger.besu.cli.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.hyperledger.besu.cli.converter.exception.FractionConversionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FractionConverterTest {

  private final FractionConverter fractionConverter = new FractionConverter();

  @Test
  public void assertThatConvertHandlesProperlyAValidString() throws FractionConversionException {
    final float fraction = fractionConverter.convert("0.58");
    assertThat(fraction).isNotNull();
    assertThat(fraction).isEqualTo(0.58f);
  }

  @Test
  public void assertThatConvertHandlesProperlyAnInvalidStringNotANumber() {
    final Throwable thrown = catchThrowable(() -> fractionConverter.convert("invalid"));
    assertThat(thrown).isInstanceOf(FractionConversionException.class);
  }

  @Test
  public void assertThatConvertHandlesProperlyAnInvalidStringOutOfRange() {
    final Throwable thrown = catchThrowable(() -> fractionConverter.convert("1.2"));
    assertThat(thrown).isInstanceOf(FractionConversionException.class);
  }
}

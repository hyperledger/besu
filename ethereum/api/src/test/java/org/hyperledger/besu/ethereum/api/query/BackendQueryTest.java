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
package org.hyperledger.besu.ethereum.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BackendQueryTest<T> {

  private final Supplier<Boolean> alive;
  private final Object wantReturn;
  private final boolean wantException;
  private final Class<T> wantExceptionClass;
  private final String wantExceptionMessage;

  public BackendQueryTest(
      final Supplier<Boolean> alive,
      final Object wantReturn,
      final boolean wantException,
      final Class<T> wantExceptionClass,
      final String wantExceptionMessage) {
    this.alive = alive;
    this.wantReturn = wantReturn;
    this.wantException = wantException;
    this.wantExceptionClass = wantExceptionClass;
    this.wantExceptionMessage = wantExceptionMessage;
  }

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {supplierOf(false), null, true, RuntimeException.class, "Timeout expired"},
          {supplierOf(true), "expected return", false, null, null}
        });
  }

  private static Supplier<Boolean> supplierOf(final boolean val) {
    return () -> val;
  }

  @Test
  public void test() throws Exception {
    if (wantException) {
      assertThatThrownBy(() -> BackendQuery.runIfAlive(() -> wantReturn, alive))
          .isInstanceOf(wantExceptionClass)
          .hasMessage(wantExceptionMessage);
    } else {
      assertThat(BackendQuery.runIfAlive(() -> wantReturn, alive)).isEqualTo(wantReturn);
    }
  }
}

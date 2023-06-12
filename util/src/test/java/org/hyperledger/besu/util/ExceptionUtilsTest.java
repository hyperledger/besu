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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {

  @Test
  public void rootCauseReturnsTopLevelCause() {
    final Exception rootCause = new IllegalStateException("test");
    assertThat(ExceptionUtils.rootCause(rootCause)).isEqualTo(rootCause);
  }

  @Test
  public void rootCauseReturnsNestedCause() {
    final Exception rootCause = new IllegalStateException("test");
    final Throwable exception = new CompletionException(new CompletionException(rootCause));
    assertThat(ExceptionUtils.rootCause(exception)).isEqualTo(rootCause);
  }

  @Test
  public void rootCauseHandleNullInput() {
    final Exception rootCause = null;
    assertThat(ExceptionUtils.rootCause(rootCause)).isNull();
  }
}

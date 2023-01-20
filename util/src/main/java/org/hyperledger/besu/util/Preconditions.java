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

import java.util.function.Function;

import com.google.errorprone.annotations.FormatMethod;

/** The Preconditions. */
public class Preconditions {
  private Preconditions() {}

  /**
   * Check guard.
   *
   * @param condition the condition
   * @param exceptionGenerator the exception generator
   * @param template the template
   * @param variables the variables
   */
  @FormatMethod
  public static void checkGuard(
      final boolean condition,
      final Function<String, ? extends RuntimeException> exceptionGenerator,
      final String template,
      final Object... variables) {
    if (!condition) {
      throw exceptionGenerator.apply(String.format(template, variables));
    }
  }
}

/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.util;

import java.util.function.Function;

public class Preconditions {
  private Preconditions() {}

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

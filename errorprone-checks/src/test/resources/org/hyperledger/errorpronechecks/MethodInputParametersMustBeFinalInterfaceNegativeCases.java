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
package org.hyperledger.errorpronechecks;

import java.util.Observable;
import java.util.Observer;

public interface MethodInputParametersMustBeFinalInterfaceNegativeCases {

  void parameterCannotBeFinal(int value);

  default void concreteMethod(final long value) {}

  static void anotherConcreteMethod(final double value) {}

  static Observer annonymousClass() {
    return new Observer() {
      @Override
      public void update(final Observable o, final Object arg) {}
    };
  }

  void methodAfterAnnonymousClass(int value);

  enum Status {}

  void methodAfterEnum(int value);
}

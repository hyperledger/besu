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

import javax.annotation.processing.Generated;

public class MethodInputParametersMustBeFinalNegativeCases {

  public void noInputParameters() {}

  public void onlyPrimativeInputParameters(final long value) {}

  public void onlyObjectInputParameters(final Object value) {}

  public void mixedInputParameters(final Object value, final int anotherValue) {}

  public interface allInterfacesAreValid {
    void parameterCannotBeFinal(int value);
  }
}

@Generated(
    value = "test",
    comments = "Every method is buggy, but ignored because the class has been tagged generated")
class MethodInputParametersMustBeFinalPositiveCasesBugGenerated1 {

  public void primativeInputMethod(int value) {}

  public void objectInputMethod(Object value) {}

  public void mixedInputMethod(Object value, int anotherValue) {}

  @Generated(
      value = "test",
      comments = "Every method is buggy, but ignored because the class has been tagged generated")
  public abstract class abstractClassDefinition {
    public void concreteMethodsAreIncluded(int value) {}
  }

  public void varArgsInputMethod(String... value) {}
}

@Generated(
    value = "test",
    comments = "Every method is buggy, but ignored because the class has been tagged generated")
class MethodInputParametersMustBeFinalPositiveCasesBugGenerated2 {

  public void mixedInputMethodFirstFinal(final Object value, int anotherValue) {}

  public void mixedInputMethodSecondFinal(Object value, final int anotherValue) {}
}

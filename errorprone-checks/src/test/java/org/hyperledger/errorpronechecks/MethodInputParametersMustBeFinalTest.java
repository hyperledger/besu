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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class MethodInputParametersMustBeFinalTest {

  private CompilationTestHelper compilationHelper;

  @Before
  public void setup() {
    compilationHelper =
        CompilationTestHelper.newInstance(MethodInputParametersMustBeFinal.class, getClass());
  }

  @Test
  public void methodInputParametersMustBeFinalPositiveCases() {
    compilationHelper.addSourceFile("MethodInputParametersMustBeFinalPositiveCases.java").doTest();
  }

  @Test
  public void methodInputParametersMustBeFinalInterfacePositiveCases() {
    compilationHelper
        .addSourceFile("MethodInputParametersMustBeFinalInterfacePositiveCases.java")
        .doTest();
  }

  @Test
  public void methodInputParametersMustBeFinalNegativeCases() {
    compilationHelper.addSourceFile("MethodInputParametersMustBeFinalNegativeCases.java").doTest();
  }

  @Test
  public void methodInputParametersMustBeFinalInterfaceNegativeCases() {
    compilationHelper
        .addSourceFile("MethodInputParametersMustBeFinalInterfaceNegativeCases.java")
        .doTest();
  }
}

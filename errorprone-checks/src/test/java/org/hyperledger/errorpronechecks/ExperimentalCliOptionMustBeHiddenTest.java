package org.hyperledger.errorpronechecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class ExperimentalCliOptionMustBeHiddenTest {

  private CompilationTestHelper compilationHelper;

  @Before
  public void setup() {
    compilationHelper =
        CompilationTestHelper.newInstance(ExperimentalCliOptionMustBeHidden.class, getClass());
  }

  @Test
  public void experimentalCliOptionMustBeHiddenPositiveCases() {
    compilationHelper.addSourceFile("ExperimentalCliOptionNotHiddenPositiveCases.java").doTest();
  }

  @Test
  public void experimentalCliOptionMustBeHiddenNegativeCases() {
    compilationHelper.addSourceFile("ExperimentalCliOptionNotHiddenNegativeCases.java").doTest();
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

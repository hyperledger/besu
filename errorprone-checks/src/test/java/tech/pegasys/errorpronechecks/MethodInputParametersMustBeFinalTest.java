package tech.pegasys.errorpronechecks;

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

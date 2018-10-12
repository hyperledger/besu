package net.consensys.errorpronechecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class DoNotInvokeMessageDigestDirectlyTest {

  private CompilationTestHelper compilationHelper;

  @Before
  public void setup() {
    compilationHelper =
        CompilationTestHelper.newInstance(DoNotInvokeMessageDigestDirectly.class, getClass());
  }

  @Test
  public void doNotInvokeMessageDigestDirectlyPositiveCases() {
    compilationHelper.addSourceFile("DoNotInvokeMessageDigestDirectlyPositiveCases.java").doTest();
  }

  @Test
  public void doNotInvokeMessageDigestDirectlyNegativeCases() {
    compilationHelper.addSourceFile("DoNotInvokeMessageDigestDirectlyNegativeCases.java").doTest();
  }
}

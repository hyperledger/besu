package net.consensys.errorpronechecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class DoNotCreateSecureRandomDirectlyTest {

  private CompilationTestHelper compilationHelper;

  @Before
  public void setup() {
    compilationHelper =
        CompilationTestHelper.newInstance(DoNotCreateSecureRandomDirectly.class, getClass());
  }

  @Test
  public void doNotCreateSecureRandomDirectlyPositiveCases() {
    compilationHelper.addSourceFile("DoNotCreateSecureRandomDirectlyPositiveCases.java").doTest();
  }

  @Test
  public void doNotCreateSecureRandomDirectlyNegativeCases() {
    compilationHelper.addSourceFile("DoNotCreateSecureRandomDirectlyNegativeCases.java").doTest();
  }
}

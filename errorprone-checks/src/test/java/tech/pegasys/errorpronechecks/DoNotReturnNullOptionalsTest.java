package tech.pegasys.errorpronechecks;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Before;
import org.junit.Test;

public class DoNotReturnNullOptionalsTest {

  private CompilationTestHelper compilationHelper;

  @Before
  public void setup() {
    compilationHelper =
        CompilationTestHelper.newInstance(DoNotReturnNullOptionals.class, getClass());
  }

  @Test
  public void doNotReturnNullPositiveCases() {
    compilationHelper.addSourceFile("DoNotReturnNullOptionalsPositiveCases.java").doTest();
  }

  @Test
  public void doNotReturnNullNegativeCases() {
    compilationHelper.addSourceFile("DoNotReturnNullOptionalsNegativeCases.java").doTest();
  }
}

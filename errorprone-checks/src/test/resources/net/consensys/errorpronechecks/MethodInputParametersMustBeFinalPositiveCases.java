package net.consensys.errorpronechecks;

public class MethodInputParametersMustBeFinalPositiveCases {

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void primativeInputMethod(int value) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void objectInputMethod(Object value) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void mixedInputMethod(Object value, int anotherValue) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void mixedInputMethodFirstFinal(final Object value, int anotherValue) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void mixedInputMethodSecondFinal(Object value, final int anotherValue) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  public void varArgsInputMethod(String... value) {}

  public abstract class abstractClassDefinition {
    // BUG: Diagnostic contains: Method input parameters must be final.
    public void concreteMethodsAreIncluded(int value) {}
  }
}

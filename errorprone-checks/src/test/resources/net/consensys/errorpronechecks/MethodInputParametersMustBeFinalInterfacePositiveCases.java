package net.consensys.errorpronechecks;

public interface MethodInputParametersMustBeFinalInterfacePositiveCases {

  // BUG: Diagnostic contains: Method input parameters must be final.
  default void concreteMethod(int value) {}

  // BUG: Diagnostic contains: Method input parameters must be final.
  static void concreteStaticMethodsAreIncluded(int value) {}
}

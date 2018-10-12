package tech.pegasys.errorpronechecks;

public class MethodInputParametersMustBeFinalNegativeCases {

  public void noInputParameters() {}

  public void onlyPrimativeInputParameters(final long value) {}

  public void onlyObjectInputParameters(final Object value) {}

  public void mixedInputParameters(final Object value, final int anotherValue) {}

  public interface allInterfacesAreValid {
    void parameterCannotBeFinal(int value);
  }
}

package tech.pegasys.errorpronechecks;

import java.util.Observable;
import java.util.Observer;

public interface MethodInputParametersMustBeFinalInterfaceNegativeCases {

  void parameterCannotBeFinal(int value);

  default void concreteMethod(final long value) {}

  static void anotherConcreteMethod(final double value) {}

  static Observer annonymousClass() {
    return new Observer() {
      @Override
      public void update(final Observable o, final Object arg) {}
    };
  }

  void methodAfterAnnonymousClass(int value);

  enum Status {}

  void methodAfterEnum(int value);
}

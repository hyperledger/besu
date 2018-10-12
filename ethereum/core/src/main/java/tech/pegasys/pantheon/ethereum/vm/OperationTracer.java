package tech.pegasys.pantheon.ethereum.vm;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.ehalt.ExceptionalHaltException;

import java.util.Optional;

public interface OperationTracer {

  OperationTracer NO_TRACING =
      ((frame, currentGasCost, executeOperation) -> executeOperation.execute());

  void traceExecution(
      MessageFrame frame, Optional<Gas> currentGasCost, ExecuteOperation executeOperation)
      throws ExceptionalHaltException;

  interface ExecuteOperation {

    void execute() throws ExceptionalHaltException;
  }
}

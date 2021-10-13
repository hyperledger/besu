/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.stream.Collectors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

public class OpenTelemetryTracing implements OperationTracer {

  public static final OpenTelemetryTracing INSTANCE = new OpenTelemetryTracing();

  private final Tracer tracer = GlobalOpenTelemetry.getTracer("org.hyperledger.besu.evm");

  private OpenTelemetryTracing() {}

  @Override
  public void traceExecution(final MessageFrame frame, final ExecuteOperation executeOperation) {
    Span span = tracer.spanBuilder(frame.getContractAddress().toHexString()).startSpan();
    try {
      span.setAttribute("code", frame.getCode().getBytes().toHexString());
      span.setAttribute("sender", frame.getSenderAddress().toHexString());
      span.setAttribute("contract", frame.getContractAddress().toHexString());
      span.setAttribute("gasPrice", frame.getGasPrice().toHexString());
      span.setAttribute("recipient", frame.getRecipientAddress().toHexString());
      span.setAttribute("value", frame.getValue().toHexString());
      final Operation.OperationResult operationResult = executeOperation.execute();
      span.setAttribute(
          "logs", frame.getLogs().stream().map(Log::toString).collect(Collectors.joining()));
      operationResult
          .getGasCost()
          .ifPresent((gasCost) -> span.setAttribute("gasCost", gasCost.toHexString()));
      frame
          .getRevertReason()
          .ifPresent(
              (revertReason) -> span.setAttribute("revertReason", revertReason.toHexString()));
      frame
          .getExceptionalHaltReason()
          .ifPresent((haltReason) -> span.setStatus(StatusCode.ERROR, haltReason.getDescription()));
    } finally {
      span.end();
    }
  }
}

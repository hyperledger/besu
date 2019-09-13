/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;

import com.fasterxml.jackson.annotation.JsonGetter;

public class StructLogWithError extends StructLog {

  private final String[] error;

  public StructLogWithError(final TraceFrame traceFrame) {
    super(traceFrame);
    error =
        traceFrame.getExceptionalHaltReasons().isEmpty()
            ? null
            : traceFrame.getExceptionalHaltReasons().stream()
                .map(ExceptionalHaltReason::name)
                .toArray(String[]::new);
  }

  @JsonGetter("error")
  public String[] getError() {
    return error;
  }
}

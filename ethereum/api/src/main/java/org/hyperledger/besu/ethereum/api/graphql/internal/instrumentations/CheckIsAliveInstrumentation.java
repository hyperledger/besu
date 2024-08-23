/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.graphql.internal.instrumentation;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

import org.hyperledger.besu.ethereum.api.graphql.GraphQLContextType;

import java.util.function.Supplier;

import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckIsAliveInstrumentation extends SimplePerformantInstrumentation {
  private static final Logger LOG = LoggerFactory.getLogger(CheckIsAliveInstrumentation.class);

  @Override
  public @Nullable InstrumentationState createState(
      final InstrumentationCreateStateParameters parameters) {
    final String operationName = parameters.getExecutionInput().getOperationName();
    return new CheckIsAliveInstrumentationState(operationName);
  }

  @Override
  public @Nullable InstrumentationContext<Object> beginFieldFetch(
      final InstrumentationFieldFetchParameters parameters, final InstrumentationState state) {
    final Supplier<Boolean> isAlive =
        parameters.getEnvironment().getGraphQlContext().get(GraphQLContextType.IS_ALIVE_HANDLER);
    if (!isAlive.get()) {
      LOG.warn(
          "Zombie backend operation detected [ {} ], aborting process.",
          ((CheckIsAliveInstrumentationState) state).getOperationName());
      throw new AbortExecutionException("Connection already expired.");
    }
    return noOp();
  }
}

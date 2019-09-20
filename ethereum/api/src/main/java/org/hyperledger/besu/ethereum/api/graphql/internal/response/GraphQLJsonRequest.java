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
package org.hyperledger.besu.ethereum.api.graphql.internal.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

public class GraphQLJsonRequest {
  private String query;
  private String operationName;
  private Map<String, Object> variables;

  @JsonGetter
  public String getQuery() {
    return query;
  }

  @JsonSetter
  public void setQuery(final String query) {
    this.query = query;
  }

  @JsonGetter
  public String getOperationName() {
    return operationName;
  }

  @JsonSetter
  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }

  @JsonGetter
  public Map<String, Object> getVariables() {
    return variables;
  }

  @JsonSetter
  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }
}

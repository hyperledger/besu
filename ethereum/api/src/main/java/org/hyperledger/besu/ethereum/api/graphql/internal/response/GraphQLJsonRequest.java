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

/**
 * This class represents a GraphQL JSON request.
 *
 * <p>It contains the query, operation name, and variables for a GraphQL request. The query is a
 * string that represents the GraphQL query. The operation name is a string that represents the
 * operation to be performed. The variables is a map that contains the variables for the GraphQL
 * query.
 */
public class GraphQLJsonRequest {
  private String query;
  private String operationName;
  private Map<String, Object> variables;

  /** Default constructor for GraphQLJsonRequest. */
  public GraphQLJsonRequest() {}

  /**
   * Gets the query of the GraphQL request.
   *
   * @return the query of the GraphQL request.
   */
  @JsonGetter
  public String getQuery() {
    return query;
  }

  /**
   * Sets the query of the GraphQL request.
   *
   * @param query the query of the GraphQL request.
   */
  @JsonSetter
  public void setQuery(final String query) {
    this.query = query;
  }

  /**
   * Gets the operation name of the GraphQL request.
   *
   * @return the operation name of the GraphQL request.
   */
  @JsonGetter
  public String getOperationName() {
    return operationName;
  }

  /**
   * Sets the operation name of the GraphQL request.
   *
   * @param operationName the operation name of the GraphQL request.
   */
  @JsonSetter
  public void setOperationName(final String operationName) {
    this.operationName = operationName;
  }

  /**
   * Gets the variables of the GraphQL request.
   *
   * @return the variables of the GraphQL request.
   */
  @JsonGetter
  public Map<String, Object> getVariables() {
    return variables;
  }

  /**
   * Sets the variables of the GraphQL request.
   *
   * @param variables the variables of the GraphQL request.
   */
  @JsonSetter
  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }
}

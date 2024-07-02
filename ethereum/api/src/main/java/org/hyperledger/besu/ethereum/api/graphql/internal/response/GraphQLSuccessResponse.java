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

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This class represents a successful GraphQL response.
 *
 * <p>It extends the GraphQLResponse class and overrides the getType method to return
 * GraphQLResponseType.SUCCESS.
 */
public class GraphQLSuccessResponse extends GraphQLResponse {

  /**
   * Constructor for GraphQLSuccessResponse.
   *
   * <p>It calls the parent constructor with the provided data as the argument.
   *
   * @param data the data to be included in the successful response.
   */
  public GraphQLSuccessResponse(final Object data) {
    super(data);
  }

  /**
   * Returns the type of the GraphQL response.
   *
   * <p>This method is overridden to return GraphQLResponseType.SUCCESS, indicating a successful
   * response.
   *
   * @return GraphQLResponseType.SUCCESS
   */
  @Override
  @JsonIgnore
  public GraphQLResponseType getType() {
    return GraphQLResponseType.SUCCESS;
  }
}

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

/**
 * This class represents a GraphQL response with no content.
 *
 * <p>It extends the GraphQLResponse class and overrides the getType method to return
 * GraphQLResponseType.NONE.
 */
public class GraphQLNoResponse extends GraphQLResponse {

  /**
   * Default constructor for GraphQLNoResponse.
   *
   * <p>It calls the parent constructor with null as the argument, indicating no content for this
   * response.
   */
  public GraphQLNoResponse() {
    super(null);
  }

  @Override
  public GraphQLResponseType getType() {
    return GraphQLResponseType.NONE;
  }
}

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

import java.util.Objects;

/**
 * Represents a GraphQL response. This abstract class provides a structure for different types of
 * GraphQL responses.
 */
public abstract class GraphQLResponse {

  private final Object result;

  /**
   * Constructs a new GraphQLResponse with the specified result.
   *
   * @param result the result to be included in the response.
   */
  GraphQLResponse(final Object result) {
    this.result = result;
  }

  /**
   * Returns the type of this GraphQL response.
   *
   * @return the type of this GraphQL response as a GraphQLResponseType.
   */
  public abstract GraphQLResponseType getType();

  /**
   * Returns the result of this GraphQL response.
   *
   * @return the result of this GraphQL response as an Object.
   */
  public Object getResult() {
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GraphQLResponse that = (GraphQLResponse) o;
    return Objects.equals(result, that.result);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(result);
  }
}

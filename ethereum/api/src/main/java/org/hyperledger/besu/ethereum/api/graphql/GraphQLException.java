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
package org.hyperledger.besu.ethereum.api.graphql;

import org.hyperledger.besu.ethereum.api.graphql.internal.response.GraphQLError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import graphql.ErrorType;
import graphql.language.SourceLocation;

class GraphQLException extends RuntimeException implements graphql.GraphQLError {
  private final GraphQLError error;

  GraphQLException(final GraphQLError error) {

    super(error.getMessage());

    this.error = error;
  }

  @Override
  public Map<String, Object> getExtensions() {
    final Map<String, Object> customAttributes = new LinkedHashMap<>();

    customAttributes.put("errorCode", this.error.getCode());
    customAttributes.put("errorMessage", this.getMessage());

    return customAttributes;
  }

  @Override
  public List<SourceLocation> getLocations() {
    return null;
  }

  @Override
  public ErrorType getErrorType() {
    switch (error) {
      case INVALID_PARAMS:
        return ErrorType.ValidationError;
      case INTERNAL_ERROR:
      default:
        return ErrorType.DataFetchingException;
    }
  }
}

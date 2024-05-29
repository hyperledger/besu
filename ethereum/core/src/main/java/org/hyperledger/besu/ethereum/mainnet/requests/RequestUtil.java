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
package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.ethereum.core.DepositRequest;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RequestUtil {

  /**
   * Filters and returns a list of requests of a specific type from a given list of requests.
   *
   * @param <T> The type of the request to filter by, extending Request.
   * @param requests The list of requests to filter.
   * @param requestType The class of the request type to filter for.
   * @return A List containing only requests of the specified type, or an empty list if the input
   *     list is null or contains no requests of the specified type.
   */
  public static <T extends Request> List<T> filterRequestsOfType(
      final List<Request> requests, final Class<T> requestType) {
    if (requests == null) {
      return Collections.emptyList();
    }
    return requests.stream().filter(requestType::isInstance).map(requestType::cast).toList();
  }

  public static Optional<List<DepositRequest>> getDepositRequests(
      final Optional<List<Request>> requests) {
    return requests.map(r -> filterRequestsOfType(r, DepositRequest.class));
  }

  public static Optional<List<WithdrawalRequest>> getWithdrawalRequests(
      final Optional<List<Request>> requests) {
    return requests.map(r -> filterRequestsOfType(r, WithdrawalRequest.class));
  }

  /**
   * Combines two optional lists of requests into a single optional list.
   *
   * @param maybeDepositRequests Optional list of deposit requests.
   * @param maybeWithdrawalRequest Optional list of withdrawal requests.
   * @return An Optional containing the combined list of requests, or an empty Optional if both
   *     inputs are empty.
   */
  public static Optional<List<Request>> combine(
      final Optional<List<Request>> maybeDepositRequests,
      final Optional<List<Request>> maybeWithdrawalRequest) {
    if (maybeDepositRequests.isEmpty() && maybeWithdrawalRequest.isEmpty()) {
      return Optional.empty();
    }
    List<Request> requests = new ArrayList<>();
    maybeDepositRequests.ifPresent(requests::addAll);
    maybeWithdrawalRequest.ifPresent(requests::addAll);
    return Optional.of(requests);
  }
}

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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.ethereum.mainnet.requests.ConsolidationRequestValidator.MAX_CONSOLIDATION_REQUESTS_PER_BLOCK;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;

public class ConsolidationRequestValidatorTestFixtures {

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  static ConsolidationRequestTestParameter
      blockWithConsolidationRequestsAndWithdrawalRequestsRoot() {
    final ConsolidationRequest consolidationRequest = createConsolidationRequest();
    final Optional<List<Request>> maybeConsolidationRequests =
        Optional.of(List.of(consolidationRequest));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequestsRoot(BodyValidation.requestsRoot(maybeConsolidationRequests.get()))
            .setRequests(maybeConsolidationRequests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ConsolidationRequestTestParameter(
        "Block with consolidation requests and withdrawal_requests_root",
        block,
        Optional.of(List.of(consolidationRequest)));
  }

  static ConsolidationRequestTestParameter blockWithConsolidationRequestsMismatch() {
    final ConsolidationRequest consolidationRequest = createConsolidationRequest();

    final Optional<List<Request>> requests =
        Optional.of(List.of(consolidationRequest, consolidationRequest));

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequestsRoot(BodyValidation.requestsRoot(requests.get()))
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ConsolidationRequestTestParameter(
        "Block with consolidation requests mismatch",
        block,
        Optional.of(List.of(consolidationRequest, consolidationRequest)),
        List.of(createConsolidationRequest()));
  }

  static ConsolidationRequestTestParameter blockWithMoreThanMaximumConsolidationRequests() {
    final List<ConsolidationRequest> consolidationRequests =
        IntStream.range(0, MAX_CONSOLIDATION_REQUESTS_PER_BLOCK + 1)
            .mapToObj(__ -> createConsolidationRequest())
            .toList();

    final Optional<List<ConsolidationRequest>> maybeConsolidationRequest =
        Optional.of(consolidationRequests);
    final Optional<List<Request>> maybeRequests =
        Optional.of(consolidationRequests.stream().map(r -> (Request) r).toList());

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setRequestsRoot(BodyValidation.requestsRoot(maybeRequests.get()))
            .setRequests(maybeRequests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new ConsolidationRequestTestParameter(
        "Block with more than maximum consolidation requests", block, maybeConsolidationRequest);
  }

  static ConsolidationRequest createConsolidationRequest() {
    return new ConsolidationRequest(
        Address.extract(Bytes32.random()),
        BLSPublicKey.wrap(Bytes48.random()),
        BLSPublicKey.wrap(Bytes48.random()));
  }

  static class ConsolidationRequestTestParameter {

    String description;
    Block block;
    Optional<List<ConsolidationRequest>> maybeConsolidationRequest;
    List<ConsolidationRequest> expectedConsolidationRequest;

    public ConsolidationRequestTestParameter(
        final String description,
        final Block block,
        final Optional<List<ConsolidationRequest>> maybeConsolidationRequest) {
      this(
          description,
          block,
          maybeConsolidationRequest,
          maybeConsolidationRequest.orElseGet(List::of));
    }

    public ConsolidationRequestTestParameter(
        final String description,
        final Block block,
        final Optional<List<ConsolidationRequest>> maybeConsolidationRequest,
        final List<ConsolidationRequest> expectedConsolidationRequest) {
      this.description = description;
      this.block = block;
      this.maybeConsolidationRequest = maybeConsolidationRequest;
      this.expectedConsolidationRequest = expectedConsolidationRequest;
    }

    @Override
    public String toString() {
      return description;
    }
  }
}

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

import static org.hyperledger.besu.ethereum.mainnet.WithdrawalRequestContractHelper.MAX_WITHDRAWAL_REQUESTS_PER_BLOCK;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;

public class WithdrawalRequestValidatorTestFixtures {

  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  static WithdrawalRequestTestParameter blockWithWithdrawalRequestsAndWithdrawalRequestsRoot() {
    final Optional<List<WithdrawalRequest>> maybeWithdrawalRequests =
        Optional.of(java.util.List.of(createWithdrawalRequest()));

    var requests = createRequests(maybeWithdrawalRequests);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawalRequestsRoot(BodyValidation.requestsRoot(requests.get()))
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with withdrawal requests and withdrawal_requests_root",
        block,
        maybeWithdrawalRequests);
  }

  static WithdrawalRequestTestParameter blockWithoutWithdrawalRequestsWithWithdrawalRequestsRoot() {
    final Optional<List<WithdrawalRequest>> withdrawalRequests = Optional.empty();
    var requests = createRequests(withdrawalRequests);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawalRequestsRoot(Hash.EMPTY)
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with withdrawal_requests_root but without withdrawal requests",
        block,
        withdrawalRequests);
  }

  static WithdrawalRequestTestParameter blockWithWithdrawalRequestsWithoutWithdrawalRequestsRoot() {
    final Optional<List<WithdrawalRequest>> withdrawalRequest =
        Optional.of(java.util.List.of(createWithdrawalRequest()));
    var requests = createRequests(withdrawalRequest);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with withdrawal requests but without withdrawal_requests_root",
        block,
        withdrawalRequest);
  }

  static WithdrawalRequestTestParameter blockWithoutWithdrawalRequestsAndWithdrawalRequestsRoot() {
    final Optional<List<WithdrawalRequest>> maybeExits = Optional.empty();
    var requests = createRequests(maybeExits);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block without withdrawal requests and withdrawal_requests_root", block, maybeExits);
  }

  static WithdrawalRequestTestParameter blockWithWithdrawalRequestsRootMismatch() {
    final Optional<List<WithdrawalRequest>> maybeExits =
        Optional.of(java.util.List.of(createWithdrawalRequest()));
    var requests = createRequests(maybeExits);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawalRequestsRoot(Hash.EMPTY)
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with withdrawal_requests_root mismatch", block, maybeExits);
  }

  static WithdrawalRequestTestParameter blockWithWithdrawalRequestsMismatch() {
    final Optional<List<WithdrawalRequest>> maybeExits =
        Optional.of(java.util.List.of(createWithdrawalRequest(), createWithdrawalRequest()));
    var requests = createRequests(maybeExits);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawalRequestsRoot(BodyValidation.requestsRoot(requests.get()))
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with withdrawal requests mismatch",
        block,
        maybeExits,
        java.util.List.of(createWithdrawalRequest()));
  }

  static WithdrawalRequestTestParameter blockWithMoreThanMaximumWithdrawalRequests() {
    final List<WithdrawalRequest> validatorExits =
        IntStream.range(0, MAX_WITHDRAWAL_REQUESTS_PER_BLOCK + 1)
            .mapToObj(__ -> createWithdrawalRequest())
            .toList();
    final Optional<List<WithdrawalRequest>> maybeExits = Optional.of(validatorExits);
    var requests = createRequests(maybeExits);

    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawalRequestsRoot(BodyValidation.requestsRoot(requests.get()))
            .setRequests(requests);
    final Block block = blockDataGenerator.block(blockOptions);

    return new WithdrawalRequestTestParameter(
        "Block with more than maximum withdrawal requests", block, maybeExits);
  }

  static WithdrawalRequest createWithdrawalRequest() {
    return new WithdrawalRequest(
        Address.extract(Bytes32.random()), BLSPublicKey.wrap(Bytes48.random()), GWei.ONE);
  }

  static class WithdrawalRequestTestParameter {

    String description;
    Block block;
    Optional<List<WithdrawalRequest>> maybeWithdrawalRequest;
    List<WithdrawalRequest> expectedWithdrawalRequest;

    public WithdrawalRequestTestParameter(
        final String description,
        final Block block,
        final Optional<List<WithdrawalRequest>> maybeWithdrawalRequest) {
      this(
          description,
          block,
          maybeWithdrawalRequest,
          maybeWithdrawalRequest.orElseGet(java.util.List::of));
    }

    public WithdrawalRequestTestParameter(
        final String description,
        final Block block,
        final Optional<List<WithdrawalRequest>> maybeWithdrawalRequest,
        final List<WithdrawalRequest> expectedWithdrawalRequest) {
      this.description = description;
      this.block = block;
      this.maybeWithdrawalRequest = maybeWithdrawalRequest;
      this.expectedWithdrawalRequest = expectedWithdrawalRequest;
    }

    @Override
    public String toString() {
      return description;
    }
  }

  private static Optional<List<Request>> createRequests(
      Optional<List<WithdrawalRequest>> withdrawalRequests) {
    throw new NotImplementedException();
  }
}

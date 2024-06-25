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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.SystemCallProcessor;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public class WithdrawalRequestProcessor implements RequestProcessor {
  private static final Address WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS =
      Address.fromHexString("0x00A3ca265EBcb825B45F985A16CEFB49958cE017");
  private static final int ADDRESS_BYTES = 20;
  private static final int PUBLIC_KEY_BYTES = 48;
  private static final int AMOUNT_BYTES = 8;
  private static final int WITHDRAWAL_REQUEST_BYTES_SIZE =
      ADDRESS_BYTES + PUBLIC_KEY_BYTES + AMOUNT_BYTES;

  /**
   * Processes a system call and convert the result to withdrawal requests
   *
   * @param blockHeader The block header being processed.
   * @param mutableWorldState The mutable world state.
   * @param protocolSpec The protocol specification.
   * @param transactionReceipts A list of transaction receipts.
   * @param blockHashLookup A lookup function for block hashes.
   * @param operationTracer A tracer for EVM operations.
   * @return An {@link Optional} containing a list of {@link WithdrawalRequest} objects if any are
   *     found, or an empty {@link Optional} if none are found.
   */
  @Override
  public Optional<List<? extends Request>> process(
      final ProcessableBlockHeader blockHeader,
      final MutableWorldState mutableWorldState,
      final ProtocolSpec protocolSpec,
      final List<TransactionReceipt> transactionReceipts,
      final BlockHashOperation.BlockHashLookup blockHashLookup,
      final OperationTracer operationTracer) {

    SystemCallProcessor systemCallProcessor =
        new SystemCallProcessor(protocolSpec.getTransactionProcessor());
    Bytes systemCallOutput =
        systemCallProcessor.process(
            WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS,
            mutableWorldState.updater(),
            blockHeader,
            operationTracer,
            blockHashLookup);
    List<WithdrawalRequest> withdrawalRequests = parseWithdrawalRequests(systemCallOutput);
    return Optional.of(withdrawalRequests);
  }

  /**
   * Parses the provided bytes into a list of {@link WithdrawalRequest} objects.
   *
   * @param bytes The bytes representing withdrawal requests.
   * @return A list of parsed {@link WithdrawalRequest} objects.
   */
  private List<WithdrawalRequest> parseWithdrawalRequests(final Bytes bytes) {
    final List<WithdrawalRequest> withdrawalRequests = new ArrayList<>();
    if (bytes == null || bytes.isEmpty()) {
      return withdrawalRequests;
    }
    int count = bytes.size() / WITHDRAWAL_REQUEST_BYTES_SIZE;
    for (int i = 0; i < count; i++) {
      Bytes requestBytes =
          bytes.slice(i * WITHDRAWAL_REQUEST_BYTES_SIZE, WITHDRAWAL_REQUEST_BYTES_SIZE);
      withdrawalRequests.add(parseSingleWithdrawalRequest(requestBytes));
    }
    return withdrawalRequests;
  }

  /**
   * Parses a single withdrawal request from the given bytes.
   *
   * @param requestBytes The bytes representing a single withdrawal request.
   * @return A {@link WithdrawalRequest} object parsed from the provided bytes.
   */
  private WithdrawalRequest parseSingleWithdrawalRequest(final Bytes requestBytes) {
    final Address sourceAddress = Address.wrap(requestBytes.slice(0, ADDRESS_BYTES));
    final BLSPublicKey validatorPublicKey =
        BLSPublicKey.wrap(requestBytes.slice(ADDRESS_BYTES, PUBLIC_KEY_BYTES));
    final UInt64 amount =
        UInt64.fromBytes(requestBytes.slice(ADDRESS_BYTES + PUBLIC_KEY_BYTES, AMOUNT_BYTES));
    return new WithdrawalRequest(sourceAddress, validatorPublicKey, GWei.of(amount));
  }
}

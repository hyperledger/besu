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
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.mainnet.SystemCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Abstract base class for processing system call requests.
 *
 * @param <T> The type of request to be processed.
 */
public abstract class AbstractSystemCallRequestProcessor<T extends Request>
    implements RequestProcessor {

  /**
   * Processes a system call and converts the result into requests of type T.
   *
   * @param context The request context being processed.
   * @return An {@link Optional} containing a list of {@link T} objects if any are found, or an
   *     empty {@link Optional} if none are found.
   */
  @Override
  public Optional<List<? extends Request>> process(final ProcessRequestContext context) {
    WorldUpdater updater = context.mutableWorldState().updater();

    // Check if the system call address is deployed
    if (updater.get(getCallAddress()) == null) {
      String error = String.format("System call address %s is not deployed", getCallAddress());
      throw new RuntimeException(error);
    }

    // Process the system call
    SystemCallProcessor systemCallProcessor =
        new SystemCallProcessor(context.protocolSpec().getTransactionProcessor());
    Bytes systemCallOutput =
        systemCallProcessor.process(
            getCallAddress(),
            updater,
            context.blockHeader(),
            context.operationTracer(),
            context.blockHashLookup());

    // Parse the system call output into requests
    return Optional.ofNullable(parseRequests(systemCallOutput));
  }

  /**
   * Parses the provided bytes into a list of {@link T} objects.
   *
   * @param bytes The bytes representing requests.
   * @return A list of parsed {@link T} objects.
   */
  protected List<T> parseRequests(final Bytes bytes) {
    if (bytes == null) {
      return null;
    }
    final List<T> requests = new ArrayList<>();
    if (bytes.isEmpty()) {
      return requests;
    }
    int count = bytes.size() / getRequestBytesSize();
    for (int i = 0; i < count; i++) {
      Bytes requestBytes = bytes.slice(i * getRequestBytesSize(), getRequestBytesSize());
      requests.add(parseRequest(requestBytes));
    }
    return requests;
  }

  /**
   * Parses a single request from the provided bytes.
   *
   * @param requestBytes The bytes representing a single request.
   * @return A parsed {@link T} object.
   */
  protected abstract T parseRequest(final Bytes requestBytes);

  /**
   * Gets the call address for the specific request type.
   *
   * @return The call address.
   */
  protected abstract Address getCallAddress();

  /**
   * Gets the size of the bytes representing a single request.
   *
   * @return The size of the bytes representing a single request.
   */
  protected abstract int getRequestBytesSize();
}

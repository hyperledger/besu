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

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.EVMWorldUpdater;

import java.math.BigInteger;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeDelegationProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(CodeDelegationProcessor.class);

  private final Optional<BigInteger> maybeChainId;

  public CodeDelegationProcessor(final Optional<BigInteger> maybeChainId) {
    this.maybeChainId = maybeChainId;
  }

  /**
   * At the start of executing the transaction, after incrementing the sender’s nonce, for each
   * authorization we do the following: 1. Verify the chain id is either 0 or the chain's current
   * ID. 2. `authority = ecrecover(keccak(MAGIC || rlp([chain_id, address, nonce])), y_parity, r,
   * s]` 3. Add `authority` to `accessed_addresses` (as defined in [EIP-2929](./eip-2929.md).) 4.
   * Verify the code of `authority` is either empty or already delegated. 5. Verify the nonce of
   * `authority` is equal to `nonce`. 6. Add `PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST` gas to
   * the global refund counter if `authority` exists in the trie. 7. Set the code of `authority` to
   * be `0xef0100 || address`. This is a delegation designation. 8. Increase the nonce of
   * `authority` by one.
   *
   * @param evmWorldUpdater The world state updater which is aware of code delegation.
   * @param transaction The transaction being processed.
   * @return The result of the code delegation processing.
   */
  public CodeDelegationResult process(
      final EVMWorldUpdater evmWorldUpdater, final Transaction transaction) {
    final CodeDelegationResult result = new CodeDelegationResult();

    transaction
        .getCodeDelegationList()
        .get()
        .forEach(
            payload ->
                payload
                    .authorizer()
                    .ifPresent(
                        authorityAddress -> {
                          LOG.trace("Set code delegation for authority: {}", authorityAddress);

                          if (maybeChainId.isPresent()
                              && !payload.chainId().equals(BigInteger.ZERO)
                              && !maybeChainId.get().equals(payload.chainId())) {
                            return;
                          }

                          final Optional<MutableAccount> maybeAuthorityAccount =
                              Optional.ofNullable(evmWorldUpdater.getAccount(authorityAddress));

                          result.addAccessedDelegatorAddress(authorityAddress);

                          MutableAccount authority;
                          boolean authorityDoesAlreadyExist = false;
                          if (maybeAuthorityAccount.isEmpty()) {
                            authority = evmWorldUpdater.createAccount(authorityAddress);
                          } else {
                            authority = maybeAuthorityAccount.get();

                            if (!evmWorldUpdater
                                .authorizedCodeService()
                                .canSetDelegatedCode(authority)) {
                              return;
                            }

                            authorityDoesAlreadyExist = true;
                          }

                          if (payload.nonce() != authority.getNonce()) {
                            return;
                          }

                          if (authorityDoesAlreadyExist) {
                            result.incremenentAlreadyExistingDelegators();
                          }

                          evmWorldUpdater
                              .authorizedCodeService()
                              .addDelegatedCode(authority, payload.address());
                          authority.incrementNonce();
                        }));

    return result;
  }
}

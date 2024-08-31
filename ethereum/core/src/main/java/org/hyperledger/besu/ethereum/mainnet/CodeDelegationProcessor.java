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
import org.hyperledger.besu.evm.account.AccountState;
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
                          final long authorityNonce =
                              maybeAuthorityAccount.map(AccountState::getNonce).orElse(0L);

                          if (payload.nonce() != authorityNonce) {
                            return;
                          }

                          result.addAccessedDelegatorAddress(authorityAddress);

                          MutableAccount authority;
                          if (maybeAuthorityAccount.isEmpty()) {
                            authority = evmWorldUpdater.createAccount(authorityAddress);
                          } else {
                            authority = maybeAuthorityAccount.get();

                            if (!evmWorldUpdater
                                .authorizedCodeService()
                                .canSetDelegatedCode(authority)) {
                              return;
                            }

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

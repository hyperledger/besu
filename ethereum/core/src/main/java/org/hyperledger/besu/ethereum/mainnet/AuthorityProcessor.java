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
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.AuthorizedCodeService;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorityProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(AuthorityProcessor.class);

  private final Optional<BigInteger> maybeChainId;

  public AuthorityProcessor(final Optional<BigInteger> maybeChainId) {
    this.maybeChainId = maybeChainId;
  }

  public void addContractToAuthority(
      final WorldUpdater worldState,
      final AuthorizedCodeService authorizedCodeService,
      final Transaction transaction) {

    transaction
        .getAuthorizationList()
        .get()
        .forEach(
            payload ->
                payload
                    .authorizer()
                    .ifPresent(
                        authorityAddress -> {
                          LOG.trace("Set code authority: {}", authorityAddress);

                          if (maybeChainId.isPresent()
                              && !payload.chainId().equals(BigInteger.ZERO)
                              && !maybeChainId.get().equals(payload.chainId())) {
                            return;
                          }

                          final Optional<MutableAccount> maybeAccount =
                              Optional.ofNullable(worldState.getAccount(authorityAddress));
                          final long accountNonce =
                              maybeAccount.map(AccountState::getNonce).orElse(0L);

                          if (payload.nonce().isPresent()
                              && !payload.nonce().get().equals(accountNonce)) {
                            return;
                          }

                          if (authorizedCodeService.hasAuthorizedCode(authorityAddress)) {
                            return;
                          }

                          Optional<Account> codeAccount =
                              Optional.ofNullable(worldState.get(payload.address()));
                          final Bytes code;
                          if (codeAccount.isPresent()) {
                            code = codeAccount.get().getCode();
                          } else {
                            code = Bytes.EMPTY;
                          }

                          authorizedCodeService.addAuthorizedCode(authorityAddress, code);
                        }));
  }
}

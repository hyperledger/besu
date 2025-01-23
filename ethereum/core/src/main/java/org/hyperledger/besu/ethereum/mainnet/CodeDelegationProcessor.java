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

import static org.hyperledger.besu.evm.account.Account.MAX_NONCE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.CodeDelegation;
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
  private final BigInteger halfCurveOrder;

  public CodeDelegationProcessor(
      final Optional<BigInteger> maybeChainId, final BigInteger halfCurveOrder) {
    this.maybeChainId = maybeChainId;
    this.halfCurveOrder = halfCurveOrder;
  }

  /**
   * At the start of executing the transaction, after incrementing the sender’s nonce, for each
   * authorization we do the following:
   *
   * <ol>
   *   <li>Verify the chain id is either 0 or the chain's current ID.
   *   <li>`authority = ecrecover(keccak(MAGIC || rlp([chain_id, address, nonce])), y_parity, r, s]`
   *   <li>Add `authority` to `accessed_addresses` (as defined in [EIP-2929](./eip-2929.md).)
   *   <li>Verify the code of `authority` is either empty or already delegated.
   *   <li>Verify the nonce of `authority` is equal to `nonce`.
   *   <li>Add `PER_EMPTY_ACCOUNT_COST - PER_AUTH_BASE_COST` gas to the global refund counter if
   *       `authority` exists in the trie.
   *   <li>Set the code of `authority` to be `0xef0100 || address`. This is a delegation
   *       designation.
   *   <li>Increase the nonce of `authority` by one.
   * </ol>
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
            codeDelegation ->
                processCodeDelegation(
                    evmWorldUpdater,
                    (org.hyperledger.besu.ethereum.core.CodeDelegation) codeDelegation,
                    result));

    return result;
  }

  private void processCodeDelegation(
      final EVMWorldUpdater evmWorldUpdater,
      final CodeDelegation codeDelegation,
      final CodeDelegationResult result) {
    LOG.trace("Processing code delegation: {}", codeDelegation);

    if (maybeChainId.isPresent()
        && !codeDelegation.chainId().equals(BigInteger.ZERO)
        && !maybeChainId.get().equals(codeDelegation.chainId())) {
      LOG.trace(
          "Invalid chain id for code delegation. Expected: {}, Actual: {}",
          maybeChainId.get(),
          codeDelegation.chainId());
      return;
    }

    if (codeDelegation.nonce() == MAX_NONCE) {
      LOG.trace("Nonce of code delegation must be less than 2^64-1");
      return;
    }

    if (codeDelegation.signature().getS().compareTo(halfCurveOrder) > 0) {
      LOG.trace(
          "Invalid signature for code delegation. S value must be less or equal than the half curve order.");
      return;
    }

    if (codeDelegation.signature().getRecId() != 0 && codeDelegation.signature().getRecId() != 1) {
      LOG.trace("Invalid signature for code delegation. RecId must be 0 or 1.");
      return;
    }

    final Optional<Address> authorizer = codeDelegation.authorizer();
    if (authorizer.isEmpty()) {
      LOG.trace("Invalid signature for code delegation");
      return;
    }

    LOG.trace("Set code delegation for authority: {}", authorizer.get());

    final Optional<MutableAccount> maybeAuthorityAccount =
        Optional.ofNullable(evmWorldUpdater.getAccount(authorizer.get()));

    result.addAccessedDelegatorAddress(authorizer.get());

    MutableAccount authority;
    boolean authorityDoesAlreadyExist = false;
    if (maybeAuthorityAccount.isEmpty()) {
      authority = evmWorldUpdater.createAccount(authorizer.get());
    } else {
      authority = maybeAuthorityAccount.get();

      if (!evmWorldUpdater.codeDelegationService().canSetCodeDelegation(authority)) {
        return;
      }

      authorityDoesAlreadyExist = true;
    }

    if (codeDelegation.nonce() != authority.getNonce()) {
      LOG.trace(
          "Invalid nonce for code delegation. Expected: {}, Actual: {}",
          authority.getNonce(),
          codeDelegation.nonce());
      return;
    }

    if (authorityDoesAlreadyExist) {
      result.incremenentAlreadyExistingDelegators();
    }

    evmWorldUpdater
        .codeDelegationService()
        .processCodeDelegation(authority, codeDelegation.address());
    authority.incrementNonce();
  }
}

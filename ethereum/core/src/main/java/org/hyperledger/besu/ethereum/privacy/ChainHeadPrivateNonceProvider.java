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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.privacy.RestrictedDefaultPrivacyController.PmtTransactionTracker;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;

public class ChainHeadPrivateNonceProvider implements PrivateNonceProvider {
  private final Blockchain blockchain;
  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;

  public ChainHeadPrivateNonceProvider(
      final Blockchain blockchain,
      final PrivateStateRootResolver privateStateRootResolver,
      final WorldStateArchive privateWorldStateArchive) {
    this.blockchain = blockchain;
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  @Override
  public long getNonce(final Address sender, final Bytes32 privacyGroupId) {
    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    Hash chainHeadHash = chainHeadHeader.getHash();

    final Hash stateRoot =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, chainHeadHash);
    return privateWorldStateArchive
        .get(stateRoot, chainHeadHash)
        .map(
            privateWorldState -> {
              final Account account = privateWorldState.get(sender);
              return account == null ? 0L : account.getNonce();
            })
        .orElse(Account.DEFAULT_NONCE);
  }

  /**
   * Calculate the nonce while taking into account any PMT that are already in progress. This makes
   * nonce management for private tx slightly cleaner.
   *
   * @param pmtPool the pool
   * @param sender the sender of the transaction
   * @param privacyGroupId the privacy group ID this tx is for
   * @return the nonce, taking into account the PMT in the pool
   */
  @Override
  public long getNonce(
      final Map<Hash, PmtTransactionTracker> pmtPool,
      final Address sender,
      final Bytes32 privacyGroupId) {
    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    Hash chainHeadHash = chainHeadHeader.getHash();
    // TODO privacyGroupID Bytes32 vs String
    long countPending =
        pmtPool.values().stream()
            .filter(
                pmt ->
                    pmt.getSender().equals(sender.toString())
                        && pmt.getPrivacyGroupId().equals(privacyGroupId.toHexString()))
            .count();
    System.out.println(
        "pmtPool = " + pmtPool + ", sender = " + sender + ", privacyGroupId = " + privacyGroupId);
    System.out.println("number of matching PMTs in the pool = " + countPending);
    // TODO do we need to add the privateNonce to the pool
    // pendingNonce
    // if latestNonce > pendingNonce return latestNonce (as below)
    // else return pendingNonce + 1

    final Hash stateRoot =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, chainHeadHash);
    return privateWorldStateArchive
        .get(stateRoot, chainHeadHash)
        .map(
            privateWorldState -> {
              final Account account = privateWorldState.get(sender);
              return account == null ? 0L : account.getNonce() + countPending;
            })
        .orElse(Account.DEFAULT_NONCE);
  }
}

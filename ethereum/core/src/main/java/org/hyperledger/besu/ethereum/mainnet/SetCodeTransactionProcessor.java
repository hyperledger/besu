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

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.SetCodeAuthorization;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.SetCodeTransactionEncoder;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetCodeTransactionProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(SetCodeTransactionProcessor.class);

  private static final Bytes MAGIC = Bytes.of(0x05);

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private final BigInteger chainId;

  public SetCodeTransactionProcessor(final BigInteger chainId) {
    this.chainId = chainId;
  }

  public Set<Address> addContractToAuthority(
      final WorldUpdater worldUpdater, final Transaction transaction) {
    final Set<Address> authorityList = new HashSet<>();

    transaction
        .setCodeTransactionPayloads()
        .get()
        .forEach(
            payload -> {
              recoverAuthority(payload)
                  .ifPresent(
                      authorityAddress -> {
                        LOG.trace("Adding authority: {}", authorityAddress);

                        if (!chainId.equals(BigInteger.ZERO)
                            && !payload.chainId().equals(BigInteger.ZERO)
                            && !chainId.equals(payload.chainId())) {
                          ;
                        }

                        final Optional<MutableAccount> maybeAccount =
                            Optional.ofNullable(worldUpdater.getAccount(authorityAddress));
                        final long accountNonce =
                            maybeAccount.map(AccountState::getNonce).orElse(0L);

                        if (payload.nonces().size() == 1
                            && !payload.nonces().getFirst().equals(accountNonce)) {
                          return;
                        }

                        worldUpdater.addCodeToEOA(
                            authorityAddress, worldUpdater.getAccount(payload.address()).getCode());
                        authorityList.add(authorityAddress);
                      });
            });

    return authorityList;
  }

  private Optional<Address> recoverAuthority(final SetCodeAuthorization authorization) {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    SetCodeTransactionEncoder.encodeSingleSetCode(authorization, rlpOutput);

    final Hash hash = Hash.hash(MAGIC.or(rlpOutput.encoded()));

    return SIGNATURE_ALGORITHM
        .get()
        .recoverPublicKeyFromSignature(hash, authorization.signature())
        .map(Address::extract);
  }

  public void removeCodeFromAuthorities(
      final WorldUpdater worldUpdater, final Set<Address> authorities) {
    authorities.forEach(worldUpdater::removeCodeFromEOA);
  }
}

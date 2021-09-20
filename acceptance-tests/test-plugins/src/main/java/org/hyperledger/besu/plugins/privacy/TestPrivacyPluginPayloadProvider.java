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
 *
 */
package org.hyperledger.besu.plugins.privacy;

import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.readFrom;
import static org.hyperledger.besu.ethereum.privacy.PrivateTransaction.serialize;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.data.Transaction;
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class TestPrivacyPluginPayloadProvider implements PrivacyPluginPayloadProvider {
  private static final Logger LOG = LogManager.getLogger();
  private final String prefix;

  public TestPrivacyPluginPayloadProvider(final String prefix) {
    this.prefix = prefix;
  }

  @Override
  public Bytes generateMarkerPayload(
      final PrivateTransaction privateTransaction, final String privacyUserId) {

    return Bytes.wrap(Bytes.fromHexString(prefix), serialize(privateTransaction).encoded());
  }

  @Override
  public Optional<PrivateTransaction> getPrivateTransactionFromPayload(
      final Transaction transaction) {

    final Bytes prefixBytes = Bytes.fromHexString(prefix);
    if (transaction.getPayload().slice(0, prefixBytes.size()).equals(prefixBytes)) {
      LOG.info("processing payload for " + prefix);
      final BytesValueRLPInput bytesValueRLPInput =
          new BytesValueRLPInput(transaction.getPayload().slice(prefixBytes.size()).copy(), false);
      return Optional.of(readFrom(bytesValueRLPInput));
    } else {
      LOG.info("Can not process payload for " + prefix);
      return Optional.empty();
    }
  }
}

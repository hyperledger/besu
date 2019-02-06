/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {
  private final Enclave enclave;
  private final String enclavePublicKey;

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(gasCalculator, privacyParameters.getPublicKey(), new Enclave(privacyParameters.getUrl()));
  }

  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final String publicKey, final Enclave enclave) {
    super("Privacy", gasCalculator);
    this.enclave = enclave;
    this.enclavePublicKey = publicKey;
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(40_000L); // Not sure
  }

  @Override
  public BytesValue compute(final BytesValue input) {
    try {
      String key = new String(input.extractArray(), UTF_8);
      ReceiveRequest receiveRequest = new ReceiveRequest(key, enclavePublicKey);
      ReceiveResponse receiveResponse = enclave.receive(receiveRequest);
      LOG.info("Got the response as ", receiveResponse.getPayload());
      return BytesValue.wrap(receiveResponse.getPayload());
      // pass it to private tx processor
    } catch (IOException e) {
      LOG.fatal("Enclave threw an unhandled exception.", e);
      return BytesValue.EMPTY;
    }
  }
}

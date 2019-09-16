/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake.ecies;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link InitiatorHandshakeMessageV4}. */
public final class InitiatorHandshakeMessageV4Test {

  private static final BytesValue EXAMPLE_MESSAGE;

  private static final SECP256K1.KeyPair EXAMPLE_KEYPAIR;

  static {
    try {
      EXAMPLE_KEYPAIR =
          SECP256K1.KeyPair.load(
              new File(InitiatorHandshakeMessageV4.class.getResource("test.keypair").toURI()));
    } catch (final IOException | URISyntaxException ex) {
      throw new IllegalStateException(ex);
    }
    try {
      EXAMPLE_MESSAGE =
          BytesValue.fromHexString(
              Resources.readLines(
                      InitiatorHandshakeMessageV4Test.class.getResource("test.initiatormessage"),
                      StandardCharsets.UTF_8)
                  .get(0));
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Test
  public void encodeDecodeRoundtrip() {
    final InitiatorHandshakeMessageV4 initial =
        InitiatorHandshakeMessageV4.decode(EXAMPLE_MESSAGE, EXAMPLE_KEYPAIR);
    final BytesValue encoded = initial.encode();
    Assertions.assertThat(encoded).isEqualTo(EXAMPLE_MESSAGE.slice(0, encoded.size()));
  }
}

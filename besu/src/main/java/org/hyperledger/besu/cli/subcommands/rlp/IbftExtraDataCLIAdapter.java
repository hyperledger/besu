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
package org.hyperledger.besu.cli.subcommands.rlp;

import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adapter to convert a typed JSON to an IbftExtraData object This adapter handles the JSON to RLP
 * encoding
 */
public class IbftExtraDataCLIAdapter implements JSONToRLP {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Collection<String>> TYPE_REF =
      new TypeReference<Collection<String>>() {};

  @Override
  public BytesValue encode(final String json) throws IOException {
    return fromJsonAddresses(json).encode();
  }

  public static IbftExtraData fromJsonAddresses(final String jsonAddresses) throws IOException {
    final Collection<String> validatorAddresses = MAPPER.readValue(jsonAddresses, TYPE_REF);
    return IbftExtraData.fromAddresses(
        validatorAddresses.stream().map(Address::fromHexString).collect(Collectors.toList()));
  }
}

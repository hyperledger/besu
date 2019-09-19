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
package org.hyperledger.besu.cli.subcommands.networkcreate.mapping;

import org.hyperledger.besu.cli.subcommands.networkcreate.model.Configuration;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;

import java.security.InvalidParameterException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.io.Files;

// TODO Handle errors
public abstract class MapperAdapter {

  ObjectMapper mapper;

  public abstract <T> T map(TypeReference<T> clazz) throws Exception;

  public static MapperAdapter getMapper(final String initFile) {
    switch (Files.getFileExtension(initFile)) {
      case "yaml":
      case "yml":
        return new YAMLMapperAdapter(initFile);
      case "toml":
      case "tml":
        return new TOMLMapperAdapter(initFile);
      case "json":
        return new JSONMapperAdapter(initFile);
      default:
        throw new InvalidParameterException("File type not handled.");
    }
  }

  ObjectMapper getMapper(final JsonFactory factory) {
    ObjectMapper mapper = new ObjectMapper(factory);
    mapper.configure(Feature.STRICT_DUPLICATE_DETECTION, true);

    mapper.registerModule(new Jdk8Module());

    SimpleModule addressModule = new SimpleModule("CustomAddressSerializer");
    addressModule.addSerializer(Address.class, new CustomAddressSerializer());
    mapper.registerModule(addressModule);

    SimpleModule balanceModule = new SimpleModule("CustomBalanceSerializer");
    balanceModule.addSerializer(Wei.class, new CustomBalanceSerializer());
    mapper.registerModule(balanceModule);

    return mapper;
  }

  public String writeValueAsString(Configuration initConfig) throws JsonProcessingException {
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(initConfig);
  }
}

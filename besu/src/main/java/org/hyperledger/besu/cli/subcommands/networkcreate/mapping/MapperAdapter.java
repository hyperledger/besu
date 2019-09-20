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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.apache.tuweni.toml.Toml;
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
public class MapperAdapter {

  Supplier<JsonFactory> supplier;
  InitFileReader initFileReader;

  private MapperAdapter(
      final Supplier<JsonFactory> supplier,
      final InitFileReader initFileReader) {
    this.supplier = supplier;
    this.initFileReader = initFileReader;
  }

  public static MapperAdapter getMapper(final URL initFileURL) {
    switch (Files.getFileExtension(initFileURL.getFile())) {
      case "yaml":
      case "yml":
        return new MapperAdapter(YAMLFactory::new, () -> Resources.toString(initFileURL, UTF_8));
      case "toml":
      case "tml":
        return new MapperAdapter(JsonFactory::new, () -> Toml.parse(Path.of(initFileURL.toURI())).toJson());
      case "json":
        return new MapperAdapter(JsonFactory::new, () -> Resources.toString(initFileURL, UTF_8));
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
    return getMapper(supplier.get()).writerWithDefaultPrettyPrinter().writeValueAsString(initConfig);
  }

  public <T> T map(TypeReference<T> clazz) throws Exception {
    ObjectMapper mapper = getMapper(supplier.get());
    return mapper.readValue(initFileReader.read(), clazz);
  }

  @FunctionalInterface
  interface InitFileReader {
    String read() throws IOException, URISyntaxException;
  }
}

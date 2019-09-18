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

import java.net.URL;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;

class TOMLMapperAdapter extends MapperAdapter {

  private final URL fileURL;

  TOMLMapperAdapter(String initFile) {
    fileURL = this.getClass().getResource(initFile);
  }

  public <T> T map(TypeReference<T> clazz) throws Exception {
    final TomlParseResult result = Toml.parse(Path.of(fileURL.toURI()));
    mapper = getMapper(new JsonFactory());
    return mapper.readValue(result.toJson(), clazz);
  }
}

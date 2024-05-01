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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@JsonSerialize(using = DiffNode.Serializer.class)
public final class DiffNode {

  private final Optional<String> from;
  private final Optional<String> to;

  DiffNode(final String from, final String to) {
    this.from = Optional.ofNullable(from);
    this.to = Optional.ofNullable(to);
  }

  DiffNode(final Optional<String> from, final Optional<String> to) {
    this.from = from;
    this.to = to;
  }

  boolean hasDifference() {
    return from.map(it -> !it.equals(to.get())).orElse(to.isPresent());
  }

  public static class Serializer extends StdSerializer<DiffNode> {

    public Serializer() {
      this(null);
    }

    protected Serializer(final Class<DiffNode> t) {
      super(t);
    }

    @Override
    public void serialize(
        final DiffNode value, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {
      if (value.from.isPresent()) {
        if (value.to.isPresent()) {
          if (value.from.get().equalsIgnoreCase(value.to.get())) {
            gen.writeString("=");
          } else {
            gen.writeStartObject();
            gen.writeObjectFieldStart("*");
            gen.writeObjectField("from", value.from.get());
            gen.writeObjectField("to", value.to.get());
            gen.writeEndObject();
            gen.writeEndObject();
          }
        } else {
          gen.writeStartObject();
          gen.writeObjectField("-", value.from.get());
          gen.writeEndObject();
        }
      } else {
        if (value.to.isPresent()) {
          gen.writeStartObject();
          gen.writeObjectField("+", value.to.get());
          gen.writeEndObject();
        } else {
          gen.writeString("=");
        }
      }
    }
  }
}

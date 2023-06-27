package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.datatypes.Address;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.apache.tuweni.bytes.Bytes;

public class JsonUtils {

  private JsonUtils() {}

  /**
   * Create an object mapper with all the standard bells and whistles needed for evmtool
   *
   * @return a properly constructed ObjectMapper
   */
  public static ObjectMapper createObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();

    // Attempting to get byte-perfect to go's standard json output
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSpacesInObjectEntries()
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  ")));

    // When we stream stdin we cannot close the stream
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);

    // GraalVM has a hard time reflecting these classes for serialization
    SimpleModule serializers = new SimpleModule("Serializers");
    serializers.addSerializer(Address.class, ToStringSerializer.instance);
    serializers.addSerializer(Bytes.class, ToStringSerializer.instance);
    objectMapper.registerModule(serializers);

    return objectMapper;
  }
}

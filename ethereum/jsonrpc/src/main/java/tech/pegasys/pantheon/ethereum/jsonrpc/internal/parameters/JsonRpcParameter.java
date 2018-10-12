package tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonRpcParameter {

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Retrieves a required parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if parameter is missing or of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available, otherwise throws exception.
   */
  public <T> T required(final Object[] params, final int index, final Class<T> paramClass) {
    final Optional<T> optionalParam = optional(params, index, paramClass);
    if (!optionalParam.isPresent()) {
      throw new InvalidJsonRpcParameters("Missing required json rpc parameter at index " + index);
    }

    return optionalParam.get();
  }

  /**
   * Retrieves an optional parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if parameter is of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available.
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> optional(
      final Object[] params, final int index, final Class<T> paramClass) {
    if (params == null || params.length <= index) {
      return Optional.empty();
    }

    final T param;
    final Object rawParam = params[index];
    if (paramClass.isAssignableFrom(rawParam.getClass())) {
      // If we're dealing with a simple type, just cast the value
      param = (T) rawParam;
    } else {
      // Otherwise, serialize param back to json and then deserialize to the paramClass type
      try {
        final String json = mapper.writeValueAsString(rawParam);
        param = mapper.readValue(json, paramClass);
      } catch (final JsonProcessingException e) {
        throw new InvalidJsonRpcParameters("Invalid json rpc parameter at index " + index, e);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return Optional.of(param);
  }
}

package tech.pegasys.pantheon.cli.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.collect.Lists;
import picocli.CommandLine.ITypeConverter;

public class CorsAllowedOriginsProperty {

  private List<String> domains = Collections.emptyList();

  public CorsAllowedOriginsProperty(final List<String> domains) {
    this.domains = domains;
  }

  public CorsAllowedOriginsProperty() {}

  public List<String> getDomains() {
    return domains;
  }

  public static class CorsAllowedOriginsPropertyConverter
      implements ITypeConverter<CorsAllowedOriginsProperty> {

    @Override
    public CorsAllowedOriginsProperty convert(final String value) throws IllegalArgumentException {
      List<String> domains;
      if (value != null && !value.isEmpty()) {
        domains = new ArrayList<>(Arrays.asList(value.split("\\s*,\\s*")));
      } else {
        throw new IllegalArgumentException("Property can't be null/empty string");
      }

      if (domains.contains("none")) {
        if (domains.size() > 1) {
          throw new IllegalArgumentException("Value 'none' can't be used with other domains");
        } else {
          return new CorsAllowedOriginsProperty(Collections.emptyList());
        }
      }

      if (domains.contains("all") || domains.contains("*")) {
        if (domains.size() > 1) {
          throw new IllegalArgumentException("Value 'all' can't be used with other domains");
        } else {
          return new CorsAllowedOriginsProperty(Lists.newArrayList("*"));
        }
      }

      try {
        final StringJoiner stringJoiner = new StringJoiner("|");
        domains.stream().filter(s -> !s.isEmpty()).forEach(stringJoiner::add);
        Pattern.compile(stringJoiner.toString());
      } catch (final PatternSyntaxException e) {
        throw new IllegalArgumentException("Domain values result in invalid regex pattern", e);
      }

      if (domains.size() > 0) {
        return new CorsAllowedOriginsProperty(domains);
      } else {
        return new CorsAllowedOriginsProperty();
      }
    }
  }
}

/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.loopgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

/**
 * What copied code names but EVM cannot see, because the operations declare it private or package
 * private: their result constants and their static helpers. Each is copied into EVM once, as it is,
 * and the copies name the copy.
 */
final class CopiedDeclarations {

  /**
   * What an {@code OperationResult} constant says, read from its {@code new OperationResult(...)}.
   *
   * @param gasCost the gas the operation costs
   * @param pcStep how far the pc moves after it
   * @param halts whether it halts the frame
   */
  record ResultConstant(long gasCost, long pcStep, boolean halts) {}

  private final JavaSources sources;
  private final Map<String, String> constants = new LinkedHashMap<>();
  private final Map<ExecutableElement, String> helperNames = new HashMap<>();
  private final Map<String, String> helpers = new LinkedHashMap<>();

  CopiedDeclarations(final JavaSources sources) {
    this.sources = sources;
  }

  /** The text to write into EVM: the copied constants, then the copied helpers. */
  String text() {
    final StringBuilder text = new StringBuilder();
    constants.values().forEach(constant -> text.append('\n').append(constant));
    helpers.values().forEach(helper -> text.append('\n').append(helper));
    return text.toString();
  }

  // ---------------------------------------------------------------------------------------------
  // Result constants

  static boolean isResultConstant(final Element element) {
    return element instanceof VariableElement field
        && field.asType().toString().endsWith("OperationResult")
        && field.getModifiers().contains(Modifier.STATIC)
        && field.getModifiers().contains(Modifier.FINAL);
  }

  /** What a result constant says, which must be created with literal arguments. */
  ResultConstant read(final VariableElement constant) {
    final List<? extends ExpressionTree> arguments = creation(constant).getArguments();
    if (arguments.size() < 2
        || arguments.size() > 3
        || arguments.get(1).getKind() != Tree.Kind.NULL_LITERAL) {
      return new ResultConstant(0, 0, true);
    }
    final long gasCost = literal(arguments.get(0), constant);
    final long pcStep = arguments.size() == 3 ? literal(arguments.get(2), constant) : 1;
    return new ResultConstant(gasCost, pcStep, false);
  }

  /** The name under which EVM can use a result constant, copying it into EVM if it must. */
  String nameOfResultConstant(final VariableElement constant) {
    final TypeElement owner = (TypeElement) constant.getEnclosingElement();
    if (JavaSources.visibleFromEvm(constant)) {
      return sources.nameInEvm(owner) + "." + constant.getSimpleName();
    }
    final String name =
        upperSnake(owner.getSimpleName().toString()) + "_" + constant.getSimpleName();
    if (!constants.containsKey(name)) {
      final List<String> arguments = new ArrayList<>();
      for (final ExpressionTree argument : creation(constant).getArguments()) {
        arguments.add(argument.toString());
      }
      constants.put(
          name,
          """
          // copied from %s.%s
          private static final OperationResult %s =
              new OperationResult(%s);
          """
              .formatted(
                  owner.getSimpleName(),
                  constant.getSimpleName(),
                  name,
                  String.join(", ", arguments)));
    }
    return name;
  }

  private NewClassTree creation(final VariableElement constant) {
    if (sources.declaration(constant) instanceof VariableTree declaration
        && declaration.getInitializer() instanceof NewClassTree creation
        && creation.getIdentifier().toString().endsWith("OperationResult")) {
      return creation;
    }
    throw new GeneratorException(
        constant.getEnclosingElement() + "." + constant + " is not a new OperationResult(...)");
  }

  private static long literal(final ExpressionTree argument, final VariableElement constant) {
    if (argument instanceof LiteralTree literal && literal.getValue() instanceof Number number) {
      return number.longValue();
    }
    throw new GeneratorException(
        constant.getEnclosingElement()
            + "."
            + constant
            + " has a cost or step that is not a literal");
  }

  /** A number constant EVM cannot name, as its value. */
  static String valueOf(final VariableElement constant, final String location) {
    final Object value = constant.getConstantValue();
    if (value instanceof Integer || value instanceof Long) {
      return value + (value instanceof Long ? "L" : "");
    }
    throw new GeneratorException(
        location + ": " + constant + " is neither visible from EVM nor a number constant");
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers

  /**
   * The name under which EVM can call a static helper the operations declare, copying the helper
   * into EVM if it must.
   */
  String nameOfHelper(final ExecutableElement helper, final TreePath call) {
    final TypeElement owner = (TypeElement) helper.getEnclosingElement();
    if (JavaSources.visibleFromEvm(helper)) {
      return sources.nameInEvm(owner) + "." + helper.getSimpleName();
    }
    final String known = helperNames.get(helper);
    if (known != null) {
      return known;
    }
    final String name =
        lowerFirst(owner.getSimpleName().toString())
            + upperFirst(helper.getSimpleName().toString());
    helperNames.put(helper, name);
    helpers.put(name, copyHelper(helper, name, call));
    return name;
  }

  private String copyHelper(
      final ExecutableElement helper, final String name, final TreePath call) {
    final MethodTree method = (MethodTree) sources.declaration(helper);
    final TreePath methodPath = sources.pathTo(helper);
    if (method == null || method.getBody() == null) {
      throw new GeneratorException(sources.location(call) + ": no source for " + helper);
    }
    final List<String> parameters = new ArrayList<>();
    for (final VariableTree parameter : method.getParameters()) {
      if (parameter.getType().toString().endsWith("MessageFrame")) {
        throw new GeneratorException(
            sources.location(call) + ": the helper " + helper + " takes the frame");
      }
      parameters.add("final " + parameter.getType() + " " + parameter.getName());
    }
    final String body =
        new SourceCopier(sources, this, null)
            .copyHelperBody(new TreePath(methodPath, method.getBody()));
    return """
        // copied from %s.%s
        private static %s %s(%s) %s
        """
        .formatted(
            helper.getEnclosingElement().getSimpleName(),
            helper.getSimpleName(),
            method.getReturnType(),
            name,
            String.join(", ", parameters),
            body);
  }

  private static String upperSnake(final String camel) {
    return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
  }

  private static String lowerFirst(final String name) {
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static String upperFirst(final String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}

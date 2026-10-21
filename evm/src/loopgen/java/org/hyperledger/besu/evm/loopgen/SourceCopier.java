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
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

/**
 * Copies a piece of source as its text, with each part that means something else in EVM's loop
 * replaced by what it has to say there. Everything else is copied character for character, comments
 * and formatting included.
 *
 * <p>What is replaced:
 *
 * <ul>
 *   <li>a call of the frame's methods, as {@link UntracedLoop} lists, while the loop's state is in
 *       its locals;
 *   <li>a variable, by the name or argument its {@link Scope} gives it;
 *   <li>a parameter of {@code executeOperationV2}, by the loop's local of the same meaning;
 *   <li>a field of EVM, by the loop's local that holds it;
 *   <li>a static member of the operations, by its name in EVM, a copy of it, or its value.
 * </ul>
 *
 * Code whose meaning would change, such as a call of an instance method of the operation or the
 * frame passed to a call, is rejected.
 */
final class SourceCopier {

  /** Where the loop's state is while the copied code runs. */
  enum State {
    /** In the loop's locals, where the frame's methods must not see it. */
    IN_LOCALS,
    /** Handed to the frame, where the copied code may use the frame as the operation does. */
    IN_FRAME
  }

  private final JavaSources sources;
  private final CopiedDeclarations declarations;
  private final CaseLocals locals;

  /**
   * A copier for one case, or for a helper when {@code locals} is null.
   *
   * @param sources the sources
   * @param declarations where to copy what EVM cannot see
   * @param locals the case's locals, or null for a helper, which has no access to the frame
   */
  SourceCopier(
      final JavaSources sources, final CopiedDeclarations declarations, final CaseLocals locals) {
    this.sources = sources;
    this.declarations = declarations;
    this.locals = locals;
  }

  /**
   * The copy of the code at the path.
   *
   * @param code the code
   * @param scope what its variables stand for
   * @param state where the loop's state is while it runs
   * @return its copy
   */
  String copy(final TreePath code, final Scope scope, final State state) {
    final String whole = replacement(code, scope, state);
    if (whole != null) {
      return whole;
    }
    // the text of the code, with each part that has a replacement replaced, from the last part
    // backwards so that the positions of the earlier ones stay valid
    final int start = sources.start(code);
    final List<Replacement> replacements = new ArrayList<>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void scan(final Tree tree, final Void unused) {
        if (tree == null) {
          return null;
        }
        final TreePath part = new TreePath(getCurrentPath(), tree);
        final String replaced = replacement(part, scope, state);
        if (replaced == null) {
          return super.scan(tree, unused);
        }
        replacements.add(
            new Replacement(sources.start(part) - start, sources.end(part) - start, replaced));
        return null;
      }
    }.scan(code, null);
    final StringBuilder text = new StringBuilder(sources.text(code));
    for (final Replacement replacement : replacements.reversed()) {
      text.replace(replacement.start(), replacement.end(), replacement.text());
    }
    return text.toString();
  }

  private record Replacement(int start, int end, String text) {}

  /**
   * The copy of a helper's body: its own parameters and locals keep their names, and only what EVM
   * cannot see changes.
   */
  String copyHelperBody(final TreePath body) {
    final Scope scope = new Scope(null);
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitVariable(final VariableTree variable, final Void unused) {
        scope.name(sources.element(getCurrentPath()), variable.getName().toString());
        return super.visitVariable(variable, unused);
      }

      @Override
      public Void scan(final Tree tree, final Void unused) {
        if (tree != null) {
          rejectIfUnsupported(new TreePath(getCurrentPath(), tree));
        }
        return super.scan(tree, unused);
      }
    }.scan(body.getParentPath(), null);
    return copy(body, scope, State.IN_FRAME);
  }

  /**
   * Rejects what the loop cannot hold: a loop of its own, whose head C2 would make an entry point
   * of the compiled loop, and what calls or allocates on every run.
   */
  void rejectIfUnsupported(final TreePath code) {
    switch (code.getLeaf().getKind()) {
      case FOR_LOOP,
          ENHANCED_FOR_LOOP,
          WHILE_LOOP,
          DO_WHILE_LOOP,
          TRY,
          THROW,
          SYNCHRONIZED,
          LAMBDA_EXPRESSION,
          MEMBER_REFERENCE,
          SWITCH,
          SWITCH_EXPRESSION ->
          throw reject(code, "a " + code.getLeaf().getKind());
      default -> {}
    }
  }

  // ---------------------------------------------------------------------------------------------
  // What a part of the code is replaced by

  /** The copy of a part of the code that means something else in the loop, or null. */
  private String replacement(final TreePath part, final Scope scope, final State state) {
    rejectIfUnsupported(part);
    final Tree leaf = part.getLeaf();
    if (leaf instanceof MethodInvocationTree call) {
      return call(part, call, scope, state);
    }
    if (leaf instanceof IdentifierTree identifier) {
      return name(part, identifier, scope, state);
    }
    if (leaf instanceof MemberSelectTree
        && sources.element(part) instanceof VariableElement field
        && field.getKind() == ElementKind.FIELD
        && field.getModifiers().contains(Modifier.STATIC)
        && !sources.isInEvm(part)) {
      return staticField(field, part);
    }
    return null;
  }

  private String call(
      final TreePath part, final MethodInvocationTree call, final Scope scope, final State state) {
    if (!(sources.element(part) instanceof ExecutableElement method)) {
      throw reject(part, "a call javac did not resolve");
    }
    final List<String> arguments = new ArrayList<>();
    for (final ExpressionTree argument : call.getArguments()) {
      final TreePath argumentPath = new TreePath(part, argument);
      if (state == State.IN_LOCALS && isFrame(argumentPath, scope)) {
        throw reject(part, "the frame passed to " + method.getSimpleName());
      }
      arguments.add(copy(argumentPath, scope, state));
    }
    final String name = method.getSimpleName().toString();
    final ExpressionTree select = call.getMethodSelect();

    if (select instanceof MemberSelectTree member
        && isFrame(new TreePath(part, member.getExpression()), scope)) {
      if (state == State.IN_FRAME) {
        return "frame." + name + "(" + String.join(", ", arguments) + ")";
      }
      final String local = UntracedLoop.frameCall(name, arguments, locals);
      if (local == null) {
        throw reject(part, "the frame's " + name + "()");
      }
      return local;
    }
    if (method.getModifiers().contains(Modifier.STATIC)) {
      return declarations.nameOfHelper(method, part) + "(" + String.join(", ", arguments) + ")";
    }
    if (select instanceof MemberSelectTree member) {
      return copy(new TreePath(part, member.getExpression()), scope, state)
          + "."
          + member.getIdentifier()
          + "("
          + String.join(", ", arguments)
          + ")";
    }
    throw reject(part, "a call of an instance method of the operation");
  }

  private String name(
      final TreePath part, final IdentifierTree identifier, final Scope scope, final State state) {
    if (part.getParentPath().getLeaf() instanceof MethodInvocationTree call
        && call.getMethodSelect() == identifier) {
      // the name of a called method, which call() has replaced with the whole call
      return null;
    }
    final Element element = sources.element(part);
    if (element == null) {
      return null;
    }
    if (isFrame(element, scope)) {
      if (state == State.IN_FRAME) {
        return "frame";
      }
      throw reject(part, "the frame used other than through its stack, pc and gas");
    }

    final String declared = scope.nameOf(element);
    if (declared != null) {
      return declared;
    }
    final Scope.Binding binding = scope.bindingOf(element);
    if (binding != null) {
      return parenthesized(copy(binding.argument(), binding.scope(), state));
    }
    if (sources.isExecuteOperationV2Parameter(element)) {
      return switch (element.getSimpleName().toString()) {
        case "pc" -> locals.pc();
        case "opcode" -> "opcode";
        case "code" -> "code";
        default -> throw reject(part, "executeOperationV2's parameter " + element);
      };
    }
    if (element.getKind() == ElementKind.FIELD) {
      return field((VariableElement) element, part);
    }
    if (element.getKind() == ElementKind.LOCAL_VARIABLE
        || element.getKind() == ElementKind.PARAMETER) {
      throw reject(part, "a variable of the operation the copy did not declare: " + element);
    }
    return null;
  }

  private String field(final VariableElement field, final TreePath part) {
    if (field.getSimpleName().contentEquals("this")) {
      throw reject(part, "a use of EVM itself (this)");
    }
    if (sources.isEvmInstanceField(field)) {
      final String local = UntracedLoop.EVM_FIELDS.get(field.getSimpleName().toString());
      if (local == null) {
        throw reject(part, "EVM's field " + field.getSimpleName());
      }
      return local;
    }
    if (!field.getModifiers().contains(Modifier.STATIC)) {
      throw reject(part, "an instance field");
    }
    return sources.isInEvm(part) ? null : staticField(field, part);
  }

  private String staticField(final VariableElement field, final TreePath part) {
    return JavaSources.visibleFromEvm(field)
        ? sources.nameInEvm((TypeElement) field.getEnclosingElement()) + "." + field.getSimpleName()
        : CopiedDeclarations.valueOf(field, sources.location(part));
  }

  // ---------------------------------------------------------------------------------------------
  // The frame

  boolean isFrame(final TreePath code, final Scope scope) {
    if (!(code.getLeaf() instanceof IdentifierTree)) {
      return false;
    }
    final Element element = sources.element(code);
    return element != null && isFrame(element, scope);
  }

  private boolean isFrame(final Element variable, final Scope scope) {
    return sources.isExecuteOperationV2Parameter(variable, "frame") || scope.isFrame(variable);
  }

  // ---------------------------------------------------------------------------------------------

  /** The text in parentheses, unless it is a name or already one parenthesized expression. */
  static String parenthesized(final String text) {
    if (text.matches("[\\w.$]+") || (text.matches("\\(.*\\)") && closesAtTheEnd(text))) {
      return text;
    }
    return "(" + text + ")";
  }

  /** Whether the parenthesis the text starts with is the one it ends with. */
  private static boolean closesAtTheEnd(final String text) {
    int depth = 0;
    for (int i = 0; i < text.length(); i++) {
      depth += text.charAt(i) == '(' ? 1 : text.charAt(i) == ')' ? -1 : 0;
      if (depth == 0 && i < text.length() - 1) {
        return false;
      }
    }
    return true;
  }

  GeneratorException reject(final TreePath code, final String what) {
    return new GeneratorException(sources.location(code) + ": cannot copy " + what);
  }
}

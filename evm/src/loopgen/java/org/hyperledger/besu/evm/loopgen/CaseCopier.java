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

import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

/**
 * Copies what one case of {@code executeOperationV2}'s switch runs into one case of the loop's
 * switch.
 *
 * <p>The general path's case is an expression the general path returns, such as {@code
 * PopOperationV2.staticOperation(frame)}. The copy is the code that returning it runs, statement by
 * statement. A {@code return} is what changes most, as the copy runs inside the loop and returns
 * nothing; it becomes one of:
 *
 * <ul>
 *   <li>for a result constant that succeeds, its gas cost and pc step, assigned to {@code cost} and
 *       {@code step} for the loop to apply;
 *   <li>for a call of an operation's static method, the copy of that method's body;
 *   <li>for anything else, the loop's state handed to the frame and the value assigned to {@code
 *       result}, which the loop completes as the general path would.
 * </ul>
 *
 * A {@code return} before the end of the case also leaves the case, with {@code break}.
 */
final class CaseCopier {

  private final JavaSources sources;
  private final CopiedDeclarations declarations;
  private final CaseLocals locals;
  private final SourceCopier copier;

  /**
   * A copier for the case with the given index in the loop's switch.
   *
   * @param sources the sources
   * @param declarations where to copy what EVM cannot see
   * @param caseIndex the index, from 1
   */
  CaseCopier(
      final JavaSources sources, final CopiedDeclarations declarations, final int caseIndex) {
    this.sources = sources;
    this.declarations = declarations;
    this.locals = new CaseLocals(caseIndex);
    this.copier = new SourceCopier(sources, declarations, locals);
  }

  /**
   * The copy of what a case of {@code executeOperationV2}'s switch runs.
   *
   * @param generalPathCase the case
   * @return the statements of the loop's case
   */
  String copy(final TreePath generalPathCase) {
    final TreePath returned =
        new TreePath(generalPathCase, ((CaseTree) generalPathCase.getLeaf()).getBody());
    return copyReturn(returned, new Scope(null), true, false);
  }

  /** What the copy says it was copied from: the method the general path's case calls. */
  static String describe(final TreePath generalPathCase) {
    Tree returned = ((CaseTree) generalPathCase.getLeaf()).getBody();
    if (returned instanceof ConditionalExpressionTree conditional) {
      // a fork flag chooses between the operation and an invalid opcode
      returned = conditional.getTrueExpression();
    }
    if (returned instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree member) {
      return member.getExpression() + "." + member.getIdentifier();
    }
    return returned.toString();
  }

  // ---------------------------------------------------------------------------------------------
  // Returns

  /**
   * The copy of {@code return value;}.
   *
   * @param value the returned value
   * @param scope what its variables stand for
   * @param endsCase whether nothing follows it in the case, so that it needs no {@code break}
   * @param asBlock whether a method body copied in place of the return gets braces of its own,
   *     which it needs everywhere but as the whole case
   */
  private String copyReturn(
      final TreePath value, final Scope scope, final boolean endsCase, final boolean asBlock) {
    final Tree leaf = value.getLeaf();
    final String leaveCase = endsCase ? "" : "\nbreak;";

    if (leaf instanceof ParenthesizedTree parenthesized) {
      return copyReturn(
          new TreePath(value, parenthesized.getExpression()), scope, endsCase, asBlock);
    }
    if (leaf instanceof ConditionalExpressionTree conditional) {
      return copyConditionalReturn(value, conditional, scope, endsCase);
    }
    final Element element = sources.element(value);
    if ((leaf instanceof IdentifierTree || leaf instanceof MemberSelectTree)
        && CopiedDeclarations.isResultConstant(element)) {
      final VariableElement constant = (VariableElement) element;
      final CopiedDeclarations.ResultConstant result = declarations.read(constant);
      if (!result.halts()) {
        return "cost = " + result.gasCost() + "L;\nstep = " + result.pcStep() + ";" + leaveCase;
      }
      if (!JavaSources.visibleFromEvm(constant)) {
        throw copier.reject(value, "a halt EVM cannot name: " + constant);
      }
      return handToFrame(
          sources.nameInEvm((TypeElement) constant.getEnclosingElement())
              + "."
              + constant.getSimpleName(),
          leaveCase);
    }
    if (leaf instanceof MethodInvocationTree call
        && element instanceof ExecutableElement method
        && method.getModifiers().contains(Modifier.STATIC)
        && isOperationsCode(method)) {
      return copyCalledMethod(value, call, method, scope, endsCase, asBlock);
    }
    return handToFrame(copier.copy(value, scope, SourceCopier.State.IN_FRAME), leaveCase);
  }

  private static boolean isOperationsCode(final ExecutableElement method) {
    return method.getEnclosingElement() instanceof TypeElement owner
        && owner.getQualifiedName().toString().startsWith(JavaSources.OPERATIONS_PACKAGE + ".");
  }

  /** Hands the loop's state to the frame and leaves the result for the loop to complete. */
  private static String handToFrame(final String result, final String leaveCase) {
    return UntracedLoop.HAND_STATE_TO_FRAME + "\nresult = " + result + ";" + leaveCase;
  }

  /** {@code return condition ? a : b;} as an {@code if} that returns either. */
  private String copyConditionalReturn(
      final TreePath value,
      final ConditionalExpressionTree conditional,
      final Scope scope,
      final boolean endsCase) {
    final String condition =
        copier.copy(
            new TreePath(value, conditional.getCondition()), scope, SourceCopier.State.IN_LOCALS);
    // both branches return, so neither's assignments reach code after the if
    final CaseLocals.Assigned before = locals.assigned();
    final String whenTrue =
        copyReturn(new TreePath(value, conditional.getTrueExpression()), scope, endsCase, true);
    locals.restore(before);
    final String whenFalse =
        copyReturn(new TreePath(value, conditional.getFalseExpression()), scope, endsCase, true);
    locals.restore(before);
    return "if (" + condition + ") {\n" + whenTrue + "\n} else {\n" + whenFalse + "\n}";
  }

  /**
   * The body of a static method of the operations, copied in place of the call that returns its
   * result.
   *
   * <p>Each parameter is bound to the call's argument: a name for the frame stays one, an argument
   * without side effects is repeated where the parameter is used, and any other argument is
   * evaluated once into a local of the parameter's name.
   */
  private String copyCalledMethod(
      final TreePath callPath,
      final MethodInvocationTree call,
      final ExecutableElement method,
      final Scope scope,
      final boolean endsCase,
      final boolean asBlock) {
    final MethodTree declaration = (MethodTree) sources.declaration(method);
    final TreePath declarationPath = sources.pathTo(method);
    if (declaration == null || declarationPath == null || declaration.getBody() == null) {
      throw new GeneratorException("no source for " + method);
    }
    final Scope methodScope = new Scope(scope);
    final StringBuilder copy = new StringBuilder();
    for (int i = 0; i < declaration.getParameters().size(); i++) {
      final VariableTree parameter = declaration.getParameters().get(i);
      final Element parameterElement = sources.element(new TreePath(declarationPath, parameter));
      final TreePath argument = new TreePath(callPath, call.getArguments().get(i));
      if (copier.isFrame(argument, scope)) {
        methodScope.nameFrame(parameterElement);
      } else if (hasNoSideEffects(argument.getLeaf())) {
        methodScope.bind(parameterElement, argument, scope);
      } else {
        final String name = locals.declare(parameter.getName().toString());
        methodScope.name(parameterElement, name);
        // not final when it holds a literal, so that javac and Error Prone do not reject the
        // conditions on it as constant; C2 folds them
        copy.append(argument.getLeaf() instanceof LiteralTree ? "" : "final ")
            .append(parameter.getType())
            .append(' ')
            .append(name)
            .append(" = ")
            .append(copier.copy(argument, scope, SourceCopier.State.IN_LOCALS))
            .append(";\n");
      }
    }
    final BlockTree body = declaration.getBody();
    copy.append(
        copyStatements(
            new TreePath(declarationPath, body), body.getStatements(), methodScope, endsCase));
    return asBlock ? "{\n" + copy + "\n}" : copy.toString().strip();
  }

  /**
   * An argument the copy may repeat where its parameter is used: a name, a field, or the frame's
   * stack array.
   */
  private static boolean hasNoSideEffects(final Tree argument) {
    return argument instanceof IdentifierTree
        || (argument instanceof MemberSelectTree member
            && member.getExpression() instanceof IdentifierTree)
        || (argument instanceof MethodInvocationTree call
            && call.getArguments().isEmpty()
            && call.getMethodSelect() instanceof MemberSelectTree member
            && member.getIdentifier().contentEquals("stackDataV2"));
  }

  // ---------------------------------------------------------------------------------------------
  // Statements

  private String copyStatements(
      final TreePath block,
      final List<? extends StatementTree> statements,
      final Scope scope,
      final boolean endsCase) {
    final StringBuilder copy = new StringBuilder();
    for (int i = 0; i < statements.size(); i++) {
      final boolean last = i == statements.size() - 1;
      copy.append(copyStatement(new TreePath(block, statements.get(i)), scope, endsCase && last));
      copy.append('\n');
    }
    return copy.toString();
  }

  private String copyStatement(
      final TreePath statement, final Scope scope, final boolean endsCase) {
    final Tree leaf = statement.getLeaf();
    if (leaf instanceof ReturnTree returnStatement) {
      return copyReturn(
          new TreePath(statement, returnStatement.getExpression()), scope, endsCase, true);
    }
    if (leaf instanceof BlockTree block) {
      return "{\n" + copyStatements(statement, block.getStatements(), scope, endsCase) + "}";
    }
    if (leaf instanceof IfTree ifStatement) {
      return copyIf(statement, ifStatement, scope, endsCase);
    }
    if (leaf instanceof ExpressionStatementTree expression) {
      return copier.copy(
              new TreePath(statement, expression.getExpression()),
              scope,
              SourceCopier.State.IN_LOCALS)
          + ";";
    }
    if (leaf instanceof VariableTree variable) {
      final String name = locals.declare(variable.getName().toString());
      scope.name(sources.element(statement), name);
      final boolean isFinal = variable.getModifiers().getFlags().contains(Modifier.FINAL);
      final ExpressionTree initializer = variable.getInitializer();
      return (isFinal ? "final " : "")
          + variable.getType()
          + " "
          + name
          + (initializer == null
              ? ""
              : " = "
                  + copier.copy(
                      new TreePath(statement, initializer), scope, SourceCopier.State.IN_LOCALS))
          + ";";
    }
    throw copier.reject(statement, "a " + leaf.getKind() + " statement");
  }

  private String copyIf(
      final TreePath statement,
      final IfTree ifStatement,
      final Scope scope,
      final boolean endsCase) {
    final StringBuilder copy = new StringBuilder("if ");
    copy.append(
            copier.copy(
                new TreePath(statement, ifStatement.getCondition()),
                scope,
                SourceCopier.State.IN_LOCALS))
        .append(' ');

    // a branch that returns leaves the case, so only the other one's assignments reach the code
    // after the if
    final CaseLocals.Assigned before = locals.assigned();
    copy.append(braced(new TreePath(statement, ifStatement.getThenStatement()), scope, endsCase));
    final CaseLocals.Assigned afterThen =
        returns(ifStatement.getThenStatement()) ? before : locals.assigned();
    locals.restore(before);
    CaseLocals.Assigned afterElse = before;
    if (ifStatement.getElseStatement() != null) {
      copy.append(" else ")
          .append(braced(new TreePath(statement, ifStatement.getElseStatement()), scope, endsCase));
      if (!returns(ifStatement.getElseStatement())) {
        afterElse = locals.assigned();
      }
    }
    locals.restore(afterThen.either(afterElse));
    return copy.toString();
  }

  private String braced(final TreePath statement, final Scope scope, final boolean endsCase) {
    final String copy = copyStatement(statement, scope, endsCase);
    return statement.getLeaf() instanceof BlockTree ? copy : "{\n" + copy + "\n}";
  }

  /** Whether the statement always ends in a {@code return}. */
  private static boolean returns(final StatementTree statement) {
    if (statement instanceof ReturnTree) {
      return true;
    }
    if (statement instanceof BlockTree block) {
      return !block.getStatements().isEmpty() && returns(block.getStatements().getLast());
    }
    if (statement instanceof IfTree ifStatement) {
      return ifStatement.getElseStatement() != null
          && returns(ifStatement.getThenStatement())
          && returns(ifStatement.getElseStatement());
    }
    return false;
  }
}

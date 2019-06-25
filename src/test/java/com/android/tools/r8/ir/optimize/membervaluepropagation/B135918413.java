// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B135918413 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public B135918413(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B135918413.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject mainMethodSubject = classSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    // TODO(b/135918413): Should be true.
    assertFalse(
        mainMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .allMatch(instruction -> instruction.getField().name.toSourceString().equals("out")));

    MethodSubject deadMethodSubject = classSubject.uniqueMethodWithName("dead");
    // TODO(b/135918413): Should be absent.
    assertThat(deadMethodSubject, isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      if (Config.alwaysTrue) {
        System.out.print("Hello");
      } else {
        dead();
      }
      if (Config.alwaysEmpty.length == 0) {
        System.out.println(" world!");
      }
    }

    @NeverInline
    static void dead() {
      System.out.println("Unexpected");
    }
  }

  static class Config {

    public static boolean alwaysTrue = true;
    public static String[] alwaysEmpty = {};
  }
}

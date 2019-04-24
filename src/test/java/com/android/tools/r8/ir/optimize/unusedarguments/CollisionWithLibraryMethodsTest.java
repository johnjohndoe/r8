// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollisionWithLibraryMethodsTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] params() {
    return Backend.values();
  }

  public CollisionWithLibraryMethodsTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    testForR8(backend)
        .addInnerClasses(CollisionWithLibraryMethodsTest.class)
        .addKeepMainRule(TestClass.class)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .compile()
        .inspect(this::verify)
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verify(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Greeting.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.uniqueMethodWithName("toString");
    assertThat(methodSubject, isPresent());
    assertEquals("java.lang.Object", methodSubject.getMethod().method.proto.parameters.toString());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Greeting().toString(null));
    }
  }

  @NeverClassInline
  static class Greeting {

    @NeverInline
    public String toString(Object unused) {
      return "Hello world!";
    }
  }
}

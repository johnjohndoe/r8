// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringConcatenationTest extends TestBase {
  private static final Class<?> MAIN = StringConcatenationTestClass.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "xyz",
      "Hello,R8",
      "42",
      "42",
      "0.14 0 false null",
      "Hello,R8",
      "Hello,R8",
      "Hello,",
      "Hello,D8",
      "Hello,R8",
      "Hello,R8",
      "na;na;na;na;na;na;na;na;Batman!",
      "na;na;na;na;na;na;na;na;Batman!"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public StringConcatenationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(
      TestRunResult result,
      int expectedStringCountInTrivialSequence,
      int expectedStringCountInBuilderWithInitialValue,
      int expectedStringCountInBuilderWithCapacity,
      int expectedStringCountInNonStringArgs,
      int expectedStringCountInTypeConversion,
      int expectedStringCountInNestedBuilderAppendItself,
      int expectedStringCountInNestedBuilderAppendResult)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    MethodSubject method = mainClass.uniqueMethodWithName("trivialSequence");
    assertThat(method, isPresent());
    long count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInTrivialSequence, count);

    method = mainClass.uniqueMethodWithName("builderWithInitialValue");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInBuilderWithInitialValue, count);

    method = mainClass.uniqueMethodWithName("builderWithCapacity");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInBuilderWithCapacity, count);

    method = mainClass.uniqueMethodWithName("nonStringArgs");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInNonStringArgs, count);

    method = mainClass.uniqueMethodWithName("typeConversion");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInTypeConversion, count);

    method = mainClass.uniqueMethodWithName("nestedBuilders_appendBuilderItself");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInNestedBuilderAppendItself, count);

    method = mainClass.uniqueMethodWithName("nestedBuilders_appendBuilderResult");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringCountInNestedBuilderAppendResult, count);

    method = mainClass.uniqueMethodWithName("simplePhi");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(4, count);

    method = mainClass.uniqueMethodWithName("phiAtInit");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(3, count);

    method = mainClass.uniqueMethodWithName("phiWithDifferentInits");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(3, count);

    method = mainClass.uniqueMethodWithName("loop");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(3, count);

    method = mainClass.uniqueMethodWithName("loopWithBuilder");
    assertThat(method, isPresent());
    count = Streams.stream(method.iterateInstructions(
        i -> i.isConstString(JumboStringMode.ALLOW))).count();
    assertEquals(2, count);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3, 3, 0, 0, 0, 3, 3);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // TODO(b/114002137): The lack of subtyping made the escape analysis to regard
    //    StringBuilder#toString as an alias-introducing instruction.
    test(result, 3, 3, 0, 0, 0, 3, 3);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue("CF does not rewrite move results.", parameters.isDexRuntime());

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .noMinification()
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1, 1, 1, 1, 1, 3, 3);
  }

  // TODO(b/114002137): Once enabled, remove this test-specific setting.
  private void configure(InternalOptions options) {
    assert !options.enableStringConcatenationOptimization;
    options.enableStringConcatenationOptimization = true;
  }

}

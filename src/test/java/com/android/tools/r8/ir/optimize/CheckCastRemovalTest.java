// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.code.AgetObject;
import com.android.tools.r8.code.AputObject;
import com.android.tools.r8.code.CheckCast;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.NewArray;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public class CheckCastRemovalTest extends JasminTestBase {
  private final String CLASS_NAME = "Example";

  @Test
  @Ignore("b/72930905")
  public void exactMatch() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new Example",
        "dup",
        "invokespecial Example/<init>()V",
        "checkcast Example", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(method.getCode().asDexCode(), ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        ReturnVoid.class));
  }

  @Test
  @Ignore("b/72930905")
  public void upCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // A < B < C
    builder.addClass("C");
    builder.addClass("B", "C");
    builder.addClass("A", "B");
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Gone
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    checkInstructions(method.getCode().asDexCode(), ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        ReturnVoid.class));
  }

  @Test
  @Ignore("b/72930905")
  public void downCasts() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    // C < B < A
    builder.addClass("A");
    builder.addClass("B", "A");
    builder.addClass("C", "B");
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "new A",
        "dup",
        "invokespecial A/<init>()V",
        "checkcast B", // Gone
        "checkcast C", // Should be kept
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-keep class A { *; }",
        "-keep class B { *; }",
        "-keep class C { *; }",
        "-dontoptimize",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        NewInstance.class,
        InvokeDirect.class,
        CheckCast.class,
        ReturnVoid.class));
    CheckCast cast = (CheckCast) code.instructions[2];
    assertEquals("C", cast.getType().toString());
  }

  @Test
  public void b72930905() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder classBuilder = builder.addClass(CLASS_NAME);
    MethodSignature main = classBuilder.addMainMethod(
        ".limit stack 4",
        ".limit locals 1",
        "iconst_1",
        "anewarray java/lang/String", // args parameter
        "dup",
        "iconst_0",
        "ldc \"a string\"",
        "aastore",
        "checkcast [Ljava/lang/Object;",
        "iconst_0",
        "aaload",
        "checkcast java/lang/String", // the cast must remain for ART
        "invokevirtual java/lang/String/length()I",
        "return");

    List<String> pgConfigs = ImmutableList.of(
        "-keep class " + CLASS_NAME + " { *; }",
        "-dontshrink");
    AndroidApp app = compileWithR8(builder, pgConfigs, null);

    checkRuntime(builder, app, CLASS_NAME);

    DexEncodedMethod method = getMethod(app, CLASS_NAME, main);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        Const4.class,
        NewArray.class,
        ConstString.class,
        Const4.class,
        AputObject.class,
        CheckCast.class,
        AgetObject.class,
        CheckCast.class,
        InvokeVirtual.class,
        ReturnVoid.class));
  }

  private void checkRuntime(JasminBuilder builder, AndroidApp app, String className)
      throws Exception {
    String normalOutput = runOnJava(builder, className);
    String dexOptimizedOutput = runOnArt(app, className);
    assertEquals(normalOutput, dexOptimizedOutput);
  }
}

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers.b115867670;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@interface JsonClass {}

@JsonClass
class Foo {

  final Interaction[] interactions;

  public Foo(Interaction[] interactions) {
    this.interactions = interactions;
  }

  @JsonClass
  static class Interaction {
    final Request request;

    Interaction(Request request) {
      this.request = request;
    }
  }

  @JsonClass
  static class Request {
    final String path;

    Request(String path) {
      this.path = path;
    }
  }
}

class Main {

  public static void main(String[] args) {
    System.out.println(Foo.class);
  }
}

@RunWith(Parameterized.class)
public class B115867670 extends ProguardCompatibilityTestBase {
  private final String pkg = getClass().getPackage().getName();
  private final Shrinker shrinker;
  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          JsonClass.class, Foo.class, Foo.Interaction.class, Foo.Request.class, Main.class);

  @Parameters(name = "shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(
        Shrinker.PROGUARD6, Shrinker.R8_COMPAT, Shrinker.R8_COMPAT_CF, Shrinker.R8, Shrinker.R8_CF);
  }

  public B115867670(Shrinker shrinker) {
    this.shrinker = shrinker;
  }

  public void runTest(String additionalKeepRules, Consumer<CodeInspector> inspection)
      throws Exception {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder
        .add("-keep @interface **.JsonClass")
        .add("-keep class **.Main {")
        .add("  public static void main(java.lang.String[]);")
        .add("}")
        .add(additionalKeepRules);
    CodeInspector inspector = inspectAfterShrinking(shrinker, CLASSES, builder.build());
    inspection.accept(inspector);
  }

  private void checkNoKeepClassMembers(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(Foo.class), isPresent());
    assertThat(inspector.clazz(Foo.Interaction.class), not(isPresent()));
    assertThat(inspector.clazz(Foo.Request.class), not(isPresent()));
  }

  private void checkKeepClassMembers(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    for (Class clazz : new Class[] {Foo.class, Foo.Interaction.class, Foo.Request.class}) {
      ClassSubject cls = inspector.clazz(clazz);
      assertThat(cls, isPresent());
      assertFalse("Class " + clazz.getSimpleName() + " should not be abstract", cls.isAbstract());
      assertEquals(1, cls.asFoundClassSubject().allFields().size());
      cls.forAllFields(field -> assertThat(field, not(isRenamed())));
    }
  }

  private void checkKeepClassMembersRenamed(CodeInspector inspector) {
    assertThat(inspector.clazz(Main.class), isPresent());
    for (Class clazz : new Class[] {Foo.class, Foo.Interaction.class, Foo.Request.class}) {
      ClassSubject cls = inspector.clazz(clazz);
      assertThat(cls, isPresent());
      assertThat(cls, isRenamed());
      assertFalse("Class " + clazz.getSimpleName() + " should not be abstract", cls.isAbstract());
      assertEquals(1, cls.asFoundClassSubject().allFields().size());
      cls.forAllFields(field -> assertThat(field, isRenamed()));
    }
  }

  @Test
  public void testNoDependentRules() throws Exception {
    runTest("", this::checkNoKeepClassMembers);
  }

  @Test
  public void testDependentWithKeepClass() throws Exception {
    runTest(
        "-keep @" + pkg + ".JsonClass class ** { <fields>; }",
        this::checkKeepClassMembers);
  }

  @Test
  public void testDependentWithKeepClassAllowObfuscation() throws Exception {
    runTest(
        "-keep,allowobfuscation @" + pkg + ".JsonClass class ** { <fields>; }",
        this::checkKeepClassMembersRenamed);
  }

  @Test
  public void testDependentWithKeepClassMembers() throws Exception {
    runTest(
        // In full mode, keepclassmembers does not imply the class is instantiated.
        addFullModeKeepClassIfReferenced(
            "-keepclassmembers @" + pkg + ".JsonClass class ** { <fields>; }"),
        this::checkKeepClassMembers);
  }

  @Test
  public void testDependentWithKeepClassMembersAllowObfuscation() throws Exception {
    runTest(
        // In full mode, keepclassmembers does not imply the class is instantiated.
        addFullModeKeepClassIfReferenced(
            "-keepclassmembers,allowobfuscation @" + pkg + ".JsonClass class ** { <fields>; }"),
        this::checkKeepClassMembersRenamed);
  }

  @Test
  public void testDependentWithIfKeepClassMembers() throws Exception {
    runTest(
        // In full mode, keepclassmembers does not imply the class is instantiated.
        addFullModeKeepClassIfReferenced(
            "-if @" + pkg + ".JsonClass class * -keepclassmembers class <1> { <fields>; }"),
        this::checkKeepClassMembers);
  }

  @Test
  public void testDependentWithIfKeepClassMembersAllowObfuscation() throws Exception {
    runTest(
        // In full mode, keepclassmembers does not imply the class is instantiated.
        addFullModeKeepClassIfReferenced(
        "-if @"
            + pkg
            + ".JsonClass class * -keepclassmembers,allowobfuscation class <1> { <fields>; }"),
        this::checkKeepClassMembersRenamed);
  }

  @Test
  public void testDependentWithIfKeep() throws Exception {
    runTest(
        addFullModeKeepAllAttributes(
            "-if @" + pkg + ".JsonClass class * -keep class <1> { <fields>; }"),
        this::checkKeepClassMembers);
  }

  @Test
  public void testDependentWithIfKeepAllowObfuscation() throws Exception {
    runTest(
        addFullModeKeepAllAttributes(
            "-if @" + pkg + ".JsonClass class * -keep,allowobfuscation class <1> { <fields>; }"),
        this::checkKeepClassMembersRenamed);
  }

  // In full mode we need to explicitly keep the class (mark instantiated) if it is referenced.
  String addFullModeKeepClassIfReferenced(String rules) {
    return shrinker.isFullModeR8()
        ? addFullModeKeepAllAttributes(rules
            + "\n-if @"
            + JsonClass.class.getTypeName()
            + " class * -keep,allowobfuscation class <1>")
        : rules;
  }

  // TODO(b/132318609): Keep the attributes. Potentially remove this once resolved.
  String addFullModeKeepAllAttributes(String rules) {
    return shrinker.isFullModeR8()
        ? rules + "\n-keepattributes *"
        : rules;
  }
}

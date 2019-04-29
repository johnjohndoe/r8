// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldNamingObfuscationDictionaryTest extends TestBase {

  public static class A {

    public String f1;

    public A(String a) {
      this.f1 = a;
    }
  }

  public static class B extends A {

    public int f0;
    public String f2;

    public B(int f0, String a, String b) {
      super(a);
      this.f0 = f0;
      this.f2 = b;
    }

    @NeverInline
    public void print() {
      System.out.println(f0 + f1 + " " + f2);
    }
  }

  public static class C extends A {

    public int f0;
    public String f3;

    public C(int f0, String a, String c) {
      super(a);
      this.f0 = f0;
      this.f3 = c;
    }

    @NeverInline
    public void print() {
      System.out.println(f0 + f1 + " " + f3);
    }
  }

  public static class Runner {

    public static void main(String[] args) {
      String arg1 = args.length == 0 ? "HELLO" : args[0];
      String arg2 = args.length == 0 ? "WORLD" : args[1];
      new B(args.length, arg1, arg2).print();
      new C(args.length, arg1, arg2).print();
    }
  }

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public FieldNamingObfuscationDictionaryTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testInheritedNamingState()
      throws IOException, CompilationFailedException, ExecutionException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a", "b", "c");

    testForR8(backend)
        .addInnerClasses(FieldNamingObfuscationDictionaryTest.class)
        .enableInliningAnnotations()
        .addKeepRules("-overloadaggressively", "-obfuscationdictionary " + dictionary.toString())
        .addKeepRules()
        .addKeepMainRule(Runner.class)
        .compile()
        .run(Runner.class)
        .assertSuccessWithOutput(StringUtils.lines("0HELLO WORLD", "0HELLO WORLD"))
        .inspect(
            inspector -> {
              assertEquals("a", inspector.clazz(A.class).uniqueFieldWithName("f1").getFinalName());
              assertEquals("a", inspector.clazz(B.class).uniqueFieldWithName("f0").getFinalName());
              assertEquals("b", inspector.clazz(B.class).uniqueFieldWithName("f2").getFinalName());
              assertEquals("a", inspector.clazz(C.class).uniqueFieldWithName("f0").getFinalName());
              assertEquals("b", inspector.clazz(C.class).uniqueFieldWithName("f3").getFinalName());
            });
  }
}

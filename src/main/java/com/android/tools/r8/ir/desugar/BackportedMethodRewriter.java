// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter.RewritableMethods.MethodGenerator;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class BackportedMethodRewriter {
  public static final String UTILITY_CLASS_NAME_PREFIX = "$r8$backportedMethods$utility";
  private static final String UTILITY_CLASS_DESCRIPTOR_PREFIX = "L" + UTILITY_CLASS_NAME_PREFIX;
  private final Set<DexType> holders = Sets.newConcurrentHashSet();

  private final AppView<?> appView;
  private final IRConverter converter;
  private final DexItemFactory factory;
  private final RewritableMethods rewritableMethods;

  private Map<DexMethod, MethodGenerator> methodGenerators = new ConcurrentHashMap<>();

  public BackportedMethodRewriter(AppView<?> appView, IRConverter converter) {
    this.appView = appView;
    this.converter = converter;
    this.factory = appView.dexItemFactory();
    this.rewritableMethods = new RewritableMethods(factory, appView.options());
  }

  public void desugar(IRCode code) {
    if (rewritableMethods.isEmpty()) {
      return; // Nothing to do!
    }

    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isInvokeStatic()) {
        continue;
      }
      InvokeStatic invoke = instruction.asInvokeStatic();

      MethodGenerator generator = getMethodGeneratorOrNull(invoke.getInvokedMethod());
      if (generator == null) {
        continue;
      }
      iterator.replaceCurrentInstruction(
            new InvokeStatic(generator.generateMethod(factory),
                invoke.outValue(), invoke.inValues()));
      methodGenerators.putIfAbsent(generator.generateMethod(factory), generator);
      holders.add(code.method.method.holder);
    }
  }

  private Collection<DexProgramClass> findSynthesizedFrom(Builder<?> builder, DexType holder) {
    for (DexProgramClass synthesizedClass : builder.getSynthesizedClasses()) {
      if (holder == synthesizedClass.getType()) {
        return synthesizedClass.getSynthesizedFrom();
      }
    }
    return null;
  }

  public static boolean hasRewrittenMethodPrefix(DexType clazz) {
    return clazz.descriptor.toString().startsWith(UTILITY_CLASS_DESCRIPTOR_PREFIX);
  }

  public void synthesizeUtilityClass(
      Builder<?> builder, ExecutorService executorService, InternalOptions options)
      throws ExecutionException {
    if (holders.isEmpty()) {
      return;
    }
    Set<DexProgramClass> referencingClasses = Sets.newConcurrentHashSet();
    for (DexType holder : holders) {
      DexClass definitionFor = appView.definitionFor(holder);
      if (definitionFor == null) {
        Collection<DexProgramClass> synthesizedFrom = findSynthesizedFrom(builder, holder);
        assert synthesizedFrom != null;
        referencingClasses.addAll(synthesizedFrom);
      } else {
        referencingClasses.add(definitionFor.asProgramClass());
      }
    }
    MethodAccessFlags flags = MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC, false);
    ClassAccessFlags classAccessFlags =
        ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);

    for (MethodGenerator generator : methodGenerators.values()) {
      DexMethod method = generator.generateMethod(factory);
      // The utility class could have been synthesized, e.g., running R8 then D8.
      if (appView.definitionFor(method.holder) != null) {
        continue;
      }
      TemplateMethodCode code = generator.generateTemplateMethod(options, method);
      DexEncodedMethod dexEncodedMethod= new DexEncodedMethod(method,
          flags, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), code);
      DexProgramClass utilityClass =
          new DexProgramClass(
              method.holder,
              null,
              new SynthesizedOrigin("java8 methods utility class", getClass()),
              classAccessFlags,
              factory.objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              new DexEncodedMethod[] {dexEncodedMethod},
              DexEncodedMethod.EMPTY_ARRAY,
              factory.getSkipNameValidationForTesting(),
              referencingClasses);
      code.setUpContext(utilityClass);
      AppInfo appInfo = appView.appInfo();
      boolean addToMainDexList =
          referencingClasses.stream().anyMatch(clazz -> appInfo.isInMainDexList(clazz.type));
      appInfo.addSynthesizedClass(utilityClass);
      converter.optimizeSynthesizedClass(utilityClass, executorService);
      builder.addSynthesizedClass(utilityClass, addToMainDexList);
    }
  }

  private MethodGenerator getMethodGeneratorOrNull(DexMethod method) {
    DexMethod original = appView.graphLense().getOriginalMethodSignature(method);
    assert original != null;
    return rewritableMethods.getGenerator(
        original.holder.descriptor, original.name, original.proto);
  }

  private static final class ByteMethods extends TemplateMethodCode {
    ByteMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(byte i) {
      return Byte.valueOf(i).hashCode();
    }

    public static int compareImpl(byte a, byte b) {
      return Byte.valueOf(a).compareTo(Byte.valueOf(b));
    }

    public static int toUnsignedIntImpl(byte value) {
      return value & 0xff;
    }

    public static long toUnsignedLongImpl(byte value) {
      return value & 0xffL;
    }
  }

  private static final class ShortMethods extends TemplateMethodCode {
    ShortMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(short i) {
      return Short.valueOf(i).hashCode();
    }

    public static int compareImpl(short a, short b) {
      return Short.valueOf(a).compareTo(Short.valueOf(b));
    }

    public static int toUnsignedIntImpl(short value) {
      return value & 0xffff;
    }

    public static long toUnsignedLongImpl(short value) {
      return value & 0xffffL;
    }
  }

  private static final class IntegerMethods extends TemplateMethodCode {
    IntegerMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(int i) {
      return Integer.valueOf(i).hashCode();
    }

    public static int compareImpl(int a, int b) {
      return Integer.valueOf(a).compareTo(Integer.valueOf(b));
    }

    public static int maxImpl(int a, int b) {
      return java.lang.Math.max(a, b);
    }

    public static int minImpl(int a, int b) {
      return java.lang.Math.min(a, b);
    }

    public static int sumImpl(int a, int b) {
      return a + b;
    }

    public static int divideUnsignedImpl(int dividend, int divisor) {
      long dividendLong = dividend & 0xffffffffL;
      long divisorLong = divisor & 0xffffffffL;
      return (int) (dividendLong / divisorLong);
    }

    public static int remainderUnsignedImpl(int dividend, int divisor) {
      long dividendLong = dividend & 0xffffffffL;
      long divisorLong = divisor & 0xffffffffL;
      return (int) (dividendLong % divisorLong);
    }

    public static int compareUnsignedImpl(int a, int b) {
      int aFlipped = a ^ Integer.MIN_VALUE;
      int bFlipped = b ^ Integer.MIN_VALUE;
      return Integer.compare(aFlipped, bFlipped);
    }

    public static long toUnsignedLongImpl(int value) {
      return value & 0xffffffffL;
    }
  }

  private static final class DoubleMethods extends TemplateMethodCode {
    DoubleMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(double d) {
      return Double.valueOf(d).hashCode();
    }

    public static double maxImpl(double a, double b) {
      return java.lang.Math.max(a, b);
    }

    public static double minImpl(double a, double b) {
      return java.lang.Math.min(a, b);
    }

    public static double sumImpl(double a, double b) {
      return a + b;
    }

    public static boolean isFiniteImpl(double d) {
      Double boxed = Double.valueOf(d);
      return !boxed.isInfinite() && !boxed.isNaN();
    }
  }

  private static final class FloatMethods extends TemplateMethodCode {
    FloatMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(float d) {
      return Float.valueOf(d).hashCode();
    }

    public static float maxImpl(float a, float b) {
      return java.lang.Math.max(a, b);
    }

    public static float minImpl(float a, float b) {
      return java.lang.Math.min(a, b);
    }

    public static float sumImpl(float a, float b) {
      return a + b;
    }

    public static boolean isFiniteImpl(float d) {
      Float boxed = Float.valueOf(d);
      return !boxed.isInfinite() && !boxed.isNaN();
    }
  }

  private static final class BooleanMethods extends TemplateMethodCode {
    BooleanMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(boolean b) {
      return Boolean.valueOf(b).hashCode();
    }

    public static int compareImpl(boolean a, boolean b) {
      return Boolean.valueOf(a).compareTo(Boolean.valueOf(b));
    }

    public static boolean logicalAndImpl(boolean a, boolean b) {
      return a && b;
    }

    public static boolean logicalOrImpl(boolean a, boolean b) {
      return a || b;
    }

    public static boolean logicalXorImpl(boolean a, boolean b) {
      return a ^ b;
    }
  }

  private static final class LongMethods extends TemplateMethodCode {
    LongMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(long i) {
      return Long.valueOf(i).hashCode();
    }

    public static long maxImpl(long a, long b) {
      return java.lang.Math.max(a, b);
    }

    public static long minImpl(long a, long b) {
      return java.lang.Math.min(a, b);
    }

    public static long sumImpl(long a, long b) {
      return a + b;
    }

    public static long divideUnsignedImpl(long dividend, long divisor) {
      // This implementation is adapted from Guava's UnsignedLongs.java and Longs.java.

      if (divisor < 0) { // i.e., divisor >= 2^63:
        // Reference implementation calls UnsignedLongs.compare(dividend, divisor) whose
        // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
        // implementations of flip() and compare() are inlined here instead.
        long dividendFlipped = dividend ^ Long.MIN_VALUE;
        long divisorFlipped = divisor ^ Long.MIN_VALUE;
        if (dividendFlipped < divisorFlipped) {
          return 0; // dividend < divisor
        } else {
          return 1; // dividend >= divisor
        }
      }

      // Optimization - use signed division if dividend < 2^63
      if (dividend >= 0) {
        return dividend / divisor;
      }

      // Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
      // guaranteed to be either exact or one less than the correct value. This follows from the
      // fact that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is
      // not quite trivial.
      long quotient = ((dividend >>> 1) / divisor) << 1;
      long rem = dividend - quotient * divisor;

      // Reference implementation calls UnsignedLongs.compare(rem, divisor) whose
      // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
      // implementations of flip() and compare() are inlined here instead.
      long remFlipped = rem ^ Long.MIN_VALUE;
      long divisorFlipped = divisor ^ Long.MIN_VALUE;
      return quotient + (remFlipped >= divisorFlipped ? 1 : 0);
    }

    public static long remainderUnsignedImpl(long dividend, long divisor) {
      // This implementation is adapted from Guava's UnsignedLongs.java and Longs.java.

      if (divisor < 0) { // i.e., divisor >= 2^63:
        // Reference implementation calls UnsignedLongs.compare(dividend, divisor) whose
        // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
        // implementations of flip() and compare() are inlined here instead.
        long dividendFlipped = dividend ^ Long.MIN_VALUE;
        long divisorFlipped = divisor ^ Long.MIN_VALUE;
        if (dividendFlipped < divisorFlipped) {
          return dividend; // dividend < divisor
        } else {
          return dividend - divisor; // dividend >= divisor
        }
      }

      // Optimization - use signed modulus if dividend < 2^63
      if (dividend >= 0) {
        return dividend % divisor;
      }

      // Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
      // guaranteed to be either exact or one less than the correct value. This follows from the
      // fact that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is
      // not quite trivial.
      long quotient = ((dividend >>> 1) / divisor) << 1;
      long rem = dividend - quotient * divisor;

      // Reference implementation calls UnsignedLongs.compare(rem, divisor) whose
      // implementation is Longs.compare(UnsignedLong.flip(a), UnsignedLong.flip(b)). The
      // implementations of flip() and compare() are inlined here instead.
      long remFlipped = rem ^ Long.MIN_VALUE;
      long divisorFlipped = divisor ^ Long.MIN_VALUE;
      return rem - (remFlipped >= divisorFlipped ? divisor : 0);
    }

    public static int compareUnsignedImpl(long a, long b) {
      long aFlipped = a ^ Long.MIN_VALUE;
      long bFlipped = b ^ Long.MIN_VALUE;
      return Long.compare(aFlipped, bFlipped);
    }
  }

  private static final class CharacterMethods extends TemplateMethodCode {
    CharacterMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static int hashCodeImpl(char i) {
      return Character.valueOf(i).hashCode();
    }

    public static int compareImpl(char a, char b) {
      return Character.valueOf(a).compareTo(Character.valueOf(b));
    }
  }

  private static final class StringMethods extends TemplateMethodCode {
    StringMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static String joinArrayImpl(CharSequence delimiter, CharSequence... elements) {
      if (delimiter == null) throw new NullPointerException("delimiter");
      StringBuilder builder = new StringBuilder();
      if (elements.length > 0) {
        builder.append(elements[0]);
        for (int i = 1; i < elements.length; i++) {
          builder.append(delimiter);
          builder.append(elements[i]);
        }
      }
      return builder.toString();
    }

    public static String joinIterableImpl(CharSequence delimiter,
        Iterable<? extends CharSequence> elements) {
      if (delimiter == null) throw new NullPointerException("delimiter");
      StringBuilder builder = new StringBuilder();
      Iterator<? extends CharSequence> iterator = elements.iterator();
      if (iterator.hasNext()) {
        builder.append(iterator.next());
        while (iterator.hasNext()) {
          builder.append(delimiter);
          builder.append(iterator.next());
        }
      }
      return builder.toString();
    }
  }

  private static final class ObjectsMethods extends TemplateMethodCode {
    ObjectsMethods(InternalOptions options, DexMethod method, String methodName) {
      super(options, method, methodName, method.proto.toDescriptorString());
    }

    public static <T> int compareImpl(T a, T b, Comparator<? super T> c) {
      return a == b ? 0 : c.compare(a, b);
    }

    public static boolean deepEqualsImpl(Object a, Object b) {
      if (a == b) return true;
      if (a == null) return false;
      if (a instanceof boolean[]) {
        return b instanceof boolean[] && Arrays.equals((boolean[]) a, (boolean[]) b);
      }
      if (a instanceof byte[]) {
        return b instanceof byte[] && Arrays.equals((byte[]) a, (byte[]) b);
      }
      if (a instanceof char[]) {
        return b instanceof char[] && Arrays.equals((char[]) a, (char[]) b);
      }
      if (a instanceof double[]) {
        return b instanceof double[] && Arrays.equals((double[]) a, (double[]) b);
      }
      if (a instanceof float[]) {
        return b instanceof float[] && Arrays.equals((float[]) a, (float[]) b);
      }
      if (a instanceof int[]) {
        return b instanceof int[] && Arrays.equals((int[]) a, (int[]) b);
      }
      if (a instanceof long[]) {
        return b instanceof long[] && Arrays.equals((long[]) a, (long[]) b);
      }
      if (a instanceof short[]) {
        return b instanceof short[] && Arrays.equals((short[]) a, (short[]) b);
      }
      if (a instanceof Object[]) {
        return b instanceof Object[] && Arrays.deepEquals((Object[]) a, (Object[]) b);
      }
      return a.equals(b);
    }

    public static boolean equalsImpl(Object a, Object b) {
      return a == b || (a != null && a.equals(b));
    }

    public static int hashImpl(Object[] o) {
      return Arrays.hashCode(o);
    }

    public static int hashCodeImpl(Object o) {
      return o == null ? 0 : o.hashCode();
    }

    public static boolean isNullImpl(Object o) {
      return o == null;
    }

    public static boolean nonNullImpl(Object o) {
      return o != null;
    }

    public static <T> T requireNonNullMessageImpl(T obj, String message) {
      if (obj == null) {
        throw new NullPointerException(message);
      }
      return obj;
    }

    public static String toStringImpl(Object o) {
      return Objects.toString(o, "null");
    }

    public static String toStringDefaultImpl(Object o, String nullDefault) {
      return o == null ? nullDefault : o.toString();
    }
  }

  public static final class RewritableMethods {
    // Map class, method, proto to a generator for creating the code and method.
    private final Map<DexString, Map<DexString, Map<DexProto, MethodGenerator>>> rewritable =
        new HashMap<>();

    public RewritableMethods(DexItemFactory factory, InternalOptions options) {
      if (!options.canUseJava7CompareAndObjectsOperations()) {
        initializeJava7CompareOperations(factory);
      }
      if (!options.canUseJava8SignedOperations()) {
        initializeJava8SignedOperations(factory);
      }
      if (!options.canUseJava8UnsignedOperations()) {
        initializeJava8UnsignedOperations(factory);
      }
    }

    boolean isEmpty() {
      return rewritable.isEmpty();
    }

    private void initializeJava7CompareOperations(DexItemFactory factory) {
      // Note: Long.compare rewriting is handled by CodeRewriter since there is a dedicated
      // bytecode which supports the operation.

      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.compare(byte a, byte b)
      DexString method = factory.createString("compare");
      DexProto proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ByteMethods::new, "compareImpl", clazz, method, proto));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.compare(short a, short b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ShortMethods::new, "compareImpl", clazz, method, proto));

      // Integer
      clazz = factory.boxedIntDescriptor;
      // int Integer.compare(int a, int b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "compareImpl", clazz, method, proto));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;
      // int Boolean.compare(boolean a, boolean b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(BooleanMethods::new, "compareImpl", clazz, method, proto));

      // Character
      clazz = factory.boxedCharDescriptor;
      // int Character.compare(char a, char b)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.charType, factory.charType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(CharacterMethods::new, "compareImpl", clazz, method, proto));

      // Objects
      clazz = factory.objectsDescriptor;

      // int Objects.compare(T a, T b, Comparator<? super T> c)
      method = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.objectType, factory.objectType,
          factory.comparatorType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "compareImpl", clazz, method, proto));

      // boolean Objects.deepEquals(Object a, Object b)
      method = factory.createString("deepEquals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "deepEqualsImpl", clazz, method, proto));

      // boolean Objects.equals(Object a, Object b)
      method = factory.createString("equals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "equalsImpl", clazz, method, proto));

      // int Objects.hash(Object... o)
      method = factory.createString("hash");
      proto = factory.createProto(factory.intType, factory.objectArrayType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "hashImpl", clazz, method, proto));

      // int Objects.hashCode(Object o)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "hashCodeImpl", clazz, method, proto));

      // Note: Objects.requireNonNull(T) rewriting is handled by CodeRewriter for now.

      // T Objects.requireNonNull(T obj, String message)
      method = factory.createString("requireNonNull");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.stringType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "requireNonNullMessageImpl", clazz, method,
              proto));

      // String Objects.toString(Object o)
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "toStringImpl", clazz, method, proto));

      // String Objects.toString(Object o, String nullDefault);
      method = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType, factory.stringType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "toStringDefaultImpl", clazz, method, proto));
    }

    private void initializeJava8SignedOperations(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;
      // int Byte.hashCode(byte i)
      DexString method = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ByteMethods::new, "hashCodeImpl", clazz, method, proto));

      // Short
      clazz = factory.boxedShortDescriptor;
      // int Short.hashCode(short i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.shortType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ShortMethods::new, "hashCodeImpl", clazz, method, proto));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.hashCode(int i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "hashCodeImpl", clazz, method, proto));

      // int Integer.max(int a, int b)
      method = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "maxImpl", clazz, method, proto));

      // int Integer.min(int a, int b)
      method = factory.createString("min");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "minImpl", clazz, method, proto));

      // int Integer.sum(int a, int b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "sumImpl", clazz, method, proto));

      // Double
      clazz = factory.boxedDoubleDescriptor;

      // int Double.hashCode(double d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.doubleType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(DoubleMethods::new, "hashCodeImpl", clazz, method, proto));

      // double Double.max(double a, double b)
      method = factory.createString("max");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(DoubleMethods::new, "maxImpl", clazz, method, proto));

      // double Double.min(double a, double b)
      method = factory.createString("min");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(DoubleMethods::new, "minImpl", clazz, method, proto));

      // double Double.sum(double a, double b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(DoubleMethods::new, "sumImpl", clazz, method, proto));

      // boolean Double.isFinite(double a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.doubleType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(DoubleMethods::new, "isFiniteImpl", clazz, method, proto));

      // Float
      clazz = factory.boxedFloatDescriptor;

      // int Float.hashCode(float d)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.floatType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(FloatMethods::new, "hashCodeImpl", clazz, method, proto));

      // float Float.max(float a, float b)
      method = factory.createString("max");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(FloatMethods::new, "maxImpl", clazz, method, proto));

      // float Float.min(float a, float b)
      method = factory.createString("min");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(FloatMethods::new, "minImpl", clazz, method, proto));

      // float Float.sum(float a, float b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(FloatMethods::new, "sumImpl", clazz, method, proto));

      // boolean Float.isFinite(float a)
      method = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.floatType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(FloatMethods::new, "isFiniteImpl", clazz, method, proto));

      // Boolean
      clazz = factory.boxedBooleanDescriptor;

      // int Boolean.hashCode(boolean b)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.booleanType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(BooleanMethods::new, "hashCodeImpl", clazz, method, proto));

      // boolean Boolean.logicalAnd(boolean a, boolean b)
      method = factory.createString("logicalAnd");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(BooleanMethods::new, "logicalAndImpl", clazz, method, proto));

      // boolean Boolean.logicalOr(boolean a, boolean b)
      method = factory.createString("logicalOr");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(BooleanMethods::new, "logicalOrImpl", clazz, method, proto));

      // boolean Boolean.logicalXor(boolean a, boolean b)
      method = factory.createString("logicalXor");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(BooleanMethods::new, "logicalXorImpl", clazz, method, proto));

      // Long
      clazz = factory.boxedLongDescriptor;

      // int Long.hashCode(long i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "hashCodeImpl", clazz, method, proto));

      // long Long.max(long a, long b)
      method = factory.createString("max");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "maxImpl", clazz, method, proto));

      // long Long.min(long a, long b)
      method = factory.createString("min");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "minImpl", clazz, method, proto));

      // long Long.sum(long a, long b)
      method = factory.createString("sum");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "sumImpl", clazz, method, proto));

      // Character
      clazz = factory.boxedCharDescriptor;

      // int Character.hashCode(char i)
      method = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.charType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(CharacterMethods::new, "hashCodeImpl", clazz, method, proto));

      // Objects
      clazz = factory.objectsDescriptor;

      // boolean Objects.isNull(Object o)
      method = factory.createString("isNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "isNullImpl", clazz, method, proto));

      // boolean Objects.nonNull(Object a)
      method = factory.createString("nonNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ObjectsMethods::new, "nonNullImpl", clazz, method, proto));
    }

    private void initializeJava8UnsignedOperations(DexItemFactory factory) {
      // Byte
      DexString clazz = factory.boxedByteDescriptor;

      // int Byte.toUnsignedInt(byte value)
      DexString method = factory.createString("toUnsignedInt");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ByteMethods::new, "toUnsignedIntImpl", clazz, method, proto));

      // long Byte.toUnsignedLong(byte value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.byteType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ByteMethods::new, "toUnsignedLongImpl", clazz, method, proto));

      // Short
      clazz = factory.boxedShortDescriptor;

      // int Short.toUnsignedInt(short value)
      method = factory.createString("toUnsignedInt");
      proto = factory.createProto(factory.intType, factory.shortType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ShortMethods::new, "toUnsignedIntImpl", clazz, method, proto));

      // long Short.toUnsignedLong(short value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.shortType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(ShortMethods::new, "toUnsignedLongImpl", clazz, method, proto));

      // Integer
      clazz = factory.boxedIntDescriptor;

      // int Integer.divideUnsigned(int a, int b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "divideUnsignedImpl", clazz, method, proto));

      // int Integer.remainderUnsigned(int a, int b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "remainderUnsignedImpl", clazz, method, proto));

      // int Integer.compareUnsigned(int a, int b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "compareUnsignedImpl", clazz, method, proto));

      // long Integer.toUnsignedLong(int value)
      method = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.intType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(IntegerMethods::new, "toUnsignedLongImpl", clazz, method, proto));

      // Long
      clazz = factory.boxedLongDescriptor;

      // long Long.divideUnsigned(long a, long b)
      method = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "divideUnsignedImpl", clazz, method, proto));

      // long Long.remainderUnsigned(long a, long b)
      method = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "remainderUnsignedImpl", clazz, method, proto));

      // int Long.compareUnsigned(long a, long b)
      method = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.longType, factory.longType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(LongMethods::new, "compareUnsignedImpl", clazz, method, proto));

      // String
      clazz = factory.stringDescriptor;

      // String String.join(CharSequence, CharSequence...)
      method = factory.createString("join");
      proto = factory.createProto(factory.stringType, factory.charSequenceType,
          factory.charSequenceArrayType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(StringMethods::new, "joinArrayImpl", clazz, method, proto));

      // String String.join(CharSequence, Iterable<? extends CharSequence>)
      method = factory.createString("join");
      proto =
          factory.createProto(factory.stringType, factory.charSequenceType, factory.iterableType);
      addOrGetMethod(clazz, method).put(proto,
          new MethodGenerator(StringMethods::new, "joinIterableImpl", clazz, method, proto));
    }

    private Map<DexString, Map<DexProto, MethodGenerator>> addOrGetClass(DexString clazz) {
      return rewritable.computeIfAbsent(clazz, k -> new HashMap<>());
    }

    private Map<DexProto, MethodGenerator> addOrGetMethod(
        DexString clazz, DexString method) {
      return addOrGetClass(clazz).computeIfAbsent(method, k -> new HashMap<>());
    }

    public MethodGenerator getGenerator(DexString clazz, DexString method, DexProto proto) {
      Map<DexString, Map<DexProto, MethodGenerator>> classMap = rewritable.get(clazz);
      if (classMap != null) {
        Map<DexProto, MethodGenerator> methodMap = classMap.get(method);
        if (methodMap != null) {
          return methodMap.get(proto);
        }
      }
      return null;
    }

    public static class MethodGenerator {
      private final TemplateMethodFactory factory;
      private final String methodName;
      private final DexString clazz;
      private final DexString method;
      private final DexProto proto;
      private DexMethod dexMethod;

      public MethodGenerator(TemplateMethodFactory factory,
          String methodName, DexString clazz, DexString method, DexProto proto) {
        this.factory = factory;
        this.methodName = methodName;
        this.clazz = clazz;
        this.method = method;
        this.proto = proto;
      }

      public DexMethod generateMethod(DexItemFactory factory) {
        if (dexMethod != null) {
          return dexMethod;
        }
        String unqualifiedName =
            DescriptorUtils.getUnqualifiedClassNameFromDescriptor(clazz.toString());
        String descriptor =
            UTILITY_CLASS_DESCRIPTOR_PREFIX + '$' + unqualifiedName + '$' + methodName + ';';
        DexType clazz = factory.createType(descriptor);
        dexMethod = factory.createMethod(clazz, proto, method);
        return dexMethod;
      }

      public TemplateMethodCode generateTemplateMethod(InternalOptions options, DexMethod method) {
        return factory.create(options, method, methodName);
      }
    }

    private interface TemplateMethodFactory {
      TemplateMethodCode create(InternalOptions options, DexMethod method, String name);
    }
  }
}
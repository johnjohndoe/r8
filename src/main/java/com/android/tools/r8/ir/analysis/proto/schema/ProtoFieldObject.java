// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.dexitembasedstring.FieldNameComputationInfo;

public class ProtoFieldObject extends ProtoObject {

  private final DexField field;

  public ProtoFieldObject(DexField field) {
    this.field = field;
  }

  @Override
  public Instruction buildIR(AppView<?> appView, IRCode code) {
    Value value =
        code.createValue(
            TypeLatticeElement.stringClassType(appView, Nullability.definitelyNotNull()));
    return new DexItemBasedConstString(
        value,
        field,
        FieldNameComputationInfo.forFieldName(),
        ThrowingInfo.defaultForConstString(appView.options()));
  }
}

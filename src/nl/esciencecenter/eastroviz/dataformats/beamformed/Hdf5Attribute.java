/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.eastroviz.dataformats.beamformed;

import java.lang.reflect.Array;
import java.util.Arrays;

import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.Datatype;

public class Hdf5Attribute {
    private Attribute a;

    public Hdf5Attribute(final Attribute a) {
        this.a = a;
    }

    boolean isPrimitive() {
        if (a.getRank() != 1) {
            return false;
        }

        final long[] dims = a.getDataDims();
        if (dims[0] != 1) {
            return false;
        }
        return true;
    }

    private void checkPrimitive() {
        if (!isPrimitive()) {
            throw new RuntimeException("internal error, this is not a primitive datatype");
        }
    }

    public int getPrimitiveIntVal() {
        checkPrimitive();
        return ((int[]) a.getValue())[0];
    }

    public long getPrimitiveLongVal() {
        checkPrimitive();
        return ((long[]) a.getValue())[0];
    }

    public float getPrimitiveFloatVal() {
        checkPrimitive();
        return ((float[]) a.getValue())[0];
    }

    public double getPrimitiveDoubleVal() {
        checkPrimitive();
        return ((double[]) a.getValue())[0];
    }

    public String getPrimitiveStringVal() {
        checkPrimitive();
        return ((String[]) a.getValue())[0];
    }

    public int[] get1DIntArrayVal() {
        if (a.getRank() != 1) {
            throw new RuntimeException();
        }

        return (int[]) a.getValue();
    }

    public long[] getDataDims() {
        return a.getDataDims();
    }

    public String getName() {
        return a.getName();
    }

    public int getRank() {
        return a.getRank();
    }

    public Datatype getType() {
        return a.getType();
    }

    public Object getValue() {
        return a.getValue();
    }

    public boolean isUnsigned() {
        return a.isUnsigned();
    }

    public void setValue(final Object arg0) {
        a.setValue(arg0);
    }

    public Object getPrimitiveValue() {
        checkPrimitive();
        return Array.get(a.getValue(), 0);
    }

    public String getValueString() {
        if (isPrimitive()) {
            return (Array.get(a.getValue(), 0)).toString();
        } else {
            Object res = a.getValue();
            if (res instanceof String[]) {
                return Arrays.toString((String[]) res);
            } else if (res instanceof int[]) {
                return Arrays.toString((int[]) res);
            } else {
                return res.toString();
            }
        }
    }

    @Override
    public String toString() {
        return a.toString();
    }

    public String toString(final String arg0) {
        return a.toString(arg0);
    }
}

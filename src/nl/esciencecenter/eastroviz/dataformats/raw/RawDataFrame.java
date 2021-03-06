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
package nl.esciencecenter.eastroviz.dataformats.raw;

import java.io.DataInputStream;
import java.io.IOException;

public class RawDataFrame {
    public static final int BEAMLET_DATA_SIZE = 8; // bytes: 16 bit sampes * real/imag * 2 polarizations
    public static final float MAX_VAL = 65535.0f / 2.0f;
    @SuppressWarnings("unused")
    private RawDataFrameHeader h;

    // beamlets are 4 signed shorts: xr, xi; yr, yi;
    private float data[][][][]; // [NR_SUBBANDS][NR_TIMES][NR_POLARIZATIONS][REAL/IMAG];

    private int nrSubbands;

    RawDataFrame(final int nrSubbands) { // nofBeamlets is incorrectly written in the file. So, we have to use the nr of subbands.
        this.nrSubbands = nrSubbands;
    }

    public boolean read(final DataInputStream din) {
        final RawDataFrameHeader h = new RawDataFrameHeader();
        h.read(din);

        if (data == null || data[0].length != h.getNofBlocks()) {
            data = new float[nrSubbands][h.getNofBlocks()][RawDataReader.NR_POLARIZATIONS][2];
        }

        for (int subband = 0; subband < nrSubbands; subband++) {
            for (int time = 0; time < h.getNofBlocks(); time++) {
                for (int pol = 0; pol < RawDataReader.NR_POLARIZATIONS; pol++) {
                    try {
                        // convert 16 bit unsigned to float between -1 and 1
                        data[subband][time][pol][0] = din.readUnsignedShort() / MAX_VAL - 1;
                        data[subband][time][pol][1] = din.readUnsignedShort() / MAX_VAL - 1;
                    } catch (final IOException e) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public float[][][][] getData() {
        return data;
    }
}

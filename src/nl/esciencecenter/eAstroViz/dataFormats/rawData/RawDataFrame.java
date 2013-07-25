package nl.esciencecenter.eAstroViz.dataFormats.rawData;

import java.io.DataInputStream;
import java.io.IOException;

public class RawDataFrame {
    static final int BEAMLET_DATA_SIZE = 8; // bytes: 16 bit sampes * real/imag * 2 polarizations
    static final float MAX_VAL = 65535.0f / 2.0f;
    RawDataFrameHeader h;

    // beamlets are 4 signed shorts: xr, xi; yr, yi;
    float data[][][][]; // [NR_SUBBANDS][NR_TIMES][NR_POLARIZATIONS][REAL/IMAG];

    int nrSubbands;

    RawDataFrame(final int nrSubbands) { // nofBeamlets is incorrectly written in the file. So, we have to use the nr of subbands.
        this.nrSubbands = nrSubbands;
    }

    public boolean read(final DataInputStream din) {
        final RawDataFrameHeader h = new RawDataFrameHeader();
        h.read(din);

        if (data == null || data[0].length != h.nofBlocks) {
            data = new float[nrSubbands][h.nofBlocks][RawDataReader.NR_POLARIZATIONS][2];
        }

        for (int subband = 0; subband < nrSubbands; subband++) {
            for (int time = 0; time < h.nofBlocks; time++) {
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

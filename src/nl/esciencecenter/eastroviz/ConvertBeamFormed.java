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
package nl.esciencecenter.eastroviz;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertBeamFormed {
    private static final Logger logger = LoggerFactory.getLogger(ConvertBeamFormed.class);
    static final int DEFAULT_ZOOM = 1024;

    private final String fileName;
    private final String outputFileName;

    private float[][][] data; // [time][subband][channel]
    private int nrSubbands;
    private int nrChannels;
    private int nrTimes;
    private int nrSamplesPerSecond;
    private int zoomFactor;

    public ConvertBeamFormed(final String fileName, final String outputFileName, int zoomFactor) {
        this.fileName = fileName;
        this.outputFileName = outputFileName;
        this.zoomFactor = zoomFactor;
        if (this.zoomFactor < 0) {
            this.zoomFactor = DEFAULT_ZOOM;
        }
    }

    void read() throws IOException {
        final BeamFormedDataReader bfdr = new BeamFormedDataReader(fileName, Integer.MAX_VALUE, Integer.MAX_VALUE, zoomFactor);
        final BeamFormedData bfd = bfdr.read();
        data = bfd.getData();
        nrSubbands = bfd.getNrSubbands();
        nrChannels = bfd.getNrChannels();
        nrTimes = bfd.getSizeX();
        nrSamplesPerSecond = bfd.getNrSamplesPerTimeStep();
    }

    void write() throws IOException {
        final FileOutputStream out = new FileOutputStream(outputFileName);
        final BufferedOutputStream buf = new BufferedOutputStream(out);
        final DataOutputStream dataOut = new DataOutputStream(buf);

        dataOut.writeInt(nrTimes);
        dataOut.writeInt(nrSubbands);
        dataOut.writeInt(nrChannels);
        dataOut.writeInt(nrSamplesPerSecond);

        logger.info("Writing output, nrTimes  = " + nrTimes + ", nrSubbands = " + nrSubbands + ", nrChannels = " + nrChannels);

        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int ch = 0; ch < nrChannels; ch++) {
                    dataOut.writeFloat(data[time][sb][ch]);
                }
            }
        }

        dataOut.close();
    }

    public static void main(final String[] args) throws IOException {
        final ConvertBeamFormed cm = new ConvertBeamFormed(args[0], args[1], Integer.parseInt(args[2]));
        cm.read();
        cm.write();
    }
}

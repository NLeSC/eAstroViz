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
package nl.esciencecenter.eAstroViz;

import static org.junit.Assert.assertEquals;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;

import org.junit.After;
import org.junit.Test;

public class TestBeamFormedData {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;

    static final String INPUT_FILE_NAME =
            "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.beamFormed";

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRead() {
        BeamFormedDataReader bfdr = new BeamFormedDataReader(INPUT_FILE_NAME, -1, -1, 1);
        BeamFormedData bfd = bfdr.read();

        assertEquals(INPUT_FILE_NAME, bfd.getFileName());
        assertEquals(NR_CHANNELS, bfd.getNrChannels());
        assertEquals(NR_SUBBANDS, bfd.getNrSubbands());
        assertEquals(NR_TIMES, bfd.getSizeX());

        float[][][] data = bfd.getData();

        // order should be [time][subband][channel]
        assertEquals(NR_TIMES, data.length);
        assertEquals(NR_SUBBANDS, data[0].length);
        assertEquals(NR_CHANNELS, data[0][0].length);
    }
}

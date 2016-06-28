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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;

import org.junit.After;
import org.junit.Test;

public class TestBeamFormedData {
	static final int ZOOM = 16;
    static final int NR_CHANNELS = 16;
    static final int NR_CHANNELS_VISIBLE = BeamFormedData.REMOVE_CHANNEL_0_FROM_VIEW ? NR_CHANNELS -1 : NR_CHANNELS;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * ZOOM - 1;

    static final String INPUT_FILE_NAME =
            "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.beamFormed";

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRead() throws IOException {
        BeamFormedDataReader bfdr = new BeamFormedDataReader(INPUT_FILE_NAME, Integer.MAX_VALUE, Integer.MAX_VALUE, ZOOM);
        BeamFormedData bfd = bfdr.read();

        assertEquals(INPUT_FILE_NAME, bfd.getFileName());
        assertEquals(NR_CHANNELS_VISIBLE, bfd.getNrChannels());
        assertEquals(NR_SUBBANDS, bfd.getNrSubbands());
        assertEquals(NR_TIMES, bfd.getSizeX());

        float[][][] data = bfd.getData();

        // order should be [time][subband][channel]
        assertEquals(NR_TIMES, data.length);
        assertEquals(NR_SUBBANDS, data[0].length);
        assertEquals(NR_CHANNELS, data[0][0].length);
    }
}

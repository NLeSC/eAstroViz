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

import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.visibility.VisibilityData;

import org.junit.After;
import org.junit.Test;

public class TestVisibilityData {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16;

    static final String INPUT_FILE_NAME =
            "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.visibilities";

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRead() throws IOException {
        VisibilityData bfd = new VisibilityData(INPUT_FILE_NAME, 1, 0, 0, -1, -1);
        bfd.read();

        assertEquals(INPUT_FILE_NAME, bfd.getFileName());
        if (VisibilityData.REMOVE_CHANNEL_0_FROM_VIEW) {
            assertEquals(NR_CHANNELS - 1, bfd.getNrChannels());
        } else {
            assertEquals(NR_CHANNELS, bfd.getNrChannels());
        }
        assertEquals(NR_SUBBANDS, bfd.getNrSubbands());
        assertEquals(NR_TIMES, bfd.getSizeX());

        float[][][][] data = bfd.getData();

        // [time][nrSubbands][nrChannels][nrCrossPolarizations]
        assertEquals(NR_TIMES, data.length);
        assertEquals(NR_SUBBANDS, data[0].length);
        assertEquals(NR_CHANNELS, data[0][0].length);
    }

    @Test
    public void testBaseline() throws IOException {
        for (int b = 0; b < 10000; b++) {
            int station1 = VisibilityData.baselineToStation1(b);
            int station2 = VisibilityData.baselineToStation2(b);
            int baseline = VisibilityData.baseline(station1, station2);
            assertEquals(b, baseline);
        }
    }
}

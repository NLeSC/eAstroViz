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
import static org.junit.Assert.fail;

import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.preprocessed.filtered.FilteredData;

import org.junit.Test;

public class TestFilteredData {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;

    static final String INPUT_FILE_NAME =
            "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.filtered";

    @Test
    public void testRead() {
        int integrationFactor = 1;
        int maxSequenceNr = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;
        int station = 0;
        int pol = 0;

        final FilteredData filteredData =
                new FilteredData(INPUT_FILE_NAME, integrationFactor, maxSequenceNr, maxSubbands, station, pol);

        try {
            filteredData.read();
        } catch (IOException e) {
            fail("read failed: " + e);
        }

        assertEquals(INPUT_FILE_NAME, filteredData.getFileName());
        assertEquals(NR_CHANNELS, filteredData.getNrChannels());
        assertEquals(NR_POLARIZATIONS, filteredData.getNrPolarizations());
        assertEquals(NR_STATIONS, filteredData.getNrStations());
        assertEquals(NR_SUBBANDS, filteredData.getNrSubbands());
        assertEquals(NR_TIMES, filteredData.getTotalTime());
    }
}

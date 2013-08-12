package src.nl.esciencecenter.eAstroViz;

import static org.junit.Assert.*;

import nl.esciencecenter.eAstroViz.ConvertBinaryFiltered;

import org.junit.Test;

public class TestConvertBinaryFiltered {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;

    @Test
    public void testWrite() {
        fail("Not yet implemented");
    }

    @Test
    public void testRead() {
        String inputFileName = "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.filteredRaw";
        String outputFileName = "dummy";

        ConvertBinaryFiltered f = new ConvertBinaryFiltered(inputFileName, outputFileName, -1);
        f.read();

        assertEquals(inputFileName, f.getFileName());
        assertEquals(outputFileName, f.getOutputFileName());
        assertEquals(-1, f.getMaxNrTimes());
        assertEquals(NR_CHANNELS, f.getNrChannels());
        assertEquals(NR_POLARIZATIONS, f.getNrPolarizations());
        assertEquals(NR_STATIONS, f.getNrStations());
        assertEquals(NR_SUBBANDS, f.getNrSubbands());
        assertEquals(NR_TIMES, f.getNrTimes());

        float[][][][][] data = f.getData();

        // order should be [staton][time][subband][channel][pol]

        assertEquals(NR_STATIONS, data.length);
        assertEquals(NR_TIMES, data[0].length);
        assertEquals(NR_SUBBANDS, data[0][0].length);
        assertEquals(NR_CHANNELS, data[0][0][0].length);
        assertEquals(NR_POLARIZATIONS, data[0][0][0][0].length);

        long diffs = 0;
        long flagged = 0;
        for (int station = 0; station < data.length; station++) {
            for (int time = 0; time < data[0].length; time++) {
                for (int sb = 0; sb < data[0][0].length; sb++) {
                    for (int ch = 0; ch < data[0][0][0].length; ch++) {
                        if(data[station][time][sb][ch][0] < 0) {
                            flagged++;
                        }
                        float diff = Math.abs(data[station][time][sb][ch][0] - data[station][time][sb][ch][1]);
                        if(diff > 1E-9) {
                            diffs++;
                        }
                    }
                }
            }
        }
        
        long nrSamples = data.length * data[0].length * data[0][0].length * data[0][0][0].length; 
                
        System.err.println("nrSamples = " + nrSamples + ", flagged = " + flagged + ", diffs = " + diffs);
        assertEquals(flagged + diffs, nrSamples);
    }

    /*
             for (int station = 0; station < data.length; station++) {
                for (int time = 0; time < data[0].length; time++) {
                    for (int sb = 0; sb < data[0][0].length; sb++) {
                        for (int ch = 0; ch < data[0][0][0].length; ch++) {
                            for (int pol = 0; pol < data[0][0][0][0].length; pol++) {
                                
                            }
                        }
                    }
                }
            }

     */
    @Test
    public void testReadFile() {
        fail("Not yet implemented");
    }

}

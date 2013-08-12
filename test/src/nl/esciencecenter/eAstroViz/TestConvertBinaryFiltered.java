package nl.esciencecenter.eAstroViz;

import static org.junit.Assert.*;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import nl.esciencecenter.eAstroViz.ConvertBinaryFiltered;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

import org.junit.Test;

public class TestConvertBinaryFiltered {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;

    static final int HEADER_FIELDS = 5;
    
    static final String INPUT_FILE_NAME = "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.filteredRaw";
    static final String OUTPUT_FILE_NAME = "dummy.filtered";

    @Test
    public void testWrite() {
        ConvertBinaryFiltered f = new ConvertBinaryFiltered(INPUT_FILE_NAME, OUTPUT_FILE_NAME, -1);
        f.read();

        try {
            f.write();
        } catch (IOException e) {
            fail("write failed: " + e);
        }

        File file = new File(OUTPUT_FILE_NAME);
        
        assertTrue(file.exists());

        assertEquals(HEADER_FIELDS * DataProvider.SIZE_OF_FLOAT + NR_STATIONS * NR_TIMES * NR_SUBBANDS * NR_CHANNELS * NR_POLARIZATIONS * DataProvider.SIZE_OF_FLOAT, file.length());
        
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            fail("open failed: " + e);
        }
        
        DataInputStream din = new DataInputStream(fin);
        
        try {
            assertEquals(NR_STATIONS, din.readInt());
            assertEquals(NR_TIMES, din.readInt());
            assertEquals(NR_SUBBANDS, din.readInt());
            assertEquals(NR_CHANNELS, din.readInt());
            assertEquals(NR_POLARIZATIONS, din.readInt());
        } catch (IOException e) {
            fail("read failed: " + e);
        }
    }

    @Test
    public void testRead() {

        ConvertBinaryFiltered f = new ConvertBinaryFiltered(INPUT_FILE_NAME, OUTPUT_FILE_NAME, -1);
        f.read();

        assertEquals(INPUT_FILE_NAME, f.getFileName());
        assertEquals(OUTPUT_FILE_NAME, f.getOutputFileName());
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
}

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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;
import nl.esciencecenter.eastroviz.dataformats.preprocessed.compressedbeamformed.CompressedBeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.preprocessed.filtered.FilteredData;
import nl.esciencecenter.eastroviz.dataformats.preprocessed.intermediate.IntermediateData;
import nl.esciencecenter.eastroviz.dataformats.raw.RawData;
import nl.esciencecenter.eastroviz.dataformats.raw.RawDataFrame;
import nl.esciencecenter.eastroviz.dataformats.raw.RawDataReader;
import nl.esciencecenter.eastroviz.dataformats.visibility.MSMetaData;
import nl.esciencecenter.eastroviz.dataformats.visibility.MSReader;
import nl.esciencecenter.eastroviz.dataformats.visibility.VisibilityData;
import nl.esciencecenter.eastroviz.gui.BeamFormedFrame;
import nl.esciencecenter.eastroviz.gui.PreProcessedFrame;
import nl.esciencecenter.eastroviz.gui.RawFrame;
import nl.esciencecenter.eastroviz.gui.VisibilityFrame;

public final class Viz {
    private static final Logger LOGGER = LoggerFactory.getLogger(Viz.class);

    public static final int REAL = 0;
    public static final int IMAG = 1;
    public static final int NR_POLARIZATIONS = 2;

    private final String fileName;

    private int maxSequenceNr = Integer.MAX_VALUE;
    private int maxSubbands = Integer.MAX_VALUE;

    private final boolean batch;
    private final boolean raw;
    private boolean intermediate = false;
    private boolean beamFormed = false;
    private boolean filtered = false;
    private boolean compressedBeamFormed = false;
    private boolean visibilities = false;
    private final int integrationFactor;
    private String flaggingType = "none";
    
    public static final class ExtFilter implements FilenameFilter {
        private final String ext;

        public ExtFilter(String ext) {
            this.ext = ext;
        }

        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(ext));
        }
    }

    public Viz(final String fileName, final boolean batch, final boolean raw, boolean visibilities, final boolean beamFormed,
            final boolean intermediate, final boolean filtered, final boolean compressedBeamFormed, final int integrationFactor,
            final int maxSeqNo, final int maxSubbands, final String flaggingType) {
        this.fileName = fileName;
        this.batch = batch;
        this.raw = raw;
        this.visibilities = visibilities;
        this.beamFormed = beamFormed;
        this.intermediate = intermediate;
        this.filtered = filtered;
        this.compressedBeamFormed = compressedBeamFormed;
        this.integrationFactor = integrationFactor;
        this.maxSequenceNr = maxSeqNo;
        this.maxSubbands = maxSubbands;
        this.flaggingType = flaggingType;
/*
        // Use the platform's native look and feel.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.info("Could not set look and feel: " + e);
            // Ignore.
        }
*/
        //Make sure we have nice window decorations.
        //        JFrame.setDefaultLookAndFeelDecorated(true);
        //        JDialog.setDefaultLookAndFeelDecorated(true);
    }

    public void start() throws IOException {
        final int station = 0;

        // TODO set flagging type for all data formats
        
        if (beamFormed) {
            final BeamFormedDataReader reader =
                    new BeamFormedDataReader(fileName, maxSequenceNr, maxSubbands, integrationFactor /* really the zoom factor in this case*/);
            BeamFormedData beamFormedData = reader.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(beamFormedData);
            beamFormedFrame.pack();

            if (batch) {
                beamFormedFrame.save("outputBeamFormed.bmp");
                System.exit(0);
            }

            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    beamFormedFrame.setVisible(true);
                }
            });

            return;
        }

        if (compressedBeamFormed) {
            final CompressedBeamFormedData compressedBeamFormedData =
                    new CompressedBeamFormedData(fileName, integrationFactor, maxSequenceNr, maxSubbands);
            compressedBeamFormedData.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(compressedBeamFormedData);
            beamFormedFrame.pack();

            if (batch) {
                beamFormedFrame.save("outputCompressedBeamFormed.bmp");
                System.exit(0);
            }

            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    beamFormedFrame.setVisible(true);
                }
            });
            return;
        }

        if (intermediate) {
            final IntermediateData intermediateData =
                    new IntermediateData(fileName, integrationFactor, maxSequenceNr, maxSubbands, station);
            intermediateData.read();

            final PreProcessedFrame intermediateFrame = new PreProcessedFrame(intermediateData);
            intermediateFrame.pack();

            if (batch) {
                intermediateFrame.save("outputIntermediate.bmp");
                System.exit(0);
            }

            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    intermediateFrame.setVisible(true);
                }
            });
            return;
        }

        if (filtered) {
            if (batch) {
                FilteredData filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, 0, 0);
                filteredData.read();
                int nrStations = filteredData.getNrStations();

                for (int s = 0; s < nrStations; s++) {
                    filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, s, 0);
                    filteredData.read();
                    filteredData.flag();
                    final PreProcessedFrame filteredFrame = new PreProcessedFrame(filteredData);
                    filteredFrame.setFlaggerType(flaggingType);
                    for (int pol = 0; pol < NR_POLARIZATIONS; pol++) {
                        filteredFrame.setPolarization(pol);
                        filteredFrame.save("outputFiltered-station-" + s + "-polarization-"
                                + filteredData.polarizationToString(pol) + ".bmp");
                    }
                }
                System.exit(0);
            } else {
                final FilteredData filteredData =
                        new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, station, 0);
                filteredData.read();

                final PreProcessedFrame filteredFrame = new PreProcessedFrame(filteredData);
                filteredFrame.pack();
                filteredFrame.setFlaggerType(flaggingType);
                
                java.awt.EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        filteredFrame.setVisible(true);
                    }
                });
                return;
            }
        }

        if (raw) {
            final int nrSubbands = 5;
            final RawData rawData = new RawData(nrSubbands);
            final RawDataReader r = new RawDataReader(fileName, nrSubbands);
            RawDataFrame f = null;
            int count = 0;

            while ((f = r.readFrame()) != null && count < 100) {
                // data is reused, so we need to copy... 
                rawData.addFrame(f.getData());
                count++;
            }

            final RawFrame rawFrame = new RawFrame(this, rawData);
            rawFrame.pack();
            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    rawFrame.setVisible(true);
                }
            });
            return;
        }

        if (visibilities) {
            if (batch) {
                // batch processing mode
                final MSMetaData meta = MSReader.getMetaData(fileName);
                final int nrStations = meta.getNrStations();
                for (int station2 = 0; station2 < nrStations; station2++) {
                    for (int station1 = 0; station1 < nrStations; station1++) {
                        final VisibilityData visibilityData =
                                new VisibilityData(fileName, station1, station2, 0, maxSequenceNr, maxSubbands);
                        visibilityData.read();
                        for (int pol = 0; pol < NR_POLARIZATIONS * NR_POLARIZATIONS; pol++) {
                            final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, pol);
                            final String fileName =
                                    "outputVisibilities-baseline-" + station1 + "-" + station2 + "-polarization-"
                                            + visibilityData.polarizationToString(pol) + ".bmp";
                            LOGGER.info("writing file: " + fileName);
                            vizFrame.save(fileName);
                            vizFrame.dispose();
                        }
                    }
                }
                System.exit(0);
            } else {
                final VisibilityData visibilityData = new VisibilityData(fileName, 1, 0, 0, maxSequenceNr, maxSubbands);
                visibilityData.read();
                final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, 0 /*pol*/);
                vizFrame.pack();
                java.awt.EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        vizFrame.setVisible(true);
                    }
                });
                return;
            }
        }

        LOGGER.info("unknwon file type!");
    }

    public static void main(final String[] args) {
        String fileName = null;
        boolean batch = false;
        boolean raw = false;
        boolean visibilities = false;
        boolean beamFormed = false;
        boolean intermediate = false;
        boolean filtered = false;
        boolean compressedBeamFormed = false;
        int maxSeqNo = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;
        int integrationFactor = 1;
        String flaggingType = "none";

        if (args.length < 1) {
            LOGGER.info("Usage: Viz [-batch] [-maxSeqNo] [-flaggingType] [-data format] <dataset directory or raw file>");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-batch")) {
                batch = true;
            } else if (args[i].equals("-raw")) {
                raw = true;
            } else if (args[i].equals("-maxSeqNo")) {
                i++;
                maxSeqNo = Integer.parseInt(args[i]);
            } else if (args[i].equals("-maxSubbands")) {
                i++;
                maxSubbands = Integer.parseInt(args[i]);
            } else if (args[i].equals("-integration")) {
                i++;
                integrationFactor = Integer.parseInt(args[i]);
            } else if (args[i].equals("-flaggingType")) {
                i++;
                flaggingType = args[i];
            } else {
                // it must be the filename
                if (fileName != null) {
                    LOGGER.info("You cannot specify the file name twice. The first one was: " + fileName
                            + ", the second one was: " + args[i]);
                    System.exit(1);
                }

                fileName = args[i];
            }
        }

        File f = new File(fileName);
        fileName = f.getPath();

        if (fileName.endsWith("compressedBeamFormed")) {
            compressedBeamFormed = true;
        } else if (fileName.endsWith("intermediate")) {
            intermediate = true;
        } else if (fileName.endsWith("filtered")) {
            filtered = true;
        } else if (fileName.endsWith("beamFormed")) {
            beamFormed = true;
        } else if (fileName.endsWith("visibilities")) {
            visibilities = true;
        }

        try {
            new Viz(fileName, batch, raw, visibilities, beamFormed, intermediate, filtered, compressedBeamFormed,
                    integrationFactor, maxSeqNo, maxSubbands, flaggingType).start();
        } catch (final IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

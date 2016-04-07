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
package nl.esciencecenter.eastroviz.dataformats.preprocessed.intermediate;

import nl.esciencecenter.eastroviz.dataformats.preprocessed.PreprocessedData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IntermediateData extends PreprocessedData {
    private static final Logger logger = LoggerFactory.getLogger(IntermediateData.class);

    public IntermediateData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands,
            final int station) {
        super(fileName, integrationFactor, maxSequenceNr, maxSubbands, new String[] { "X", "Y" }, station, 0);
    }

    @Override
    public String polarizationToString(final int pol) {
        switch (pol) {
        case 0:
            return "X";
        case 1:
            return "Y";
        default:
            return "error";
        }
    }

    @Override
    public int StringToPolarization(final String polString) {
        if (polString.equals("X")) {
            return 0;
        } else if (polString.equals("Y")) {
            return 1;
        } else {
            logger.warn("illegal polarization: " + polString);
            return -1;
        }
    }
}

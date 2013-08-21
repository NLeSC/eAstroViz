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
package nl.esciencecenter.eastroviz.gui;

public class ColorMap {
    private int[] colorMap;
    @SuppressWarnings("unused")
    private String name;

    public ColorMap(String name, int[] map) {
        this.colorMap = map.clone();
        this.name = name;
    }

    public int getColor(float min, float max, float var) {
        int cmEntries = colorMap.length;

        float result = (var - min) / (max - min);
        float rawIndex = result * cmEntries;

        if (var < min) {
            return colorMap[0];
        }

        if (var > max) {
            return colorMap[cmEntries - 1];
        }

        int iLow = (int) Math.floor(rawIndex);
        int iHigh = (int) Math.ceil(rawIndex);

        int cLow;
        if (iLow == cmEntries) {
            cLow = colorMap[cmEntries - 1];
        } else if (iLow < 0) {
            cLow = colorMap[0];
        } else {
            cLow = colorMap[iLow];
        }

        int cHigh;
        if (iHigh == cmEntries) {
            cHigh = colorMap[cmEntries - 1];
        } else if (iHigh < 0) {
            cHigh = colorMap[0];
        } else {
            cHigh = colorMap[iHigh];
        }

        float colorInterval = rawIndex - iLow;

        int red = getInterpolatedColor(cHigh >> 16, cLow >> 16, colorInterval);
        int green = getInterpolatedColor((cHigh >> 8) & 0xFF, (cLow >> 8) & 0xFF, colorInterval);
        int blue = getInterpolatedColor(cHigh & 0xFF, cLow & 0xFF, colorInterval);

        return red << 16 | green << 8 | blue;
    }

    private int getInterpolatedColor(int high, int low, float colorInterval) {
        if (low > high) {
            return (int) (high + (colorInterval * (low - high)));
        }

        if (low == high) {
            return low;
        }

        return (int) (low + (colorInterval * (high - low)));
    }

}

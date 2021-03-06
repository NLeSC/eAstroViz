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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import nl.esciencecenter.eastroviz.Viz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColorMapInterpreter {

    static final int COLOR_MAP_SIZE = 256;

    private static final Logger logger = LoggerFactory.getLogger(ColorMapInterpreter.class);

    private HashMap<String, ColorMap> colorMapMaps;

    public ColorMapInterpreter() {
        rebuild();
    }

    private void rebuild() {
        colorMapMaps = new HashMap<String, ColorMap>();

        try {
            String[] colorMapFileNames = getColorMaps();
            for (String fileName : colorMapFileNames) {
                int[] colorMap = new int[COLOR_MAP_SIZE];

                BufferedReader in = new BufferedReader(new FileReader("colormaps/" + fileName + ".ncmap"));
                String str;

                int key = 0;
                while ((str = in.readLine()) != null) {
                    String[] numbers = str.split(" ");
                    colorMap[key] =
                            Integer.parseInt(numbers[0]) << 16 | Integer.parseInt(numbers[1]) << 8 | Integer.parseInt(numbers[2]);
                    key++;
                }
                in.close();

                ColorMap m = new ColorMap(fileName, colorMap);

                colorMapMaps.put(fileName, m);
            }
        } catch (IOException e) {
            logger.warn(e.getMessage());
        }
    }

    public String[] getColorMaps() {
        final String[] ls = new File("colormaps").list(new Viz.ExtFilter("ncmap"));
        final String[] result = new String[ls.length];

        for (int i = 0; i < ls.length; i++) {
            result[i] = ls[i].split("\\.")[0];
        }

        return result;
    }

    public ColorMap getColorMap(String colorMapName) {
        if (!colorMapMaps.containsKey(colorMapName)) {
            logger.warn("Unregistered color map requested: " + colorMapName);
            colorMapMaps.get("default");
        }
        return colorMapMaps.get(colorMapName);
    }
}

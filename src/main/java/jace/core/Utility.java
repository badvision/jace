/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.core;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import jace.config.InvokableAction;
import jace.config.InvokableActionRegistry;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

/**
 * This is a set of helper functions which do not belong anywhere else.
 * Functions vary from introspection, discovery, and string/pattern matching.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Utility {

    //------------------------------ String comparators
    /**
     * Rank two strings similarity in terms of distance The lower the number,
     * the more similar these strings are to each other See:
     * http://en.wikipedia.org/wiki/Levenshtein_distance#Computing_Levenshtein_distance
     *
     * @param s
     * @param t
     * @return Distance (lower means a closer match, zero is identical)
     */
    public static int levenshteinDistance(String s, String t) {
        if (s == null || t == null || s.length() == 0 || t.length() == 0) {
            return Integer.MAX_VALUE;
        }

        s = s.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
        t = t.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
        int m = s.length();
        int n = t.length();
        int[][] dist = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            dist[i][0] = i;
        }
        for (int i = 1; i <= n; i++) {
            dist[0][i] = i;
        }
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    dist[i][j] = dist[i - 1][j - 1];
                } else {
                    int del = dist[i - 1][j] + 1;
                    int insert = dist[i][j - 1] + 1;
                    int sub = dist[i - 1][j - 1] + 1;
                    dist[i][j] = Math.min(Math.min(del, insert), sub);
                }
            }
        }
        return dist[m][n];
    }

    /**
     * Normalize distance based on longest string
     *
     * @param s
     * @param t
     * @return Similarity ranking, higher is better
     */
    public static int adjustedLevenshteinDistance(String s, String t) {
        return Math.max(s.length(), t.length()) - levenshteinDistance(s, t);
    }

    /**
     * Compare strings based on a tally of similar patterns found, using a fixed
     * search window The resulting score is heavily penalized if the strings
     * differ greatly in length This is not as efficient as levenshtein, so it's
     * only used as a tie-breaker.
     *
     * @param c1
     * @param c2
     * @param width Search window size
     * @return Overall similarity score (higher is better)
     */
    public static double rankMatch(String c1, String c2, int width) {
        double score = 0;
        String s1 = c1.toLowerCase();
        String s2 = c2.toLowerCase();
        for (int i = 0; i < s1.length() + 1 - width; i++) {
            String m = s1.substring(i, i + width);
            int j = 0;
            while ((j = s2.indexOf(m, j)) > -1) {
                score += width;
                j++;
            }
        }
        double l1 = s1.length();
        double l2 = s2.length();
        // If the two strings are equivilent in length, the score is higher
        // If the two strings are different in length, the score is adjusted lower depending on how large the difference is
        // This is offset just a hair for tuning purposes
        double adjustment = (Math.min(l1, l2) / Math.max(l1, l2)) + 0.1;
        return score * adjustment * adjustment;
    }

    private static boolean isHeadless = false;

    public static void setHeadlessMode(boolean headless) {
        isHeadless = headless;
    }

    public static boolean isHeadlessMode() {
        return isHeadless;
    }

    public static Optional<Image> loadIcon(String filename) {
        if (isHeadless) {
            return Optional.empty();
        }
        InputStream stream = Utility.class.getResourceAsStream("/jace/data/" + filename);
        if (stream == null) {
            System.err.println("Could not load icon: " + filename);
            return Optional.empty();
        }
        return Optional.of(new Image(stream));
    }

    public static Optional<Label> loadIconLabel(String filename) {
        if (isHeadless) {
            return Optional.empty();
        }
        Optional<Image> img = loadIcon(filename);
        if (img.isEmpty()) {
            return Optional.empty();
        }
        Label label = new Label() {
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Label l2) {
                    return super.equals(l2) || l2.getText().equals(getText());
                } else {
                    return super.equals(obj);
                }
            }

            @Override
            public int hashCode() {
                return getText().hashCode();
            }
        };
        label.setGraphic(new ImageView(img.get()));
        label.setAlignment(Pos.CENTER);
        label.setContentDisplay(ContentDisplay.TOP);
        label.setTextFill(Color.WHITE);
        DropShadow shadow = new DropShadow(5.0, Color.BLACK);
        label.setEffect(shadow);
        return Optional.of(label);
    }

    public static void confirm(String title, String message, Runnable accept) {
        Platform.runLater(() -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setContentText(message);
            confirm.setTitle(title);
            Optional<ButtonType> response = confirm.showAndWait();
            response.ifPresent(b -> {
                if (b.getButtonData().isDefaultButton()) {
                    (new Thread(accept)).start();
                }
            });
        });
    }

    public static void decision(String title, String message, String aLabel, String bLabel, Runnable aAction, Runnable bAction) {
        Platform.runLater(() -> {
            ButtonType buttonA = new ButtonType(aLabel, ButtonData.LEFT);
            ButtonType buttonB = new ButtonType(bLabel, ButtonData.RIGHT);

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, buttonA, buttonB);
            confirm.setTitle(title);

            Optional<ButtonType> response = confirm.showAndWait();
            response.ifPresent(b -> {
                if (b.getButtonData() == ButtonData.LEFT && aAction != null) {
                    Platform.runLater(aAction);
                } else if (b.getButtonData() == ButtonData.RIGHT && bAction != null) {
                    Platform.runLater(bAction);
                }
            });
        });
    }
    
    public static class RankingComparator implements Comparator<String> {

        String match;

        public RankingComparator(String match) {
            // Adding a space helps respect word boundaries as part of the match
            // In the case of very close matches this is another tie-breaker
            // Especially for very small search terms
            this.match = match + " ";
        }

        @Override
        public int compare(String o1, String o2) {
            double s1 = adjustedLevenshteinDistance(match, o1);
            double s2 = adjustedLevenshteinDistance(match, o2);
            if (s2 == s1) {
                s1 = rankMatch(o1, match, 3) + rankMatch(o1, match, 2);
                s2 = rankMatch(o2, match, 3) + rankMatch(o2, match, 2);
                if (s2 == s1) {
                    return (o1.compareTo(o2));
                } else {
                    // Normalize result to -1, 0 or 1 so there is no rounding issues!
                    return (int) Math.signum(s2 - s1);
                }
            } else {
                return (int) (s2 - s1);
            }
        }
    }

    /**
     * Given a desired search string and a search space of recognized
     * selections, identify the best match in the list
     *
     * @param match String to search for
     * @param search Space of all valid results
     * @return Best match found, or null if there was nothing close to a match
     * found.
     */
    public static String findBestMatch(String match, Collection<String> search) {
        if (search == null || search.isEmpty()) {
            return null;
        }
        RankingComparator r = new RankingComparator(match);
        List<String> candidates = new ArrayList<>(search);
        Collections.sort(candidates, r);
//	for (String c : candidates) {
//	    double m2 = rankMatch(c, match, 2);
//	    double m3 = rankMatch(c, match, 3);
//	    double m4 = rankMatch(c, match, 4);
//	    double l = levenshteinDistance(match, c);
//	    System.out.println(match + "->" + c + ":" + l + " -- "+ m2 + "," + m3 + "," + "(" + (m2 + m3) + ")");
//	}
//	double score = rankMatch(match, candidates.get(0), 2);
        double score = adjustedLevenshteinDistance(match, candidates.get(0));
        if (score > 1) {
            return candidates.get(0);
        }
        return null;
    }

    public static void gripe(final String message) {
        gripe(message, false, null);
    }

    public static void gripe(final String message, boolean wait, Runnable andThen) {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setContentText(message);
            errorAlert.setTitle("Error");
            if (wait) {
                errorAlert.showAndWait();
                if (andThen != null) {
                    andThen.run();
                }
            } else {
                errorAlert.show();
            }
        });
    }

    @SuppressWarnings("all")
    static Map<Class, Map<String, Object>> enumCache = new HashMap<>();

    @SuppressWarnings("all")
    public static Object findClosestEnumConstant(String value, Class type) {
        Map<String, Object> enumConstants = enumCache.get(type);
        if (enumConstants == null) {
            Object[] constants = type.getEnumConstants();
            enumConstants = new HashMap<>();
            for (Object o : constants) {
                enumConstants.put(o.toString(), o);
            }
            enumCache.put(type, enumConstants);
        }

        String key = findBestMatch(value, enumConstants.keySet());
        if (key == null) {
            return null;
        }
        return enumConstants.get(key);
    }

    @SuppressWarnings("all")
    public static Object deserializeString(String value, Class type, boolean hex) {
        int radix = hex ? 16 : 10;
        if (type.equals(Integer.TYPE) || type == Integer.class) {
            value = value.replaceAll(hex ? "[^0-9\\-A-Fa-f]" : "[^0-9\\-]", "");
            try {
                return Integer.valueOf(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Short.TYPE) || type == Short.class) {
            value = value.replaceAll(hex ? "[^0-9\\-\\.A-Fa-f]" : "[^0-9\\-\\.]", "");
            try {
                return Short.valueOf(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Long.TYPE) || type == Long.class) {
            value = value.replaceAll(hex ? "[^0-9\\-\\.A-Fa-f]" : "[^0-9\\-\\.]", "");
            try {
                return Long.valueOf(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Byte.TYPE) || type == Byte.class) {
            try {
                value = value.replaceAll(hex ? "[^0-9\\-A-Fa-f]" : "[^0-9\\-]", "");
                return Byte.valueOf(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Boolean.TYPE) || type == Boolean.class) {
            return Boolean.valueOf(value);
        } else if (type.equals(Float.TYPE) || type == Float.class) {
            return Float.parseFloat(value);
        } else if (type.equals(Double.TYPE) || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == File.class) {
            return new File(String.valueOf(value));
        } else if (type.isEnum()) {
            value = value.replaceAll("[\\.\\s\\-]", "");
            return findClosestEnumConstant(value, type);
        }
        return null;
    }

    public static Function<Boolean, Boolean> getNamedInvokableAction(String action) {
        InvokableActionRegistry registry = InvokableActionRegistry.getInstance();        
        List<InvokableAction> actionsList = new ArrayList<>(registry.getAllStaticActions());
        actionsList.sort((a, b) -> Integer.compare(getActionNameMatch(action, a), getActionNameMatch(action, b)));
//        for (InvokableAction a : actionsList) {
//            String actionName = a.alternatives() == null ? a.name() : (a.name() + ";" + a.alternatives());
//            System.out.println("Score for " + action + " evaluating " + a.name() + ": " + getActionNameMatch(action, a));
//        }
        return registry.getStaticFunction(actionsList.get(0).name());
    }

    private static int getActionNameMatch(String str, InvokableAction action) {
        int nameMatch = levenshteinDistance(str, action.name());
        if (action.alternatives() != null) {
            for (String alt : action.alternatives().split(";")) {
                nameMatch = Math.min(nameMatch, levenshteinDistance(str, alt));
            }
        }
        return nameMatch;
    }

    public static enum OS {Windows, Linux, Mac, Unknown}
    public static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return OS.Windows;
        } else if (osName.contains("linux")) {
            return OS.Linux;
        } else if (osName.contains("mac")) {
            return OS.Mac;
        } else {
            System.out.println("Unknown %s".formatted(osName));
            return OS.Unknown;
        }
    }
}

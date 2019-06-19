/*
 * Copyright (C) 2012 Brendan Robert (BLuRry) brendan.robert@gmail.com.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package jace.core;

import jace.Emulator;
import jace.config.Configuration;
import jace.config.InvokableAction;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import org.reflections.Reflections;

/**
 * This is a set of helper functions which do not belong anywhere else.
 * Functions vary from introspection, discovery, and string/pattern matching.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class Utility {

    static Reflections reflections = new Reflections("jace");

    public static Set<Class> findAllSubclasses(Class clazz) {
        return reflections.getSubTypesOf(clazz);
    }

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

    public static String join(Collection<String> c, String d) {
        return c.stream().collect(Collectors.joining(d));
    }

    private static boolean isHeadless = false;

    public static void setHeadlessMode(boolean headless) {
        isHeadless = headless;
        if (Emulator.instance == null && headless) {
            Emulator.instance = new Emulator(Collections.emptyList());
        }
    }

    public static boolean isHeadlessMode() {
        return isHeadless;
    }

    public static Optional<Image> loadIcon(String filename) {
        if (isHeadless) {
            return Optional.empty();
        }
        InputStream stream = Utility.class.getClassLoader().getResourceAsStream("jace/data/" + filename);
        return Optional.of(new Image(stream));
    }

    public static Optional<Label> loadIconLabel(String filename) {
        if (isHeadless) {
            return Optional.empty();
        }
        Image img = loadIcon(filename).get();
        Label label = new Label() {
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Label) {
                    Label l2 = (Label) obj;
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
        label.setGraphic(new ImageView(img));
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

//    public static void runModalProcess(String title, final Runnable runnable) {
////        final JDialog frame = new JDialog(Emulator.getFrame());
//        final JProgressBar progressBar = new JProgressBar();
//        progressBar.setIndeterminate(true);
//        final JPanel contentPane = new JPanel();
//        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//        contentPane.setLayout(new BorderLayout());
//        contentPane.add(new JLabel(title), BorderLayout.NORTH);
//        contentPane.add(progressBar, BorderLayout.CENTER);
//        frame.setContentPane(contentPane);
//        frame.pack();
//        frame.setLocationRelativeTo(null);
//        frame.setVisible(true);
//
//        new Thread(() -> {
//            runnable.run();
//            frame.setVisible(false);
//            frame.dispose();
//        }).start();
//    }
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

    public static void printStackTrace() {
        System.out.println("CURRENT STACK TRACE:");
        for (StackTraceElement s : Thread.currentThread().getStackTrace()) {
            System.out.println(s.getClassName() + "." + s.getMethodName() + " (line " + s.getLineNumber() + ") " + (s.isNativeMethod() ? "NATIVE" : ""));
        }
        System.out.println("END OF STACK TRACE");
    }

    public static int parseHexInt(Object s) {
        if (s == null) {
            return -1;
        }
        if (s instanceof Integer) {
            return (Integer) s;
        }
        String val = String.valueOf(s).trim();
        int base = 10;
        if (val.startsWith("$")) {
            base = 16;
            val = val.contains(" ") ? val.substring(1, val.indexOf(' ')) : val.substring(1);
        } else if (val.startsWith("0x")) {
            base = 16;
            val = val.contains(" ") ? val.substring(2, val.indexOf(' ')) : val.substring(2);
        }
        try {
            return Integer.parseInt(val, base);
        } catch (NumberFormatException ex) {
            gripe("This isn't a valid number: " + val + ".  If you put a $ in front of that then I'll know you meant it to be a hex number.");
            throw ex;
        }
    }

    public static void gripe(final String message) {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setContentText(message);
            errorAlert.setTitle("Error");
            errorAlert.show();
        });
    }

    public static Object findChild(Object object, String fieldName) {
        if (object instanceof Map) {
            Map map = (Map) object;
            for (Object key : map.keySet()) {
                if (key.toString().equalsIgnoreCase(fieldName)) {
                    return map.get(key);
                }
            }
            return null;
        }
        try {
            Field f = object.getClass().getField(fieldName);
            return f.get(object);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            for (Method m : object.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase("get" + fieldName) && m.getParameterTypes().length == 0) {
                    try {
                        return m.invoke(object, new Object[0]);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex1) {
                    }
                }
            }
        }
        return null;
    }

    public static Object setChild(Object object, String fieldName, String value, boolean hex) {
        if (object instanceof Map) {
            Map map = (Map) object;
            for (Object key : map.entrySet()) {
                if (key.toString().equalsIgnoreCase(fieldName)) {
                    map.put(key, value);
                    return null;
                }
            }
            return null;
        }
        Field f;
        try {
            f = object.getClass().getField(fieldName);
        } catch (NoSuchFieldException ex) {
            System.out.println("Object type " + object.getClass().getName() + " has no field named " + fieldName);
            Logger.getLogger(Utility.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (SecurityException ex) {
            Logger.getLogger(Utility.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        Object useValue = deserializeString(value, f.getType(), hex);
        try {
            f.set(object, useValue);
            return useValue;
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            for (Method m : object.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase("set" + fieldName) && m.getParameterTypes().length == 0) {
                    try {
                        m.invoke(object, useValue);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex1) {
                    }
                }
            }
        }
        return useValue;
    }
    static Map<Class, Map<String, Object>> enumCache = new HashMap<>();

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

    public static Object deserializeString(String value, Class type, boolean hex) {
        int radix = hex ? 16 : 10;
        if (type.equals(Integer.TYPE) || type == Integer.class) {
            value = value.replaceAll(hex ? "[^0-9\\-A-Fa-f]" : "[^0-9\\-]", "");
            try {
                return Integer.parseInt(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Short.TYPE) || type == Short.class) {
            value = value.replaceAll(hex ? "[^0-9\\-\\.A-Fa-f]" : "[^0-9\\-\\.]", "");
            try {
                return Short.parseShort(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Long.TYPE) || type == Long.class) {
            value = value.replaceAll(hex ? "[^0-9\\-\\.A-Fa-f]" : "[^0-9\\-\\.]", "");
            try {
                return Long.parseLong(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Byte.TYPE) || type == Byte.class) {
            try {
                value = value.replaceAll(hex ? "[^0-9\\-A-Fa-f]" : "[^0-9\\-]", "");
                return Byte.parseByte(value, radix);
            } catch (NumberFormatException ex) {
                return null;
            }
        } else if (type.equals(Boolean.TYPE) || type == Boolean.class) {
            return Boolean.valueOf(value);
        } else if (type == File.class) {
            return new File(String.valueOf(value));
        } else if (type.isEnum()) {
            value = value.replaceAll("[\\.\\s\\-]", "");
            return findClosestEnumConstant(value, type);
        }
        return null;
    }

    public static Object getProperty(Object object, String path) {
        String[] paths = path.split("\\.");
        for (String path1 : paths) {
            object = findChild(object, path1);
            if (object == null) {
                return null;
            }
        }
        return object;
    }

    public static Object setProperty(Object object, String path, String value, boolean hex) {
        String[] paths = path.split("\\.");
        for (int i = 0; i < paths.length - 1; i++) {
            object = findChild(object, paths[i]);
            if (object == null) {
                return null;
            }
        }
        return setChild(object, paths[paths.length - 1], value, hex);
    }

    static Map<InvokableAction, Runnable> allActions = null;

    public static Map<InvokableAction, Runnable> getAllInvokableActions() {
        if (allActions == null) {
            allActions = new HashMap<>();
            Configuration.BASE.getTreeAsStream().forEach((Configuration.ConfigNode node) -> {
                for (Method m : node.subject.getClass().getMethods()) {
                    if (m.isAnnotationPresent(InvokableAction.class)) {
                        allActions.put(m.getAnnotation(InvokableAction.class), () -> {
                            try {
                                m.invoke(node.subject);
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                Logger.getLogger(Utility.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                    }
                }
            });
        }
        return allActions;
    }

    public static Runnable getNamedInvokableAction(String action) {
        Map<InvokableAction, Runnable> actions = getAllInvokableActions();
        List<InvokableAction> actionsList = new ArrayList(actions.keySet());
        actionsList.sort((a,b) -> Integer.compare(getActionNameMatch(action, a), getActionNameMatch(action, b)));
//        for (InvokableAction a : actionsList) {
//            String actionName = a.alternatives() == null ? a.name() : (a.name() + ";" + a.alternatives());
//            System.out.println("Score for " + action + " evaluating " + a.name() + ": " + getActionNameMatch(action, a));
//        }
        return actions.get(actionsList.get(0));
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
}

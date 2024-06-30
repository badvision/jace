package jace.cheat;

import jace.core.RAMEvent;
import jace.core.RAMListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Callback;

/**
 *
 * @author blurry
 */
public class DynamicCheat extends RAMListener {
    int id;
    IntegerProperty addr;
    StringProperty expression;
    BooleanProperty active;
    StringProperty name;
    String cheatName;
    Callback<RAMEvent, Integer> expressionCallback;

    public DynamicCheat(String cheatName, int address, int holdValue) {
        super(cheatName, RAMEvent.TYPE.ANY, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY);
        id = (int) (Math.random() * 10000000);
        addr = new SimpleIntegerProperty(address);
        expression = new SimpleStringProperty(String.valueOf(holdValue));
        isHold = true;
        active = new SimpleBooleanProperty(false);
        name = new SimpleStringProperty("Untitled");
        expressionCallback = (RAMEvent e) -> holdValue;
        doConfig();
    }

    boolean isHold = false;

    @Override
    protected void doConfig() {
        if (addr != null) {
            setScopeStart(addr.getValue());
        }
    }

    @Override
    protected void doEvent(RAMEvent e) {
        if (active.get() && expressionCallback != null) {
            Integer newVal = expressionCallback.call(e);
            if (newVal != null) {
                e.setNewValue(newVal);
            } else {
                active.set(false);
                expressionCallback = null;
            }
        }
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public IntegerProperty addressProperty() {
        return addr;
    }

    public StringProperty expressionProperty() {
        return expression;
    }

    public static String escape(String in) {
        return in.replaceAll(";", "~~").replaceAll("\n","\\n");
    }
    
    public static String unescape(String in) {
        return in.replaceAll("~~", ";").replaceAll("\\n", "\n");
    }
    
    public static final String DELIMITER = ";";
    public String serialize() {
        return escape(cheatName) + DELIMITER + escape(name.get()) + DELIMITER 
                + escape("$"+Integer.toHexString(addr.get())) + DELIMITER
                + escape(expression.get());
    }

    static public DynamicCheat deserialize(String in) {
        String[] parts = in.split(DELIMITER);
        String cheatName = unescape(parts[0]);
        String name = unescape(parts[1]);
        Integer addr = Integer.parseInt(parts[2].substring(1), 16);
        String expr = unescape(parts[3]);
        
        DynamicCheat out = new DynamicCheat(cheatName, addr, Integer.parseInt(expr));
        out.name.set(name);
        return out;
    }
}

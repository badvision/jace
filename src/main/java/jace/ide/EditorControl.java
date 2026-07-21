package jace.ide;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;

public interface EditorControl {
    String getText();
    void setText(String text);
    void setSyntaxDefinition(SyntaxDefinition def);
    void clearMarkers();
    void addMarker(int line, MarkerType type, String message);
    boolean isDirty();
    void clearDirty();
    void requestFocus();
    void cut();
    void copy();
    void paste();
    void undo();
    void redo();
    ReadOnlyBooleanProperty dirtyProperty();
    ReadOnlyStringProperty textProperty();
    void showFindReplace();
    void goToLine(int lineNumber);
    void setShowLineNumbers(boolean show);
}

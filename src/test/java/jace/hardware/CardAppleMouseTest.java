package jace.hardware;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import jace.AbstractFXTest;
import jace.core.RAMEvent;
import jace.core.RAMEvent.SCOPE;
import jace.core.RAMEvent.TYPE;
import jace.core.RAMEvent.VALUE;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class CardAppleMouseTest extends AbstractFXTest {

    private CardAppleMouse cardAppleMouse;

    @Before
    public void setUp() {
        cardAppleMouse = new CardAppleMouse();
    }

    @Test
    public void testGetDeviceName() {
        assertEquals("Apple Mouse", cardAppleMouse.getDeviceName());
    }

    @Test
    public void testReset() {
        cardAppleMouse.mode = 1;
        cardAppleMouse.clampWindow = new Rectangle2D(10, 10, 100, 100);
        cardAppleMouse.detach();

        cardAppleMouse.reset();

        assertEquals(0, cardAppleMouse.mode);
        assertEquals(new Rectangle2D(0, 0, 0x03ff, 0x03ff), cardAppleMouse.clampWindow);
    }

    @Test
    // Test mouseHandler responses to mouse events
    public void testMouseHandler() {
        MouseEvent clickEvent = new MouseEvent(MouseEvent.MOUSE_CLICKED,0,0,0,0,MouseButton.PRIMARY,1,false,false,false, false, false, false, false, false, false, false, null);
        MouseEvent releaseEvent = new MouseEvent(MouseEvent.MOUSE_RELEASED,0,0,0,0,MouseButton.PRIMARY,1,false,false,false, false, false, false, false, false, false, false, null);
        MouseEvent dragEvent = new MouseEvent(MouseEvent.MOUSE_DRAGGED,0,0,0,0,MouseButton.PRIMARY,1,false,false,false, false, false, false, false, false, false, false, null);

        cardAppleMouse.mode = 1;
        cardAppleMouse.mouseHandler.handle(clickEvent);
        cardAppleMouse.mouseHandler.handle(dragEvent);
        cardAppleMouse.mouseHandler.handle(releaseEvent);
        assertEquals(1, cardAppleMouse.mode);
    }

    @Test
    // Test firmware entry points
    public void testFirmware() {
        // Test reads
        RAMEvent event = new RAMEvent(TYPE.READ, SCOPE.ANY, VALUE.ANY, 0, 0, 0);
        cardAppleMouse.handleFirmwareAccess(0x80, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x81, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x82, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x83, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x84, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x85, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x86, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x87, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x88, TYPE.EXECUTE, 0, event);
        assertEquals(0x60, event.getNewValue());
        event.setNewValue(0x00);
        cardAppleMouse.handleFirmwareAccess(0x05, TYPE.READ, 0, event);
        assertEquals(0x38, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x07, TYPE.READ, 0, event);
        assertEquals(0x18, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x08, TYPE.READ, 0, event);
        assertEquals(0x01, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x0B, TYPE.READ, 0, event);
        assertEquals(0x01, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x0C, TYPE.READ, 0, event);
        assertEquals(0x20, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x11, TYPE.READ, 0, event);
        assertEquals(0x00, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x12, TYPE.READ, 0, event);
        assertEquals(0x080, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x13, TYPE.READ, 0, event);
        assertEquals(0x081, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x14, TYPE.READ, 0, event);
        assertEquals(0x082, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x15, TYPE.READ, 0, event);
        assertEquals(0x083, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x16, TYPE.READ, 0, event);
        assertEquals(0x084, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x17, TYPE.READ, 0, event);
        assertEquals(0x085, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x18, TYPE.READ, 0, event);
        assertEquals(0x086, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x19, TYPE.READ, 0, event);
        assertEquals(0x087, event.getNewValue());
        cardAppleMouse.handleFirmwareAccess(0x1A, TYPE.READ, 0, event);
        assertEquals(0x088, event.getNewValue());
    }

}
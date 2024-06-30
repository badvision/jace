package jace.config;

import java.io.IOException;
import java.util.logging.Level;

// NOTE: This is generated code. Do not edit.
public class InvokableActionRegistryImpl extends InvokableActionRegistry {

    @Override
    public void init() {
        InvokableAction annotation;
        annotation = createInvokableAction("Resize window", "general", "Resize the screen to 1x/1.5x/2x/3x video size", "Aspect;Adjust screen;Adjust window size;Adjust aspect ratio;Fix screen;Fix window size;Fix aspect ratio;Correct aspect ratio;", true, false, new String[]{"ctrl+shift+a"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.scaleIntegerRatio();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.scaleIntegerRatio", ex);
            }
        });
        annotation = createInvokableAction("Rewind", "General", "Go back 1 second", "Timewarp", true, false, new String[]{"ctrl+shift+Open Bracket"});
        putStaticAction(annotation.name(), jace.state.StateManager.class, annotation, (b) -> {
            try {
                jace.state.StateManager.beKindRewind();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.state.StateManager.beKindRewind", ex);
            }
        });
        annotation = createInvokableAction("Configuration", "general", "Edit emulator configuraion", "Reconfigure;Preferences;Settings;Config", true, false, new String[]{"f4", "ctrl+shift+c"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.showConfig();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.showConfig", ex);
            }
        });
        annotation = createInvokableAction("Load settings", "general", "Load all configuration settings previously saved", "load preferences;revert settings;revert preferences", true, false, new String[]{"meta+ctrl+r"});
        putStaticAction(annotation.name(), jace.config.Configuration.class, annotation, (b) -> {
            try {
                jace.config.Configuration.loadSettings();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.config.Configuration.loadSettings", ex);
            }
        });
        annotation = createInvokableAction("About", "general", "Display about window", "info;credits", true, false, new String[]{"ctrl+shift+."});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.showAboutWindow();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.showAboutWindow", ex);
            }
        });
        annotation = createInvokableAction("Record sound", "sound", "Toggles recording (saving) sound output to a file", "", true, false, new String[]{"ctrl+shift+w"});
        putStaticAction(annotation.name(), jace.apple2e.Speaker.class, annotation, (b) -> {
            try {
                jace.apple2e.Speaker.toggleFileOutput();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.apple2e.Speaker.toggleFileOutput", ex);
            }
        });
        annotation = createInvokableAction("BRUN file", "file", "Loads a binary file in memory and executes it. File should end with #06xxxx, where xxxx is the start address in hex", "Execute program;Load binary;Load program;Load rom;Play single-load game", true, false, new String[]{"ctrl+shift+b"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.runFile();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.runFile", ex);
            }
        });
        annotation = createInvokableAction("Save Raw Screenshot", "general", "Save raw (RAM) format of visible screen", "screendump;raw screenshot", true, false, new String[]{"ctrl+shift+z"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.saveScreenshotRaw();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.saveScreenshotRaw", ex);
            }
        });
        annotation = createInvokableAction("Save settings", "general", "Save all configuration settings as defaults", "save preferences;save defaults", true, false, new String[]{"meta+ctrl+s"});
        putStaticAction(annotation.name(), jace.config.Configuration.class, annotation, (b) -> {
            try {
                jace.config.Configuration.saveSettings();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.config.Configuration.saveSettings", ex);
            }
        });
        annotation = createInvokableAction("Toggle fullscreen", "general", "Activate/deactivate fullscreen mode", "fullscreen;maximize", true, false, new String[]{"ctrl+shift+f"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.toggleFullscreen();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.toggleFullscreen", ex);
            }
        });
        annotation = createInvokableAction("Toggle Debug", "debug", "Show/hide the debug panel", "Show Debug;Hide Debug;Inspect", true, false, new String[]{"ctrl+shift+d"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.toggleDebugPanel();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.toggleDebugPanel", ex);
            }
        });
        annotation = createInvokableAction("Refresh screen", "display", "Marks screen contents as changed, forcing full screen redraw", "redraw", true, false, new String[]{"ctrl+shift+r"});
        putStaticAction(annotation.name(), jace.core.Video.class, annotation, (b) -> {
            try {
                jace.core.Video.forceRefresh();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Video.forceRefresh", ex);
            }
        });
        annotation = createInvokableAction("Toggle video mode", "video", "", "Gfx mode;color;b&w;monochrome", true, false, new String[]{"ctrl+shift+g"});
        putStaticAction(annotation.name(), jace.apple2e.VideoNTSC.class, annotation, (b) -> {
            try {
                jace.apple2e.VideoNTSC.changeVideoMode();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.apple2e.VideoNTSC.changeVideoMode", ex);
            }
        });
        annotation = createInvokableAction("Save Screenshot", "general", "Save image of visible screen", "Save image;save framebuffer;screenshot", true, false, new String[]{"ctrl+shift+s"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.saveScreenshot();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.saveScreenshot", ex);
            }
        });
        annotation = createInvokableAction("Paste clipboard", "Keyboard", "", "paste", true, false, new String[]{"Ctrl+Shift+V", "Shift+Insert"});
        putStaticAction(annotation.name(), jace.core.Keyboard.class, annotation, (b) -> {
            try {
                jace.core.Keyboard.pasteFromClipboard();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Keyboard.pasteFromClipboard", ex);
            }
        });
        annotation = createInvokableAction("Open IDE", "development", "Open new IDE window for Basic/Assembly/Plasma coding", "IDE;dev;development;acme;assembler;editor", true, false, new String[]{"ctrl+shift+i"});
        putStaticAction(annotation.name(), jace.EmulatorUILogic.class, annotation, (b) -> {
            try {
                jace.EmulatorUILogic.showIDE();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.EmulatorUILogic.showIDE", ex);
            }
        });
        annotation = createInvokableAction("Up", "joystick", "", "", true, true, new String[]{"up"});
        putInstanceAction(annotation.name(), jace.hardware.Joystick.class, annotation, (o, b) -> {
            try {
                return ((jace.hardware.Joystick) o).joystickUp(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.hardware.Joystick.joystickUp", ex);
                return false;
            }
        });
        annotation = createInvokableAction("Open Apple Key", "Keyboard", "", "OA", false, true, new String[]{"Alt"});
        putInstanceAction(annotation.name(), jace.core.Keyboard.class, annotation, (o, b) -> {
            try {
                ((jace.core.Keyboard) o).openApple(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Keyboard.openApple", ex);
            }
        });
        annotation = createInvokableAction("Left", "joystick", "", "", true, true, new String[]{"left"});
        putInstanceAction(annotation.name(), jace.hardware.Joystick.class, annotation, (o, b) -> {
            try {
                return ((jace.hardware.Joystick) o).joystickLeft(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.hardware.Joystick.joystickLeft", ex);
                return false;
            }
        });
        annotation = createInvokableAction("Right", "joystick", "", "", true, true, new String[]{"right"});
        putInstanceAction(annotation.name(), jace.hardware.Joystick.class, annotation, (o, b) -> {
            try {
                return ((jace.hardware.Joystick) o).joystickRight(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.hardware.Joystick.joystickRight", ex);
                return false;
            }
        });
        annotation = createInvokableAction("Closed Apple Key", "Keyboard", "", "CA", false, true, new String[]{"Shortcut", "Meta", "Command"});
        putInstanceAction(annotation.name(), jace.core.Keyboard.class, annotation, (o, b) -> {
            try {
                ((jace.core.Keyboard) o).solidApple(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Keyboard.solidApple", ex);
            }
        });
        annotation = createInvokableAction("Down", "joystick", "", "", true, true, new String[]{"down"});
        putInstanceAction(annotation.name(), jace.hardware.Joystick.class, annotation, (o, b) -> {
            try {
                return ((jace.hardware.Joystick) o).joystickDown(b);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.hardware.Joystick.joystickDown", ex);
                return false;
            }
        });
        annotation = createInvokableAction("Pause", "General", "Stops the computer, allowing reconfiguration of core elements", "freeze;halt", true, false, new String[]{"meta+pause", "alt+pause"});
        putInstanceAction(annotation.name(), jace.core.Computer.class, annotation, (o, b) -> {
            try {
                return ((jace.core.Computer) o).pause();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Computer.pause", ex);
                return false;
            }
        });
        annotation = createInvokableAction("Reset", "general", "Process user-initatiated reboot (ctrl+apple+reset)", "reboot;reset;three-finger-salute;restart", true, false, new String[]{"Ctrl+Ignore Alt+Ignore Meta+Backspace", "Ctrl+Ignore Alt+Ignore Meta+Delete"});
        putInstanceAction(annotation.name(), jace.core.Computer.class, annotation, (o, b) -> {
            try {
                ((jace.core.Computer) o).invokeReset();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Computer.invokeWarmStart", ex);
            }
        });
        annotation = createInvokableAction("Toggle Cheats", "General", "", "cheat;Plug-in", true, false, new String[]{"ctrl+shift+m"});
        putInstanceAction(annotation.name(), jace.cheat.Cheats.class, annotation, (o, b) -> {
            try {
                ((jace.cheat.Cheats) o).toggleCheats();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.cheat.Cheats.toggleCheats", ex);
            }
        });
        annotation = createInvokableAction("Resume", "General", "Resumes the computer if it was previously paused", "unpause;unfreeze;resume;play", true, false, new String[]{"meta+shift+pause", "alt+shift+pause"});
        putInstanceAction(annotation.name(), jace.core.Computer.class, annotation, (o, b) -> {
            try {
                ((jace.core.Computer) o).resume();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error invoking jace.core.Computer.resume", ex);
            }
        });
    }
}

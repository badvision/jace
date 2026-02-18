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

package jace.hardware;

import jace.Emulator;
import jace.EmulatorUILogic;
import jace.config.ConfigurableField;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import jace.core.Utility;
import jace.hardware.mbf.MicrosoftBinaryFormat;
import jace.state.Stateful;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import java.util.Optional;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * MegaFlash Bluetooth Mouse Simulator Card
 * Simulates the MegaFlash card's Bluetooth mouse interface for testing
 * without physical hardware. Implements all 12 mouse commands (0x60-0x6B).
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Stateful
@Name("MegaFlash")
public class CardMegaFlash extends Card {

    // ==================== Configuration ====================
    @ConfigurableField(name = "Debug Output", defaultValue = "false", description = "Enable debug logging for MegaFlash FPU operations")
    public boolean debugOutput = false;

    // ==================== Command Codes ====================
    // Mouse commands (0x60-0x6B)
    private static final int CMD_MOUSE_INIT = 0x60;
    private static final int CMD_MOUSE_READ = 0x61;
    private static final int CMD_MOUSE_STATUS = 0x62;
    private static final int CMD_MOUSE_CONFIG = 0x63;
    private static final int CMD_MOUSE_PAIR_START = 0x64;
    private static final int CMD_MOUSE_PAIR_CANCEL = 0x65;
    private static final int CMD_MOUSE_DISCONNECT = 0x66;
    private static final int CMD_MOUSE_CLAMP = 0x67;
    private static final int CMD_MOUSE_LIST_DEVICES = 0x68;
    private static final int CMD_MOUSE_SCAN_START = 0x69;
    private static final int CMD_MOUSE_SCAN_STOP = 0x6A;
    private static final int CMD_MOUSE_REMOVE_DEVICE = 0x6B;

    // Math accelerator commands (0x30-0x3C)
    // Binary operations
    private static final int CMD_FADD = 0x30;
    private static final int CMD_FMUL = 0x31;
    private static final int CMD_FDIV = 0x32;

    // Unary operations
    private static final int CMD_FSIN = 0x33;
    private static final int CMD_FCOS = 0x34;
    private static final int CMD_FTAN = 0x35;
    private static final int CMD_FATN = 0x36;
    private static final int CMD_FLOG = 0x37;
    private static final int CMD_FEXP = 0x38;
    private static final int CMD_FSQR = 0x39;

    // I/O operations
    private static final int CMD_FOUT = 0x3A;
    private static final int CMD_FMUL10 = 0x3B;
    private static final int CMD_FDIV10 = 0x3C;

    // ==================== Error Codes ====================
    private static final int MFERR_NONE = 0x00;
    private static final int MFERR_NOTPICOW = 0x02;
    private static final int MFERR_UNKNOWNCMD = 0x03;
    private static final int MFERR_INVALIDWEKEY = 0x04;
    private static final int MFERR_INVALIDARG = 0x0A;
    private static final int MFERR_BTNOTCONNECTED = 0x0C;
    private static final int MFERR_BTPAIRFAILED = 0x0D;

    // Math error codes (returned in result byte 0, not statusReg)
    private static final int MATHERR_OVERFLOW = 0x80;
    private static final int MATHERR_DIV0 = 0x40;
    private static final int MATHERR_IQERROR = 0x20;
    private static final int MATHERR_NONE = 0x00;

    // ==================== Other Constants ====================
    private static final int WRITEENABLEKEY = 0x71;
    private static final int BUSYFLAG = 0x80;
    private static final int ERRORFLAG = 0x40;

    // ==================== Register State ====================
    @Stateful
    private int statusReg;
    @Stateful
    private byte[] paramBuffer = new byte[256];
    @Stateful
    private int paramPointer;
    @Stateful
    private byte[] dataBuffer = new byte[512];
    @Stateful
    private int dataPointer;

    // ==================== Mouse State ====================
    @Stateful
    private boolean initialized;
    @Stateful
    private boolean isPaired;
    @Stateful
    private boolean isConnected;
    @Stateful
    private boolean isPairing;
    @Stateful
    private int pairingAttemptCount;
    @Stateful
    private long pairingStartTime;

    // Configuration state
    @Stateful
    private int sensitivity = 128; // Default 1.0x
    @Stateful
    private int accelerationMode = 2; // Default medium
    @Stateful
    private int buttonMapping = 0; // Default mapping
    @Stateful
    private int clampMinX = 0;
    @Stateful
    private int clampMaxX = 639;
    @Stateful
    private int clampMinY = 0;
    @Stateful
    private int clampMaxY = 191;

    // Mouse delta accumulation (simulates what the Pi Pico would do)
    @Stateful
    private int accumulatedDeltaX;
    @Stateful
    private int accumulatedDeltaY;
    @Stateful
    private Point2D lastMouseLocation = new Point2D(0, 0);
    @Stateful
    private boolean button0Pressed;
    @Stateful
    private boolean button1Pressed;
    @Stateful
    private boolean button2Pressed;

    // Device info
    @Stateful
    private String deviceName = "Jace Simulator Mouse";
    @Stateful
    private byte[] deviceMAC = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};

    // ID register state for detection (hardware initial value: 0x96)
    @Stateful
    private int idRegisterValue = 0x96;

    // UI indicator icon
    private Optional<Label> indicatorIcon;

    // JavaFX mouse handler
    private EventHandler<MouseEvent> mouseHandler = this::processMouseEvent;

    public CardMegaFlash() {
        super(false);
        if (debugOutput) {
            System.out.println("=== MegaFlash Card Initialized (Math Accelerator v2.0) ===");
        }
        indicatorIcon = Utility.loadIconLabel("megaflash.png");
        reset();
    }

    @Override
    public void setSlot(int slot) {
        super.setSlot(slot);
        if (debugOutput) {
            int baseIO = 0x0c080 + slot * 16;
            System.out.println("MegaFlash Card: Assigned to slot " + slot + ", I/O addresses $" + String.format("%04X", baseIO) + "-$" + String.format("%04X", baseIO + 15));
        }
    }

    @Override
    public void attach() {
        if (debugOutput) {
            System.out.println("MegaFlash Card: attach() called for slot " + getSlot());

            // DIAGNOSTIC: Verify card registration and identity check
            var registeredCard = getMemory().getCard(getSlot());
            System.out.println("MegaFlash Card: Registered card in slot " + getSlot() + " = " +
                (registeredCard.isPresent() ? registeredCard.get().getClass().getSimpleName() : "EMPTY"));
            System.out.println("MegaFlash Card: Identity check (this == registered): " +
                registeredCard.map(c -> c == this).orElse(false));
            System.out.println("MegaFlash Card: this object = " + System.identityHashCode(this));
            if (registeredCard.isPresent()) {
                System.out.println("MegaFlash Card: registered object = " + System.identityHashCode(registeredCard.get()));
            }
        }

        super.attach();

        if (debugOutput) {
            // DIAGNOSTIC: Check if listener was actually registered
            System.out.println("MegaFlash Card: attach() completed");
            var registeredCard = getMemory().getCard(getSlot());
            System.out.println("MegaFlash Card: After attach, identity check = " +
                registeredCard.map(c -> c == this).orElse(false));
        }
    }

    @Override
    public String getDeviceName() {
        return "MegaFlash";
    }

    @Override
    public void reset() {
        statusReg = MFERR_NONE;
        paramPointer = 0;
        dataPointer = 0;
        initialized = false;
        isPaired = false;
        isConnected = false;
        isPairing = false;
        pairingAttemptCount = 0;
        accumulatedDeltaX = 0;
        accumulatedDeltaY = 0;
        lastMouseLocation = new Point2D(0, 0);
        button0Pressed = false;
        button1Pressed = false;
        button2Pressed = false;
        idRegisterValue = 0x96;  // Reset to hardware initial value
        EmulatorUILogic.removeMouseListener(mouseHandler);
        EmulatorUILogic.removeIndicators(this);
    }

    // ==================== Register Interface ====================

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        // Show activity indicator on any I/O access
        indicatorIcon.ifPresent(icon -> EmulatorUILogic.addIndicator(this, icon, 100));  // 100ms TTL

        value = value & 0xFF;  // Normalize to 0-255 range
        switch (register) {
            case 0x00: // CMDREG (write) / STATUSREG (read)
                if (type == TYPE.WRITE) {
                    if (debugOutput) {
                        System.out.println("MegaFlash: Command write 0x" + String.format("%02X", value & 0xFF));
                    }
                    handleCommand(value);
                } else if (type.isRead()) {
                    if (debugOutput) {
                        System.out.println("MegaFlash: Status read 0x" + String.format("%02X", statusReg & 0xFF));
                    }
                    e.setNewValue(statusReg & 0xFF);
                }
                break;

            case 0x01: // PARAMREG (read/write with auto-increment)
                if (type == TYPE.WRITE) {
                    if (debugOutput) {
                        System.out.println("MegaFlash: PARAMREG[" + paramPointer + "] write 0x" + String.format("%02X", value & 0xFF));
                    }
                    paramBuffer[paramPointer] = (byte) value;
                    paramPointer = (paramPointer + 1) & 0xFF;
                } else if (type.isRead()) {
                    if (debugOutput) {
                        System.out.println("MegaFlash: PARAMREG[" + paramPointer + "] read 0x" + String.format("%02X", paramBuffer[paramPointer] & 0xFF));
                    }
                    e.setNewValue(paramBuffer[paramPointer] & 0xFF);
                    paramPointer = (paramPointer + 1) & 0xFF;
                }
                break;

            case 0x02: // DATAREG (read/write with auto-increment)
                if (type == TYPE.WRITE) {
                    dataBuffer[dataPointer] = (byte) value;
                    dataPointer = (dataPointer + 1) & 0x1FF;
                } else if (type.isRead()) {
                    e.setNewValue(dataBuffer[dataPointer] & 0xFF);
                    dataPointer = (dataPointer + 1) & 0x1FF;
                }
                break;

            case 0x03: // IDREG (read only) - returns value then flips to bitwise NOT
                if (type.isRead()) {
                    e.setNewValue(idRegisterValue);
                    idRegisterValue = ~idRegisterValue & 0xFF;  // Bitwise NOT (hardware behavior)
                }
                break;
        }
    }

    // ==================== Command Dispatcher ====================

    private void handleCommand(int commandCode) {
        // Set BUSY flag when command starts
        statusReg = BUSYFLAG;

        // Reset param pointer for output
        paramPointer = 0;
        dataPointer = 0;

        switch (commandCode) {
            // Math accelerator commands - Binary operations
            case CMD_FADD:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FADD command (0x30)");
                }
                cmdFAdd();
                break;
            case CMD_FMUL:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FMUL command (0x31)");
                }
                cmdFMul();
                break;
            case CMD_FDIV:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FDIV command (0x32)");
                }
                cmdFDiv();
                break;

            // Math accelerator commands - Unary operations
            case CMD_FSIN:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FSIN command (0x33)");
                }
                cmdFSin();
                break;
            case CMD_FCOS:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FCOS command (0x34)");
                }
                cmdFCos();
                break;
            case CMD_FTAN:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FTAN command (0x35)");
                }
                cmdFTan();
                break;
            case CMD_FATN:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FATN command (0x36)");
                }
                cmdFAtn();
                break;
            case CMD_FLOG:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FLOG command (0x37)");
                }
                cmdFLog();
                break;
            case CMD_FEXP:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FEXP command (0x38)");
                }
                cmdFExp();
                break;
            case CMD_FSQR:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FSQR command (0x39)");
                }
                cmdFSqr();
                break;

            // Math accelerator commands - I/O operations
            case CMD_FOUT:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received FOUT command (0x3A)");
                }
                cmdFOut();
                break;
            case CMD_FMUL10:
            case CMD_FDIV10:
                if (debugOutput) {
                    System.out.println("MegaFlash FPU: Received undefined command (0x" + String.format("%02X", commandCode) + ")");
                }
                statusReg = MFERR_UNKNOWNCMD;
                break;

            // Mouse commands
            case CMD_MOUSE_INIT:
                cmdMouseInit();
                break;
            case CMD_MOUSE_READ:
                cmdMouseRead();
                break;
            case CMD_MOUSE_STATUS:
                cmdMouseStatus();
                break;
            case CMD_MOUSE_CONFIG:
                cmdMouseConfig();
                break;
            case CMD_MOUSE_PAIR_START:
                cmdMousePairStart();
                break;
            case CMD_MOUSE_PAIR_CANCEL:
                cmdMousePairCancel();
                break;
            case CMD_MOUSE_DISCONNECT:
                cmdMouseDisconnect();
                break;
            case CMD_MOUSE_CLAMP:
                cmdMouseClamp();
                break;
            case CMD_MOUSE_LIST_DEVICES:
                cmdMouseListDevices();
                break;
            case CMD_MOUSE_SCAN_START:
                cmdMouseScanStart();
                break;
            case CMD_MOUSE_SCAN_STOP:
                cmdMouseScanStop();
                break;
            case CMD_MOUSE_REMOVE_DEVICE:
                cmdMouseRemoveDevice();
                break;
            default:
                statusReg = MFERR_UNKNOWNCMD;
                break;
        }
    }

    // ==================== Command Implementations ====================

    // ==================== Math Accelerator Commands ====================

    /**
     * CMD_FOUT (0x3A): Format floating-point number as Applesoft BASIC string
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = string length, dataBuffer[0-n] = formatted string
     *
     * Formats the floating-point number in Applesoft BASIC style:
     * - Use scientific notation for |value| > 999999 or |value| < 0.01
     * - Format: "-1.23456E+12" or "123.456"
     * - Special case: 0 formats as "0"
     */
    private void cmdFOut() {
        // Read FAC from paramBuffer[0-6]
        double value = jace.hardware.mbf.MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FOUT");
            System.out.println("  Value = " + value);
        }

        // Format the value as Applesoft BASIC string
        String formatted = formatApplesoftNumber(value);

        if (debugOutput) {
            System.out.println("  Formatted = \"" + formatted + "\" (length: " + formatted.length() + ")");
        }

        // Write string length to paramBuffer[0]
        paramBuffer[0] = (byte) Math.min(formatted.length(), 32);

        // Write formatted string to dataBuffer (up to 32 bytes)
        for (int i = 0; i < Math.min(formatted.length(), 32); i++) {
            dataBuffer[i] = (byte) formatted.charAt(i);
        }

        statusReg = MFERR_NONE;
    }

    /**
     * Format a double value as an Applesoft BASIC numeric string.
     * Matches the formatting rules used by Applesoft BASIC:
     * - Integers format without decimal point (e.g., "1", "42")
     * - Decimals format with minimal precision (e.g., "123.45")
     * - Large/small values use scientific notation (e.g., "1.23456E+10")
     *
     * @param value the value to format
     * @return formatted string
     */
    private String formatApplesoftNumber(double value) {
        // Special case: zero
        if (value == 0.0) {
            return "0";
        }

        double absValue = Math.abs(value);

        // Use scientific notation for very large or very small values
        if (absValue > 999999.0 || (absValue < 0.0001 && absValue != 0.0)) {
            return formatScientific(value);
        }

        // Format as decimal
        String result = formatDecimal(value);
        return result;
    }

    /**
     * Format a number in scientific notation (e.g., "1.23456E+10")
     *
     * @param value the value to format
     * @return formatted string in scientific notation
     */
    private String formatScientific(double value) {
        // Get the sign
        String sign = value < 0 ? "-" : "";
        double absValue = Math.abs(value);

        // Calculate exponent
        int exponent = (int) Math.floor(Math.log10(absValue));

        // Calculate mantissa (normalized to [1.0, 10.0))
        double mantissa = absValue / Math.pow(10, exponent);

        // Format mantissa with appropriate precision
        String mantissaStr;
        if (mantissa == Math.floor(mantissa)) {
            mantissaStr = String.format("%.0f", mantissa);
        } else {
            mantissaStr = String.format("%.5f", mantissa).replaceAll("0+$", "");
            if (mantissaStr.endsWith(".")) {
                mantissaStr = mantissaStr.substring(0, mantissaStr.length() - 1);
            }
        }

        // Format exponent with sign
        String expSign = exponent >= 0 ? "+" : "";
        return sign + mantissaStr + "E" + expSign + exponent;
    }


    // ==================== Binary Math Operations (Stubs - awaiting Agent 2/3 implementation) ====================

    /**
     * CMD_FADD (0x30): Floating-point addition - FAC + ARG -> FAC
     * STUB: Awaiting Agent 2 implementation
     */
    private void cmdFAdd() {
        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FADD");
            System.out.print("  ParamBuffer[0-13] = ");
            for (int i = 0; i < 14; i++) {
                System.out.print(String.format("%02X ", paramBuffer[i] & 0xFF));
            }
            System.out.println();
        }

        // Read FAC from paramBuffer[0-6]
        double fac = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        // Read ARG from paramBuffer[7-13]
        double arg = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 7);

        if (debugOutput) {
            System.out.println("  FAC (decoded) = " + fac);
            System.out.println("  ARG (decoded) = " + arg);
        }

        // Perform addition (ARG + FAC)
        double result = arg + fac;

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeMathResult(result);
    }

    /**
     * CMD_FMUL (0x31): Floating-point multiplication (ARG * FAC)
     */
    private void cmdFMul() {
        if (debugOutput) {
            // Diagnostic: Dump first 14 bytes of paramBuffer
            System.out.println("MegaFlash FPU: Executing FMUL");
            System.out.print("  ParamBuffer[0-13] = ");
            for (int i = 0; i < 14; i++) {
                System.out.print(String.format("%02X ", paramBuffer[i] & 0xFF));
            }
            System.out.println();
        }

        // Read FAC from paramBuffer[0-6]
        double fac = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        // Read ARG from paramBuffer[7-13]
        double arg = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 7);

        if (debugOutput) {
            System.out.println("  FAC = " + fac + ", ARG = " + arg);
        }

        // Perform multiplication (ARG * FAC)
        double result = arg * fac;

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeMathResult(result);
    }

    /**
     * CMD_FDIV (0x32): Floating-point division (FAC / ARG)
     * Hardware operation: divides FAC by ARG
     */
    private void cmdFDiv() {
        if (debugOutput) {
            // Diagnostic: Dump first 14 bytes of paramBuffer
            System.out.println("MegaFlash FPU: Executing FDIV");
            System.out.print("  ParamBuffer[0-13] = ");
            for (int i = 0; i < 14; i++) {
                System.out.print(String.format("%02X ", paramBuffer[i] & 0xFF));
            }
            System.out.println();
        }

        // Read FAC from paramBuffer[0-6]
        double fac = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        // Read ARG from paramBuffer[7-13]
        double arg = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 7);

        if (debugOutput) {
            System.out.println("  FAC = " + fac + ", ARG = " + arg);
        }

        // Check for division by zero (ARG is the divisor)
        if (Math.abs(arg) < 1e-30) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_DIV0) + " (Division by zero)");
            }
            // Division by zero error
            paramBuffer[0] = (byte) MATHERR_DIV0;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            // Reset pointer for reading
            paramPointer = 0;
            statusReg = MFERR_NONE;
            return;
        }

        // Perform division: FAC / ARG (matches Pi Pico hardware)
        double result = fac / arg;

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeMathResult(result);
    }

    /**
     * Helper method to write math operation results to paramBuffer.
     * Handles overflow detection and error code generation.
     */
    private void writeMathResult(double result) {
        // Check for NaN or Infinity (overflow)
        if (Double.isNaN(result) || Double.isInfinite(result) || MicrosoftBinaryFormat.isOverflow(result)) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_OVERFLOW) + " (Overflow)");
            }
            // Write overflow error
            paramBuffer[0] = (byte) MATHERR_OVERFLOW;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
        } else {
            if (debugOutput) {
                System.out.println("  Error = 0x00 (Success)");
            }
            // Write success error code
            paramBuffer[0] = (byte) MATHERR_NONE;

            // Convert result to MBF and write to paramBuffer[1-7]
            byte[] resultBytes = new byte[7];
            MicrosoftBinaryFormat.doubleToMbf(result, resultBytes, 0);
            for (int i = 0; i < 7; i++) {
                paramBuffer[1 + i] = resultBytes[i];
            }
        }

        // Reset pointer for reading
        paramPointer = 0;

        // statusReg remains MFERR_NONE (math errors go in result byte 0)
        statusReg = MFERR_NONE;
    }
    private String formatDecimal(double value) {
        // Check if it's an integer value
        if (value == Math.floor(value)) {
            // Format as integer (no decimal point)
            return String.format("%.0f", value);
        }

        // Format with appropriate decimal places, removing trailing zeros
        String result = String.format("%.6f", value);
        result = result.replaceAll("0+$", "");
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }


    // ==================== Unary Math Operations ====================

    /**
     * CMD_FSIN (0x33): Compute sine of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     */
    private void cmdFSin() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FSIN");
            System.out.println("  Operand = " + operand);
        }

        // Compute sine
        double result = Math.sin(operand);

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FCOS (0x34): Compute cosine of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     */
    private void cmdFCos() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FCOS");
            System.out.println("  Operand = " + operand);
        }

        // Compute cosine
        double result = Math.cos(operand);

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FTAN (0x35): Compute tangent of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     *
     * Special handling: tan(π/2) and near π/2 values cause overflow
     */
    private void cmdFTan() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FTAN");
            System.out.println("  Operand = " + operand);
        }

        // Check for overflow near π/2 (where tan approaches infinity)
        // Hardware threshold: check if cos(x) is very close to zero
        double cosValue = Math.cos(operand);
        if (Math.abs(cosValue) < 1e-10) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_OVERFLOW) + " (Overflow at π/2)");
            }
            // Overflow condition
            paramPointer = 0;
            paramBuffer[0] = (byte) MATHERR_OVERFLOW;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            statusReg = MFERR_NONE;
            return;
        }

        // Compute tangent
        double result = Math.tan(operand);

        // Check for overflow in result
        if (Double.isNaN(result) || Double.isInfinite(result) || MicrosoftBinaryFormat.isOverflow(result)) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_OVERFLOW) + " (Overflow)");
            }
            paramPointer = 0;
            paramBuffer[0] = (byte) MATHERR_OVERFLOW;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            statusReg = MFERR_NONE;
            return;
        }

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FATN (0x36): Compute arctangent of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     *
     * Returns angle in radians in range [-π/2, π/2]
     */
    private void cmdFAtn() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FATN");
            System.out.println("  Operand = " + operand);
        }

        // Compute arctangent
        double result = Math.atan(operand);

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FLOG (0x37): Compute natural logarithm of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     *
     * Domain: operand must be > 0
     * Returns IQERROR for operand <= 0
     */
    private void cmdFLog() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FLOG");
            System.out.println("  Operand = " + operand);
        }

        // Check domain: operand must be > 0
        if (operand <= 0.0) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_IQERROR) + " (Invalid operand <= 0)");
            }
            paramPointer = 0;
            paramBuffer[0] = (byte) MATHERR_IQERROR;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            statusReg = MFERR_NONE;
            return;
        }

        // Compute natural logarithm
        double result = Math.log(operand);

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FEXP (0x38): Compute exponential e^FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     *
     * Returns OVERFLOW for excessively large inputs
     */
    private void cmdFExp() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FEXP");
            System.out.println("  Operand = " + operand);
        }

        // Compute exponential
        double result = Math.exp(operand);

        // Check for overflow
        if (Double.isInfinite(result) || Double.isNaN(result) || MicrosoftBinaryFormat.isOverflow(result)) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_OVERFLOW) + " (Overflow)");
            }
            paramPointer = 0;
            paramBuffer[0] = (byte) MATHERR_OVERFLOW;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            statusReg = MFERR_NONE;
            return;
        }

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * CMD_FSQR (0x39): Compute square root of FAC
     * Input: paramBuffer[0-6] = FAC (7-byte MBF format)
     * Output: paramBuffer[0] = error code, paramBuffer[1-7] = result in MBF format
     *
     * Domain: operand must be >= 0
     * Returns IQERROR for negative operands
     */
    private void cmdFSqr() {
        // Read FAC from paramBuffer[0-6]
        double operand = MicrosoftBinaryFormat.mbfToDouble(paramBuffer, 0);

        if (debugOutput) {
            System.out.println("MegaFlash FPU: Executing FSQR");
            System.out.println("  Operand = " + operand);
        }

        // Check domain: operand must be >= 0
        if (operand < 0.0) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_IQERROR) + " (Negative operand)");
            }
            paramPointer = 0;
            paramBuffer[0] = (byte) MATHERR_IQERROR;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
            statusReg = MFERR_NONE;
            return;
        }

        // Compute square root
        double result = Math.sqrt(operand);

        if (debugOutput) {
            System.out.println("  Result = " + result);
        }

        // Write result
        writeUnaryResult(result);
    }

    /**
     * Helper method to write unary operation result to paramBuffer
     * Format: Byte 0 = error code, Bytes 1-7 = result in MBF format
     */
    private void writeUnaryResult(double result) {
        // Check for NaN or Infinity (overflow)
        if (Double.isNaN(result) || Double.isInfinite(result) || MicrosoftBinaryFormat.isOverflow(result)) {
            if (debugOutput) {
                System.out.println("  Error = 0x" + String.format("%02X", MATHERR_OVERFLOW) + " (Overflow)");
            }
            // Write overflow error
            paramBuffer[0] = (byte) MATHERR_OVERFLOW;
            // Write zero result
            for (int i = 1; i < 8; i++) {
                paramBuffer[i] = 0;
            }
        } else {
            if (debugOutput) {
                System.out.println("  Error = 0x00 (Success)");
            }
            // Write success error code
            paramBuffer[0] = (byte) MATHERR_NONE;

            // Convert result to MBF and write to paramBuffer[1-7]
            byte[] resultBytes = new byte[7];
            MicrosoftBinaryFormat.doubleToMbf(result, resultBytes, 0);
            for (int i = 0; i < 7; i++) {
                paramBuffer[1 + i] = resultBytes[i];
            }
        }

        // Reset pointer for reading
        paramPointer = 0;

        // statusReg remains MFERR_NONE (math errors go in result byte 0)
        statusReg = MFERR_NONE;
    }

    // ==================== Mouse Commands ====================

    /**
     * CMD_MOUSE_INIT (0x60): Initialize Bluetooth mouse subsystem
     * Returns: 0x00 = Success (simulated Pico W)
     */
    private void cmdMouseInit() {
        if (initialized) {
            paramBuffer[0] = 0x03; // Already initialized
        } else {
            paramBuffer[0] = 0x00; // Success
            initialized = true;
        }
        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_READ (0x61): Read mouse deltas and button states
     * Returns: deltaX, deltaY, buttons, statusFlags
     *
     * This is the most important command - atomically reads and clears accumulated deltas
     */
    private void cmdMouseRead() {
        if (!isConnected) {
            // Not connected - return zeros
            paramBuffer[0] = 0; // deltaX
            paramBuffer[1] = 0; // deltaY
            paramBuffer[2] = 0; // buttons
            paramBuffer[3] = 0; // status flags (bit 6 = 0, not connected)
        } else {
            // Clamp accumulated deltas to 8-bit signed range [-127, +127]
            int dx = clampDelta(accumulatedDeltaX);
            int dy = clampDelta(accumulatedDeltaY);

            // Check for overflow
            boolean overflow = (accumulatedDeltaX > 127 || accumulatedDeltaX < -127 ||
                              accumulatedDeltaY > 127 || accumulatedDeltaY < -127);

            // Clear accumulated deltas (read-and-clear semantics)
            accumulatedDeltaX = 0;
            accumulatedDeltaY = 0;

            // Pack results
            paramBuffer[0] = (byte) dx;
            paramBuffer[1] = (byte) dy;
            paramBuffer[2] = (byte) (
                (button0Pressed ? 0x80 : 0) |
                (button1Pressed ? 0x40 : 0) |
                (button2Pressed ? 0x20 : 0)
            );
            paramBuffer[3] = (byte) (
                (overflow ? 0x80 : 0) |
                (isConnected ? 0x40 : 0) // Connection bit
            );
        }
        statusReg = MFERR_NONE;
    }

    private int clampDelta(int delta) {
        if (delta > 127) return 127;
        if (delta < -127) return -127;
        return delta;
    }

    /**
     * CMD_MOUSE_STATUS (0x62): Query connection and device status
     * Returns: connectionState, signalStrength, battery, deviceType, etc.
     */
    private void cmdMouseStatus() {
        // Connection state
        if (!initialized) {
            paramBuffer[0] = 0x00; // Not initialized
        } else if (isPairing) {
            paramBuffer[0] = 0x04; // Pairing mode active
        } else if (isConnected) {
            paramBuffer[0] = 0x03; // Connected
        } else if (isPaired) {
            paramBuffer[0] = 0x02; // Paired, not connected
        } else {
            paramBuffer[0] = 0x01; // Initialized, not paired
        }

        // Signal strength (0-100 or 0xFF if unavailable)
        paramBuffer[1] = (byte) (isConnected ? 85 : 0xFF);

        // Battery level (0-100 or 0xFF if unavailable)
        paramBuffer[2] = (byte) (isConnected ? 75 : 0xFF);

        // Device type (0x01 = standard mouse)
        paramBuffer[3] = (byte) (isConnected ? 0x01 : 0x00);

        // Poll rate detected (0x01 = 125Hz)
        paramBuffer[4] = (byte) (isConnected ? 0x01 : 0x00);

        // Report count (16-bit little-endian)
        paramBuffer[5] = 0x00;
        paramBuffer[6] = 0x00;

        // Reserved
        paramBuffer[7] = 0x00;

        // Device name in DATAREG
        if (isConnected) {
            byte[] nameBytes = deviceName.getBytes();
            System.arraycopy(nameBytes, 0, dataBuffer, 0, Math.min(nameBytes.length, 32));
            // MAC address
            System.arraycopy(deviceMAC, 0, dataBuffer, 32, 6);
        }

        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_CONFIG (0x63): Configure sensitivity and options
     * Input: sensitivity, accelMode, buttonMapping, writeEnableKey
     */
    private void cmdMouseConfig() {
        int sens = paramBuffer[0] & 0xFF;
        int accel = paramBuffer[1] & 0xFF;
        int mapping = paramBuffer[2] & 0xFF;
        int key = paramBuffer[3] & 0xFF;

        // Validate write enable key
        if (key != WRITEENABLEKEY) {
            statusReg = MFERR_INVALIDWEKEY;
            return;
        }

        // Validate sensitivity (must be 64-255)
        if (sens < 64) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        // Validate acceleration mode (0-3)
        if (accel > 3) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        // Apply configuration
        sensitivity = sens;
        accelerationMode = accel;
        buttonMapping = mapping;

        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_PAIR_START (0x64): Enter pairing mode
     * Input: timeout, options, writeEnableKey
     * Returns: result code
     *
     * Simulates pairing with alternating success/failure on each attempt
     */
    private void cmdMousePairStart() {
        int timeout = paramBuffer[0] & 0xFF;
        int options = paramBuffer[1] & 0xFF;
        int key = paramBuffer[2] & 0xFF;

        // Validate write enable key
        if (key != WRITEENABLEKEY) {
            statusReg = MFERR_INVALIDWEKEY;
            return;
        }

        // Validate timeout (must be >= 10)
        if (timeout < 10) {
            paramBuffer[0] = 0x04; // Invalid timeout
            statusReg = MFERR_NONE;
            return;
        }

        // Check if already pairing
        if (isPairing) {
            paramBuffer[0] = 0x01; // Already in pairing mode
            statusReg = MFERR_NONE;
            return;
        }

        // Start simulated pairing with 100ms delay
        isPairing = true;
        pairingStartTime = System.currentTimeMillis();
        paramBuffer[0] = 0x00; // Success (pairing mode entered)

        // Simulate pairing result after delay (alternating success/failure)
        new Thread(() -> {
            try {
                Thread.sleep(100);
                isPairing = false;

                // Alternate between success and failure based on attempt count
                if ((pairingAttemptCount % 2) == 0) {
                    // Success - connect the mouse
                    isPaired = true;
                    isConnected = true;
                    EmulatorUILogic.addMouseListener(mouseHandler);
                } else {
                    // Failure - set error status
                    isPaired = false;
                    isConnected = false;
                    statusReg = MFERR_BTPAIRFAILED;
                }
                pairingAttemptCount++;
            } catch (InterruptedException e) {
                isPairing = false;
            }
        }).start();

        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_PAIR_CANCEL (0x65): Cancel pairing mode
     * Input: writeEnableKey
     */
    private void cmdMousePairCancel() {
        int key = paramBuffer[0] & 0xFF;

        // Validate write enable key
        if (key != WRITEENABLEKEY) {
            statusReg = MFERR_INVALIDWEKEY;
            return;
        }

        isPairing = false;
        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_DISCONNECT (0x66): Disconnect from current mouse
     * Input: forgetFlag, writeEnableKey
     */
    private void cmdMouseDisconnect() {
        int forgetFlag = paramBuffer[0] & 0xFF;
        int key = paramBuffer[1] & 0xFF;

        // Validate write enable key
        if (key != WRITEENABLEKEY) {
            statusReg = MFERR_INVALIDWEKEY;
            return;
        }

        // Validate forget flag
        if (forgetFlag > 1) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        isConnected = false;
        if (forgetFlag == 1) {
            isPaired = false;
        }

        EmulatorUILogic.removeMouseListener(mouseHandler);
        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_CLAMP (0x67): Set mouse movement bounds
     * Input: axis, minLow, minHigh, maxLow, maxHigh
     */
    private void cmdMouseClamp() {
        int axis = paramBuffer[0] & 0xFF;
        int minLow = paramBuffer[1] & 0xFF;
        int minHigh = paramBuffer[2] & 0xFF;
        int maxLow = paramBuffer[3] & 0xFF;
        int maxHigh = paramBuffer[4] & 0xFF;

        // Validate axis (0 = X, 1 = Y)
        if (axis > 1) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        int min = minLow | (minHigh << 8);
        int max = maxLow | (maxHigh << 8);

        // Validate min <= max
        if (min > max) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        if (axis == 0) {
            clampMinX = min;
            clampMaxX = max;
        } else {
            clampMinY = min;
            clampMaxY = max;
        }

        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_LIST_DEVICES (0x68): List paired devices
     * Returns: device count and device info in DATAREG
     */
    private void cmdMouseListDevices() {
        // Return count of 1 if paired, 0 otherwise
        paramBuffer[0] = (byte) (isPaired || isConnected ? 1 : 0);
        paramBuffer[1] = (byte) (isConnected ? 0 : 0xFF); // Active device index

        if (isPaired || isConnected) {
            // Device 0 entry at bytes 0-63 in DATAREG
            byte[] nameBytes = deviceName.getBytes();
            System.arraycopy(nameBytes, 0, dataBuffer, 0, Math.min(nameBytes.length, 32));
            System.arraycopy(deviceMAC, 0, dataBuffer, 32, 6);
            dataBuffer[38] = 0x01; // Device type: standard mouse
            dataBuffer[39] = (byte) (isConnected ? 0xFF : 0); // 0xFF = currently connected
        }

        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_SCAN_START (0x69): Start device scan
     * Input: scan duration
     */
    private void cmdMouseScanStart() {
        int duration = paramBuffer[0] & 0xFF;

        if (duration < 5 || duration > 60) {
            paramBuffer[0] = 0x02; // Invalid duration
            statusReg = MFERR_NONE;
            return;
        }

        paramBuffer[0] = 0x00; // Scan started
        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_SCAN_STOP (0x6A): Stop device scan
     */
    private void cmdMouseScanStop() {
        statusReg = MFERR_NONE;
    }

    /**
     * CMD_MOUSE_REMOVE_DEVICE (0x6B): Remove paired device
     * Input: deviceIndex, writeEnableKey
     */
    private void cmdMouseRemoveDevice() {
        int deviceIndex = paramBuffer[0] & 0xFF;
        int key = paramBuffer[1] & 0xFF;

        // Validate write enable key
        if (key != WRITEENABLEKEY) {
            statusReg = MFERR_INVALIDWEKEY;
            return;
        }

        // Validate device index (0-3)
        if (deviceIndex > 3) {
            statusReg = MFERR_INVALIDARG;
            return;
        }

        // Simulate removal
        if (deviceIndex == 0) {
            isPaired = false;
            isConnected = false;
            EmulatorUILogic.removeMouseListener(mouseHandler);
        }

        statusReg = MFERR_NONE;
    }

    // ==================== Mouse Event Handling ====================

    private void processMouseEvent(MouseEvent event) {
        if (event.getEventType() == MouseEvent.MOUSE_MOVED ||
            event.getEventType() == MouseEvent.MOUSE_DRAGGED) {

            double x = event.getSceneX();
            double y = event.getSceneY();

            // Calculate delta from last position
            double deltaX = x - lastMouseLocation.getX();
            double deltaY = y - lastMouseLocation.getY();

            // Accumulate deltas (this simulates what the Pico would do with HID reports)
            accumulatedDeltaX += (int) deltaX;
            accumulatedDeltaY += (int) deltaY;

            // Update last location
            lastMouseLocation = new Point2D(x, y);

            event.consume();
        }

        if (event.getEventType() == MouseEvent.MOUSE_PRESSED ||
            event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
            mousePressed(event);
            event.consume();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
            mouseReleased(event);
            event.consume();
        }
    }

    private void mousePressed(MouseEvent event) {
        MouseButton button = event.getButton();
        if (button == MouseButton.PRIMARY) {
            button0Pressed = true;
        } else if (button == MouseButton.SECONDARY) {
            button1Pressed = true;
        } else if (button == MouseButton.MIDDLE) {
            button2Pressed = true;
        }
    }

    private void mouseReleased(MouseEvent event) {
        MouseButton button = event.getButton();
        if (button == MouseButton.PRIMARY) {
            button0Pressed = false;
        } else if (button == MouseButton.SECONDARY) {
            button1Pressed = false;
        } else if (button == MouseButton.MIDDLE) {
            button2Pressed = false;
        }
    }

    // ==================== Firmware/C8 ROM (Not Used) ====================

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // No firmware ROM for this card
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // No C8 ROM for this card
    }

    @Override
    public void tick() {
        // No periodic updates needed
    }
}

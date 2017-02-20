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
package jace.hardware;

import jace.config.ConfigurableField;
import jace.config.DynamicSelection;
import jace.config.Name;
import jace.core.Card;
import jace.core.Computer;
import jace.core.RAMEvent;
import jace.core.RAMEvent.TYPE;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;

/**
 * Partial implementation of Passport midi card, supporting midi output routed
 * to the java midi synth for playback. Compatible with Ultima V. Card
 * operational notes taken from the Passport MIDI interface manual
 * ftp://ftp.apple.asimov.net/pub/apple_II/documentation/hardware/misc/passport_midi.pdf
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com 
 */
@Name(value = "Passport Midi Interface", description = "MIDI sound card")
public class PassportMidiInterface extends Card {

    private Receiver midiOut;

    public PassportMidiInterface(Computer computer) {
        super(computer);
    }

    @Override
    protected void handleC8FirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // There is no rom on this card, so nothing to do here
    }

    // MIDI timing: 31250 BPS, 8-N-1  (roughly 3472k per second)
    public static enum TIMER_MODE {

        CONTINUOUS, SINGLE_SHOT, FREQ_COMPARISON, PULSE_COMPARISON
    };
    
    @ConfigurableField(name = "Midi Output Device", description = "Midi output device")
    public static DynamicSelection<String> preferredMidiDevice = new DynamicSelection<String>(null) {
        @Override
        public boolean allowNull() {
            return false;
        }

        @Override
        public LinkedHashMap<? extends String, String> getSelections() {
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info dev : devices) {
                try {
                    MidiDevice device = MidiSystem.getMidiDevice(dev);
                    if (device.getMaxReceivers() > 0 || dev instanceof Synthesizer)
                        System.out.println("MIDI Device found: " + dev);
                    out.put(dev.getName(), dev.getName());
                } catch (MidiUnavailableException ex) {
                    Logger.getLogger(PassportMidiInterface.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return out;
        }
    };

    public static class PTMTimer {
        // Configuration values

        public boolean prescaledTimer = false; // Only available on Timer 3
        public boolean enableClock = false; // False == use CX clock input
        public boolean dual8BitMode = false;
        public TIMER_MODE mode = TIMER_MODE.CONTINUOUS;
        public boolean irqEnabled = false;
        public boolean counterOutputEnable = false;
        // Set by data latches
        public Long duration = 0L;
        // Run values
        public boolean irqRequested = true;
        public Long value = 0L;
    }
    // I/O registers
    // --- 6840 PTM
    public static final int TIMER_CONTROL_1 = 0;
    public static final int TIMER_CONTROL_2 = 1;
    public static final int TIMER1_MSB = 2;
    public static final int TIMER1_LSB = 3;
    public static final int TIMER2_MSB = 4;
    public static final int TIMER2_LSB = 5;
    //     (Most likely not used)
    public static final int TIMER3_MSB = 6;
    public static final int TIMER3_LSB = 7;
    // --- 6850 ACIA registers (write)
    public static final int ACIA_CONTROL = 8;
    public static final int ACIA_SEND = 9;
    // --- 6850 ACIA registers (read)
    public static final int ACIA_STATUS = 8;
    public static final int ACIA_RECV = 9;
    // --- Drums
    public static final int DRUM_SYNC_SET = 0x0e;
    public static final int DRUM_SYNC_CLEAR = 0x0f;
    //---------------------------------------------------------
    // PTM control values (register 1,2 and 3)
    public static final int PTM_START_TIMERS = 0;
    public static final int PTM_STOP_TIMERS = 1;
    public static final int PTM_RESET = 67;
    // PTM select values (register 2 only) -- modifies what Reg 1 points to
    public static final int PTM_SELECT_REG_1 = 1;
    public static final int PTM_SELECT_REG_3 = 0;
    // PTM select values (register 3 only)
    public static final int TIMER3_PRESCALED = 1;
    public static final int TIMER3_NOT_PRESCALED = 0;
    // PTM bit values
    public static final int PTM_CLOCK_SOURCE = 2;       // Bit 1
    // 0 = external, 2 = internal clock
    public static final int PTM_LATCH_IS_16BIT = 4;    // Bit 2
    // 0 = 16-bit, 4 = dual 8-bit
    // Bits 3-5
    // 5 4 3
    public static final int PTM_CONTINUOUS = 0;         // 0 x 0
    public static final int PTM_SINGLE_SHOT = 32;       // 1 x 0
    public static final int PTM_FREQ_COMP = 8;          // x 0 1
    public static final int PTM_PULSE_COMP = 24;        // x 1 1
    public static final int PTM_IRQ_ENABLED = 64;       // Bit 6
    // 64 = IRQ Enabled, 0 = IRQ Masked
    public static final int PTM_OUTPUT_ENABLED = 128;    // Bit 7
    // 128 = Timer output enabled, 0 = disabled
    // ACIA control values
    // Reset == Master reset + even parity + 2 stop bits + 8 bit + No interrupts (??)
    public static final int ACIA_RESET = 19;
    public static final int ACIA_MASK_INTERRUPTS = 17;
    public static final int ACIA_OFF = 21;
    // Counter * 1 + RTS = low, transmit interrupt enabled
    public static final int ACIA_INT_ON_SEND = 49;
    // Counter * 1 + RTS = high, transmit interrupt disabled + Interrupt on receive
    public static final int ACIA_INT_ON_RECV = 145;
    // Counter * 1 + RTS = low, transmit interrupt enabled + Interrupt on receive
    public static final int ACIA_INT_ON_SEND_AND_RECV = 177;
    // ACIA control register values
    // --- Bits 1 and 0 control counter divide select
    public static final int ACIA_COUNTER_1 = 0;
    public static final int ACIA_COUNTER_16 = 1;
    public static final int ACIA_COUNTER_64 = 2;
    public static final int ACIA_MASTER_RESET = 3;
    // Midi is always transmitted 8-N-1
    public static final int ACIA_ODD_PARITY = 4;    // 4 = odd, 0 = even
    public static final int ACIA_STOP_BITS_1 = 8;    // 8 = 1 stop bit, 0 = 2 stop bits
    public static final int ACIA_WORD_LENGTH_8 = 16;    // 16 = 8-bit, 0 = 7-bit
    // --- Bits 5 and 6 control interrupts
    // 6 5
    // 0 0  RTS = low, transmit interrupt disabled
    // 0 1  RTS = low, transmit interrupt enabled
    // 1 0  RTS = high, transmit interrupt disabled
    // 1 1  RTS = low, Transmit break, trasmit interrupt disabled
    public static final int ACIA_RECV_INTERRUPT = 128;  // 128 = interrupt on receive, 0 = no interrupt
    // PTM configuration
    private boolean ptmTimer3Selected = false; // When true, reg 1 points at timer 3
    private boolean ptmTimersActive = false; // When true, timers run constantly
    private final PTMTimer[] ptmTimer = {
        new PTMTimer(),
        new PTMTimer(),
        new PTMTimer()
    };
    private boolean ptmStatusReadSinceIRQ = false;
    // ---------------------- ACIA CONFIGURATION
    private final boolean aciaInterruptOnSend = false;
    private final boolean aciaInterruptOnReceive = false;
    // ---------------------- ACIA STATUS BITS
    // True when MIDI IN receives a byte
    private final boolean receivedACIAByte = false;
    // True when data is not transmitting (always true because we aren't really doing wire transmission);
    private final boolean transmitACIAEmpty = true;
    // True if another byte is received before the previous byte was processed
    private final boolean receiverACIAOverrun = false;
    // True if ACIA generated interrupt request
    private final boolean irqRequestedACIA = false;

    @Override
    public void reset() {
        // TODO: Deactivate card
        suspend();
    }

    @Override
    public boolean suspend() {
        // TODO: Deactivate card
        suspendACIA();
        return super.suspend();
    }

    @Override
    protected void handleFirmwareAccess(int register, TYPE type, int value, RAMEvent e) {
        // No firmware, so do nothing
    }

    @Override
    protected void handleIOAccess(int register, TYPE type, int value, RAMEvent e) {
        switch (type) {
            case READ_DATA:
                int returnValue = 0;
                switch (register) {
                    case ACIA_STATUS:
                        returnValue = getACIAStatus();
                        break;
                    case ACIA_RECV:
                        returnValue = getACIARecieve();
                        break;
                    //TODO: Implement PTM registers
                    case TIMER_CONTROL_1:
                        // Technically it's not supposed to return anything...
                        returnValue = getPTMStatus();
                        break;
                    case TIMER_CONTROL_2:
                        returnValue = getPTMStatus();
                        break;
                    case TIMER1_LSB:
                        returnValue = (int) (ptmTimer[0].value & 0x0ff);
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[0].irqRequested = false;
                        }
                        break;
                    case TIMER1_MSB:
                        returnValue = (int) (ptmTimer[0].value >> 8) & 0x0ff;
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[0].irqRequested = false;
                        }
                        break;
                    case TIMER2_LSB:
                        returnValue = (int) (ptmTimer[1].value & 0x0ff);
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[1].irqRequested = false;
                        }
                        break;
                    case TIMER2_MSB:
                        returnValue = (int) (ptmTimer[1].value >> 8) & 0x0ff;
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[1].irqRequested = false;
                        }
                        break;
                    case TIMER3_LSB:
                        returnValue = (int) (ptmTimer[2].value & 0x0ff);
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[2].irqRequested = false;
                        }
                        break;
                    case TIMER3_MSB:
                        returnValue = (int) (ptmTimer[2].value >> 8) & 0x0ff;
                        if (ptmStatusReadSinceIRQ) {
                            ptmTimer[2].irqRequested = false;
                        }
                        break;
                    default:
                        System.out.println("Passport midi read unrecognized, port " + register);
                }

                e.setNewValue(returnValue);
//                System.out.println("Passport I/O read register " + register + " == " + returnValue);
                break;
            case WRITE:
                int v = e.getNewValue() & 0x0ff;
//                System.out.println("Passport I/O write register " + register + " == " + v);
                switch (register) {
                    case ACIA_CONTROL:
                        processACIAControl(v);
                        break;
                    case ACIA_SEND:
                        processACIASend(v);
                        break;
                    case TIMER_CONTROL_1:
                        if (ptmTimer3Selected) {
//                            System.out.println("Configuring timer 3");
                            ptmTimer[2].prescaledTimer = ((v & TIMER3_PRESCALED) != 0);
                            processPTMConfiguration(ptmTimer[2], v);
                        } else {
//                            System.out.println("Configuring timer 1");
                            if ((v & PTM_STOP_TIMERS) == 0) {
                                startPTM();
                            } else {
                                stopPTM();
                            }
                            processPTMConfiguration(ptmTimer[0], v);
                        }
                        break;
                    case TIMER_CONTROL_2:
//                        System.out.println("Configuring timer 2");
                        ptmTimer3Selected = ((v & PTM_SELECT_REG_1) == 0);
                        processPTMConfiguration(ptmTimer[1], v);
                        break;
                    case TIMER1_LSB:
                        ptmTimer[0].duration = (ptmTimer[0].duration & 0x0ff00) | v;
                        break;
                    case TIMER1_MSB:
                        ptmTimer[0].duration = (ptmTimer[0].duration & 0x0ff) | (v << 8);
                        break;
                    case TIMER2_LSB:
                        ptmTimer[1].duration = (ptmTimer[1].duration & 0x0ff00) | v;
                        break;
                    case TIMER2_MSB:
                        ptmTimer[1].duration = (ptmTimer[1].duration & 0x0ff) | (v << 8);
                        break;
                    case TIMER3_LSB:
                        ptmTimer[2].duration = (ptmTimer[2].duration & 0x0ff00) | v;
                        break;
                    case TIMER3_MSB:
                        ptmTimer[2].duration = (ptmTimer[2].duration & 0x0ff00) | (v << 8);
                        break;
                    default:
                        System.out.println("Passport midi write unrecognized, port " + register);
                }

                break;
        }
    }

    @Override
    public void tick() {
        if (ptmTimersActive) {
            for (PTMTimer t : ptmTimer) {
//                if (t.duration == 0) {
//                    continue;
//                }
                t.value--;
                if (t.value < 0) {
                    // TODO: interrupt dual 8-bit mode, whatver that is!
                    if (t.irqEnabled) {
//                        System.out.println("Timer generating interrupt!");
                        t.irqRequested = true;
                        computer.getCpu().generateInterrupt();
                        ptmStatusReadSinceIRQ = false;
                    }
                    if (t.mode == TIMER_MODE.CONTINUOUS || t.mode == TIMER_MODE.FREQ_COMPARISON) {
                        t.value = t.duration;
                    }
                }
            }
        }
    }

    @Override
    public String getDeviceName() {
        return "Passport MIDI Controller";
    }

    //------------------------------------------------------ PTM
    private void processPTMConfiguration(PTMTimer timer, int val) {
        timer.enableClock = (val & PTM_CLOCK_SOURCE) != 0;
        timer.dual8BitMode = (val & PTM_LATCH_IS_16BIT) != 0;
        switch (val & 56) {
            // Evaluate bits 3, 4 and 5 to determine mode
            case PTM_CONTINUOUS:
                timer.mode = TIMER_MODE.CONTINUOUS;
                break;
            case PTM_PULSE_COMP:
                timer.mode = TIMER_MODE.PULSE_COMPARISON;
                break;
            case PTM_FREQ_COMP:
                timer.mode = TIMER_MODE.FREQ_COMPARISON;
                break;
            case PTM_SINGLE_SHOT:
                timer.mode = TIMER_MODE.SINGLE_SHOT;
                break;
            default:
                timer.mode = TIMER_MODE.CONTINUOUS;
                break;
        }
        timer.irqEnabled = (val & PTM_IRQ_ENABLED) != 0;
        timer.counterOutputEnable = (val & PTM_OUTPUT_ENABLED) != 0;
    }

    private void stopPTM() {
//        System.out.println("Passport timers halted");
        ptmTimersActive = false;
    }

    private void startPTM() {
//        System.out.println("Passport timers started");
        ptmTimersActive = true;
        ptmTimer[0].irqRequested = false;
        ptmTimer[1].irqRequested = false;
        ptmTimer[2].irqRequested = false;
        ptmTimer[0].value = ptmTimer[0].duration;
        ptmTimer[1].value = ptmTimer[1].duration;
        ptmTimer[2].value = ptmTimer[2].duration;

    }

    // Bits 0, 1 and 2 == IRQ requested from timer 1, 2 or 3
    // Bit 7 = Any IRQ
    private int getPTMStatus() {
        int status = 0;
        for (int i = 0; i < 3; i++) {
            PTMTimer t = ptmTimer[i];
            if (t.irqRequested && t.irqEnabled) {
                ptmStatusReadSinceIRQ = true;
                status |= (1 << i);
                status |= 128;
            }
        }
        return status;
    }
    //------------------------------------------------------ ACIA
    /*
     ACIA status register
     Bit 0 = Receive data register full
     Bit 1 = Transmit data register empty
     Bits 2 and 3 pertain to modem (DCD and CTS, so ignore)
     Bit 4 = Framing error
     Bit 5 = Receiver overrun
     Bit 6 = Partity error (not used by MIDI)
     Bit 7 = Interrupt request
     */

    private int getACIAStatus() {
        int status = 0;
        if (receivedACIAByte) {
            status |= 1;
        }
        if (transmitACIAEmpty) {
            status |= 2;
        }
        if (receiverACIAOverrun) {
            status |= 32;
        }
        if (irqRequestedACIA) {
            status |= 128;
        }
        return status;
    }

    // TODO: Implement MIDI IN... some day
    private int getACIARecieve() {
        return 0;
    }

    private void processACIAControl(int value) {
        if ((value & 0x03) == ACIA_MASTER_RESET) {
            resume();
        }
    }
    ShortMessage currentMessage;
    int currentMessageStatus;
    int currentMessageData1;
    int currentMessageData2;
    int messageSize = 255;
    int currentMessageReceived = 0;

    private void processACIASend(int value) {
        if (!isRunning()) {
//            System.err.println("ACIA not active!");
            return;
        } else {
//            System.out.println("ACIA send "+value);
        }
        // First off try to finish off previous command already in play
        boolean sendMessage = false;
        if (currentMessage != null) {
            if ((value & 0x080) > 0) {
                // Any command byte received means we finished receiving another command
                // and valid or not, process it as-is
                if (currentMessage != null) {
                    sendMessage = true;
                }
                // If there is no current message, then we'll pick this up afterwards...
            } else {
                // If we receive a data byte ( < 128 ) then check if we have the right size
                // if so, then the command was completely received, and it's time to send it.
                currentMessageReceived++;
                if (currentMessageReceived >= messageSize) {
                    sendMessage = true;
                }
                if (currentMessageReceived == 1) {
                    currentMessageData1 = value;
                } else {
                    // Possibly redundant, but there's no reason a message should be longer than this...
                    currentMessageData2 = value;
                    sendMessage = true;
                }
            }
        }

        // If we have a command to send, then do it
        if (sendMessage == true) {
            if (midiOut != null) {
                // Send message
                try {
//                    System.out.println("Sending MIDI message "+currentMessageStatus+","+currentMessageData1+","+currentMessageData2);
                    currentMessage.setMessage(currentMessageStatus, currentMessageData1, currentMessageData2);
                    midiOut.send(currentMessage, -1L);
                } catch (InvalidMidiDataException ex) {
                    Logger.getLogger(PassportMidiInterface.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            currentMessage = null;
        }

        // Do we have a new command byte?
        if ((value & 0x080) > 0) {
            // Start a new message
            currentMessage = new ShortMessage();
            currentMessageStatus = value;
            currentMessageData1 = 0;
            currentMessageData2 = 0;
            try {
                currentMessage.setMessage(currentMessageStatus, 0, 0);
                messageSize = currentMessage.getLength();
            } catch (InvalidMidiDataException ex) {
                messageSize = 0;
            }
            currentMessageReceived = 0;
        }

    }

    @Override
    public void resume() {
        if (isRunning() && midiOut != null) {
            return;
        }
        try {
            MidiDevice selectedDevice = MidiSystem.getSynthesizer();
            MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
            if (devices.length == 0) {
                System.out.println("No MIDI devices found");
            } else {
                for (MidiDevice.Info dev : devices) {
                    System.out.println("MIDI Device found: " + dev);
                    if ((preferredMidiDevice.getValue() == null && dev.getName().contains("Java Sound") && dev instanceof Synthesizer) ||
                            preferredMidiDevice.getValue().equalsIgnoreCase(dev.getName())
                        ) {
                        selectedDevice = MidiSystem.getMidiDevice(dev);
                        break;
                    }
                }
            }
            if (selectedDevice != null) {
                System.out.println("Selected MIDI device: " + selectedDevice.getDeviceInfo().getName());
                selectedDevice.open();
                midiOut = selectedDevice.getReceiver();
                super.resume();
            }
        } catch (MidiUnavailableException ex) {
            System.out.println("Could not open MIDI synthesizer");
            Logger.getLogger(PassportMidiInterface.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void suspendACIA() {
        // TODO: Stop ACIA thread...
        if (midiOut != null) {
            currentMessage = new ShortMessage();
            // Send a note-off on every channel
            for (int channel = 0; channel < 16; channel++) {
                try {
                    // All Notes Off
                    currentMessage.setMessage(0x0B0 | channel, 123, 0);
                    midiOut.send(currentMessage, 0);                
                    // All Oscillators Off
                    currentMessage.setMessage(0x0B0 | channel, 120, 0);
                    midiOut.send(currentMessage, 0);                
                } catch (InvalidMidiDataException ex) {
                    Logger.getLogger(PassportMidiInterface.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            midiOut.close();
            midiOut = null;
        }
    }
}

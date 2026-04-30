package jace.ipc;

public final class IpcConstants {
    private IpcConstants() {}

    // Request types: Cyrene -> Jace
    public static final int C2K_OPEN_CONNECTION  = 1;
    public static final int C2K_CLOSE_CONNECTION = 2;
    public static final int C2K_GET_SNAPSHOT     = 3;
    public static final int C2K_GET_OPERATION    = 4;
    public static final int C2K_PAUSE            = 5;
    public static final int C2K_WRITE_DATA       = 6;

    // Response types: Jace -> Cyrene
    public static final int K2C_SEND_SNAPSHOT    = 6;
    public static final int K2C_SEND_OPERATION   = 7;
    public static final int K2C_CLOSE_CONNECTION = 9;

    // TCP frame
    public static final int FRAME_HEADER_SIZE    = 8;
    public static final int DEFAULT_PORT         = 57867;

    // Snapshot layout offsets
    public static final int SNAP_OFF_NEXT_OP     = 0;
    public static final int SNAP_OFF_STOP_COND   = 100;
    public static final int SNAP_OFF_BREAKPOINT  = 101;
    public static final int SNAP_OFF_DOC_REG     = 512;
    public static final int SNAP_OFF_BATT_RAM    = 768;
    public static final int SNAP_OFF_BANK00      = 1024;
    public static final int SNAP_OFF_BANKE0      = 1024 + 65536;
    public static final int SNAP_OFF_BANKE1      = 1024 + 131072;
    public static final int SNAP_OFF_ROM         = 1024 + 196608;
    public static final int SNAP_OFF_DOC_RAM     = 1024 + 327680;
    // Total: 1024 header + 5 x 65536 RAM/ROM blocks = 1,327,104 bytes
    public static final int SNAP_TOTAL_SIZE      = 1024 + 5 * 65536;

    // Operation data field offsets (within the 59-byte op block at SNAP_OFF_NEXT_OP)
    public static final int OP_OFF_A            = 0;
    public static final int OP_OFF_X            = 2;
    public static final int OP_OFF_Y            = 4;
    public static final int OP_OFF_PC           = 6;
    public static final int OP_OFF_PBR          = 8;
    public static final int OP_OFF_DBR          = 9;
    public static final int OP_OFF_STACK        = 10;
    public static final int OP_OFF_DIRECT_PAGE  = 12;
    public static final int OP_OFF_STATUS       = 14;
    public static final int OP_OFF_OPCODE       = 16;
    public static final int OP_OFF_OPERAND1     = 17;
    public static final int OP_OFF_OPERAND2     = 18;
    public static final int OP_OFF_OPERAND3     = 19;
    public static final int OP_OFF_DATA_SIZE    = 20;
    public static final int OP_OFF_READ_ADDR    = 21;
    public static final int OP_OFF_WRITE_ADDR   = 24;
    public static final int OP_OFF_JUMP_ADDR    = 27;
    public static final int OP_OFF_READ_VAL     = 30;
    public static final int OP_OFF_WRITE_VAL    = 32;
    public static final int OP_OFF_FLAGS        = 34;
    public static final int OP_OFF_CALL_NUM     = 35;
    public static final int OP_OFF_ROM_VERSION  = 37;
    public static final int OP_OFF_RAM_BANKS    = 38;
    public static final int OP_OFF_VERTCNT      = 39;
    public static final int OP_OFF_HORIZCNT     = 40;
    public static final int OP_OFF_SHADOW       = 41;
    public static final int OP_OFF_STATEREG     = 42;
    public static final int OP_OFF_GOID         = 43;
    public static final int OP_OFF_GCC          = 51;
    public static final int OP_BLOCK_SIZE       = 59;

    // Stop conditions
    public static final byte STOP_NONE          = 0;
    public static final byte STOP_BREAKPOINT    = 10;

    // Apple IIe discriminators
    public static final byte ROM_VERSION_APPLE_IIE = 0x00;
    public static final byte RAM_BANKS_APPLE_IIE   = 2;

    // DOC silence byte (offset 2 within DOC register block)
    public static final byte DOC_SILENCE_BYTE   = (byte) 0x80;

    public static final int OPERATION_FLUSH_THRESHOLD = 20000;
}

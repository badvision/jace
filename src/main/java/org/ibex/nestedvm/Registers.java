// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm;

interface Registers {
    // Register Names
    public final static int ZERO = 0; // Immutable, hardwired to 0
    public final static int AT = 1;  // Reserved for assembler
    public final static int K0 = 26; // Reserved for kernel 
    public final static int K1 = 27; // Reserved for kernel 
    public final static int GP = 28; // Global pointer (the middle of .sdata/.sbss)
    public final static int SP = 29; // Stack pointer
    public final static int FP = 30; // Frame Pointer
    public final static int RA = 31; // Return Address
    
    // Return values (caller saved)
    public final static int V0 = 2;
    public final static int V1 = 3;
    // Argument Registers (caller saved)
    public final static int A0 = 4; 
    public final static int A1 = 5;
    public final static int A2 = 6;
    public final static int A3 = 7;
    // Temporaries (caller saved)
    public final static int T0 = 8;
    public final static int T1 = 9;
    public final static int T2 = 10;
    public final static int T3 = 11;
    public final static int T4 = 12;
    public final static int T5 = 13;
    public final static int T6 = 14;
    public final static int T7 = 15;
    public final static int T8 = 24;
    public final static int T9 = 25;
    // Saved (callee saved)
    public final static int S0 = 16;
    public final static int S1 = 17;
    public final static int S2 = 18;
    public final static int S3 = 19;
    public final static int S4 = 20;
    public final static int S5 = 21;
    public final static int S6 = 22;
    public final static int S7 = 23;
}

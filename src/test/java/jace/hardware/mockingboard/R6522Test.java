package jace.hardware.mockingboard;

import org.junit.Test;

public class R6522Test {
    R6522 r6522 = new R6522() {
        @Override
        public String getShortName() {
            return "name";
        }

        @Override
        public void sendOutputA(int value) {
            // No-op
        }

        @Override
        public void sendOutputB(int value) {
            // No-op
        }

        @Override
        public int receiveOutputA() {
            return -1;
        }

        @Override
        public int receiveOutputB() {
            return -1;
        }
    };

    @Test
    public void testWriteRegs() {
        for (R6522.Register reg : R6522.Register.values()) {
            r6522.writeRegister(reg.val, 0);
        }
    }

    @Test
    public void testReadRegs() {
        for (R6522.Register reg : R6522.Register.values()) {
            r6522.readRegister(reg.val);
        }
    }
}

    


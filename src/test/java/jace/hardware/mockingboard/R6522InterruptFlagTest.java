package jace.hardware.mockingboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import jace.core.Utility;

/**
 * Verifies the 6522's interrupt-flag / interrupt-enable split.
 *
 * <h3>The rule</h3>
 * On a real 6522, IER does <em>not</em> gate the flag in IFR. The flag is set by
 * the interrupt condition itself; IER decides only whether the IRQ <em>pin</em>
 * is pulled low. MAME's {@code 6522via.cpp} splits it exactly this way:
 * <pre>
 *   TIMER_CALLBACK_MEMBER(via6522_device::t1_tick) {
 *       ...
 *       set_int(INT_T1);          // unconditional -- no IER test
 *   }
 *
 *   void via6522_device::set_int(int data) {
 *       if (!(m_ifr &amp; data)) { m_ifr |= data; output_irq(); }
 *   }
 *
 *   void via6522_device::output_irq() {
 *       if (m_ier &amp; m_ifr &amp; 0x7f) { ... m_ifr |= INT_ANY; assert IRQ ... }
 *       else                       { ... m_ifr &amp;= ~INT_ANY; clear IRQ ... }
 *   }
 * </pre>
 * So IER appears in exactly one place: deciding the IRQ line and the IFR bit-7
 * summary. It never guards {@code m_ifr |= INT_T1}.
 *
 * <h3>Why it matters here</h3>
 * Polling IFR with interrupts left disabled is a standard idiom, and
 * {@code CardMockingboard.handleFirmwareAccess} documents software that relies
 * on it ("Games such as Skyfox use the timer to detect if the card is
 * present"). If the flag never appears unless IRQs are enabled, such a poll
 * spins forever. This is invisible to a register-frame comparison against a
 * player that does use IRQs.
 */
public class R6522InterruptFlagTest {

    private R6522 via;

    private static R6522 newVia() {
        return new R6522() {
            @Override public String getShortName() { return "test-via"; }
            @Override public void sendOutputA(int value) { /* not under test */ }
            @Override public void sendOutputB(int value) { /* not under test */ }
            @Override public int receiveOutputA() { return 0; }
            @Override public int receiveOutputB() { return 0; }
        };
    }

    private void write(R6522.Register r, int value) {
        via.writeRegister(r.val, value);
    }

    private int read(R6522.Register r) {
        return via.readRegister(r.val);
    }

    /** Runs the VIA until T1's flag appears, up to a bounded number of ticks. */
    private int ticksUntilTimer1Flag(int limit) {
        for (int i = 1; i <= limit; i++) {
            via.tick();
            if ((read(R6522.Register.IFR) & 0x40) != 0) {
                return i;
            }
        }
        return -1;
    }

    @Before
    public void setUp() {
        // R6522.tick() reaches Emulator.withComputer to raise the CPU interrupt.
        Utility.setHeadlessMode(true);
        via = newVia();
    }

    @Test
    public void timer1FlagIsSetEvenWhenTheTimer1InterruptIsDisabled() {
        write(R6522.Register.ACR, 0x40);   // T1 free-run
        write(R6522.Register.IER, 0x40);   // bit 7 clear => DISABLE T1 interrupt
        assertFalse("precondition: T1 interrupt must be disabled", via.timer1interruptEnabled);

        write(R6522.Register.T1CL, 10);
        write(R6522.Register.T1CH, 0);

        assertTrue("T1's IFR flag must appear on timer expiry even with the T1 "
                   + "interrupt disabled -- IER gates the IRQ line, not the flag",
                   ticksUntilTimer1Flag(40) > 0);
    }

    @Test
    public void timer2FlagIsSetEvenWhenTheTimer2InterruptIsDisabled() {
        write(R6522.Register.IER, 0x20);   // bit 7 clear => DISABLE T2 interrupt
        assertFalse("precondition: T2 interrupt must be disabled", via.timer2interruptEnabled);

        write(R6522.Register.T2CL, 10);
        write(R6522.Register.T2CH, 0);

        boolean flagged = false;
        for (int i = 0; i < 40 && !flagged; i++) {
            via.tick();
            flagged = (read(R6522.Register.IFR) & 0x20) != 0;
        }
        assertTrue("T2's IFR flag must appear on timer expiry even with the T2 "
                   + "interrupt disabled", flagged);
    }

    @Test
    public void ifrBitSevenSummarisesEnabledInterruptsOnly() {
        // MAME output_irq(): INT_ANY is set from (m_ier & m_ifr), not from m_ifr
        // alone. A flag that is set but not enabled must not claim the IRQ line.
        write(R6522.Register.ACR, 0x40);
        write(R6522.Register.IER, 0x40);   // disable T1
        write(R6522.Register.T1CL, 10);
        write(R6522.Register.T1CH, 0);
        assertTrue("precondition: the flag must be raised", ticksUntilTimer1Flag(40) > 0);

        int ifr = read(R6522.Register.IFR);
        assertEquals("T1's flag bit must be set", 0x40, ifr & 0x40);
        assertEquals("IFR bit 7 must stay clear while the interrupt is disabled",
                     0, ifr & 0x80);
    }

    @Test
    public void enablingAnAlreadyFlaggedInterruptRaisesIfrBitSeven() {
        write(R6522.Register.ACR, 0x40);
        write(R6522.Register.IER, 0x40);   // disable T1
        write(R6522.Register.T1CL, 10);
        write(R6522.Register.T1CH, 0);
        assertTrue("precondition: the flag must be raised", ticksUntilTimer1Flag(40) > 0);
        assertEquals("precondition: bit 7 clear while disabled", 0, read(R6522.Register.IFR) & 0x80);

        write(R6522.Register.IER, 0xC0);   // bit 7 set => ENABLE T1

        assertEquals("once the interrupt is enabled the pending flag must show in bit 7",
                     0x80, read(R6522.Register.IFR) & 0x80);
    }

    @Test
    public void writingIfrClearsTheFlagAndTheSummaryBit() {
        write(R6522.Register.ACR, 0x40);
        write(R6522.Register.IER, 0xC0);   // enable T1
        write(R6522.Register.T1CL, 10);
        write(R6522.Register.T1CH, 0);
        assertTrue("precondition: the flag must be raised", ticksUntilTimer1Flag(40) > 0);
        assertEquals("precondition: bit 7 set while enabled and flagged",
                     0x80, read(R6522.Register.IFR) & 0x80);

        write(R6522.Register.IFR, 0x40);   // MAME: clear_int(data & 0x7f)

        assertEquals("writing a 1 to a flag bit clears it", 0, read(R6522.Register.IFR));
    }

    @Test
    public void readingTimer1LowCounterClearsTheFlagWhenTheInterruptIsDisabled() {
        // MAME clears INT_T1 on a T1CL read unconditionally. That is the escape
        // hatch a polling loop uses, so it has to work in the disabled case too.
        write(R6522.Register.ACR, 0x40);
        write(R6522.Register.IER, 0x40);   // disable T1
        write(R6522.Register.T1CL, 10);
        write(R6522.Register.T1CH, 0);
        assertTrue("precondition: the flag must be raised", ticksUntilTimer1Flag(40) > 0);

        read(R6522.Register.T1CL);

        assertEquals("reading T1CL acknowledges the timer interrupt",
                     0, read(R6522.Register.IFR) & 0x40);
    }

    @Test
    public void ierReadbackReportsBitSevenSetAndTheEnableMask() {
        // MAME: case VIA_IER -> val = m_ier | 0x80.
        write(R6522.Register.IER, 0xC0);   // enable T1
        write(R6522.Register.IER, 0x20);   // disable T2
        int ier = read(R6522.Register.IER);
        assertEquals("IER reads back with bit 7 always set", 0x80, ier & 0x80);
        assertEquals("T1 enabled", 0x40, ier & 0x40);
        assertEquals("T2 disabled", 0, ier & 0x20);
    }
}

# RamAccessor for Apple //e Memory Bank Switching

## Overview

The RamAccessor utility provides explicit control over the complex memory bank switching in the Apple //e emulator. It allows you to:

1. Read from or write to specific memory banks regardless of current soft switch settings
2. Specify exactly which memory bank to access (main/aux, language card bank 1/2)
3. Receive RAM events that include detailed information about which memory bank was accessed

## Memory Switches

The Apple //e memory architecture is controlled via a set of soft switches that determine which memory bank is accessed. The `MemorySwitch` enum represents these switches:

- `AUX_ZP`: Use auxiliary zero page memory (0000-01FF)
- `AUX`: Use auxiliary memory for addresses outside the zero page
- `AUX_LC`: Use auxiliary language card instead of main language card
- `LC1`: Use language card bank 1
- `LC2`: Use language card bank 2 (overrides LC1 if both are specified)

## Basic Usage

```java
// Create an accessor for a RAM128k instance
RAM128k ram = /* get RAM128k instance */;
RamAccessor accessor = new RamAccessor(ram);

// Read from main memory
byte mainValue = accessor.bankRead(0x1000, false);

// Read from auxiliary memory
byte auxValue = accessor.bankRead(0x1000, false, MemorySwitch.AUX);

// Write to language card bank 2
accessor.bankWrite(0xD000, (byte) 0x42, true, MemorySwitch.LC2);

// Read from auxiliary language card bank 1
byte auxLcValue = accessor.bankRead(0xD000, false, MemorySwitch.AUX_LC, MemorySwitch.LC1);
```

## Enhanced RAM Events

The RamAccessor also supports an enhanced RAM event type called `RAMEventWithFlags` that includes information about which memory bank was accessed:

```java
// Create a RAM listener that's sensitive to aux memory
RAMListener auxListener = new RAMListener("Aux Listener", RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY) {
    @Override
    protected void doConfig() {
        setScopeStart(0x1000);
    }
    
    @Override
    protected void doEvent(RAMEvent e) {
        if (e instanceof RAMEventWithFlags) {
            RAMEventWithFlags fe = (RAMEventWithFlags) e;
            if (fe.hasFlag(MemorySwitch.AUX)) {
                // Handle aux memory read
            }
        }
    }
};
```

### MemoryFlagListener for Cleaner Event Handling

For even cleaner event handling with memory flag filtering, use the `MemoryFlagListener` class:

```java
// Create a listener that only processes events in auxiliary memory
MemoryFlagListener auxListener = new MemoryFlagListener("Aux Memory Listener", 
        RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
        MemorySwitch.AUX) {
    @Override
    protected void doConfig() {
        setScopeStart(0x1000);
    }
    
    @Override
    protected void doEvent(RAMEvent e) {
        // The event is already verified to be in auxiliary memory,
        // so we can focus on our core logic without flag checking
        // ...
    }
};

// Create a listener for complex memory configurations
MemoryFlagListener complexListener = new MemoryFlagListener("Complex Listener", 
        RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
        new MemorySwitch[] { MemorySwitch.AUX, MemorySwitch.LC2 },
        new MemorySwitch[] { MemorySwitch.AUX_ZP }) {
    // Handles only events in auxiliary memory, language card bank 2,
    // but not in zero page
    // ...
};

// Create a listener with a custom predicate for advanced filtering
MemoryFlagListener customListener = new MemoryFlagListener("Custom Listener", 
        RAMEvent.TYPE.READ, RAMEvent.SCOPE.ADDRESS, RAMEvent.VALUE.ANY, 
        e -> e.isAuxMemory() && e.getLcBank() > 1) {
    // Custom logic for complex conditions
    // ...
};
```

## Integration with Existing Code

The RamAccessor can be used alongside the existing memory access methods. It can translate between the current memory configuration and the explicit memory switches:

```java
// Get switches representing current memory configuration
MemorySwitch[] currentSwitches = accessor.getCurrentMemorySwitches();

// Read using current memory configuration
byte value = accessor.bankRead(0x1000, true, currentSwitches);
```

## Testing

The RamAccessor is designed to be easily testable, with a constructor that takes a RAM128k instance that can be mocked for testing purposes. The test classes demonstrate:

1. Reading from and writing to different memory banks
2. Creating RAM events with specific memory flags
3. Implementing listeners that respond to specific memory configurations

## Additional Resources

For more information on the Apple //e memory architecture, see:
- [Apple IIe Technical Reference Manual](https://www.applelogic.org/files/AIIETECHREF2.pdf) 
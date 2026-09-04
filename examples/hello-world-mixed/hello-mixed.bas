\rem HELLO WORLD! -- Mixed BASIC + Assembly via DATA/POKE/CALL
\rem This program embeds a 30-byte 6502 assembly routine in DATA statements,
\rem POKEs each byte to address $300 (768), then CALLs it to print "HELLO WORLD!" and return to BASIC.
\rem
\rem Assembly routine layout at $300:
\rem   $300: A2 00          LDX #0
\rem   $302: BD 10 03       LDA $0310,X    (load char from msg table)
\rem   $305: F0 08          BEQ $30F        (done? jump to RTS)
\rem   $307: 09 80          ORA #$80        (80-column output)
\rem   $309: 20 ED FD       JSR $FDED       (COUT: print A-register char)
\rem   $30C: E8             INX             (advance index)
\rem   $30D: D0 F3          BNE $302        (loop back)
\rem   $30F: 60             RTS             (return to BASIC)
\rem   msg at $310: "HELLO WORLD!" + CR ($0D) + $00 = 14 bytes
\rem Total embedded routine: 30 bytes (16 code + 14 msg)

10 REM --- POKE embedded assembly bytes to $300 ---
20 FOR I=0 TO 29
30 READ B:POKE 768+I,B
40 NEXT I
50 REM --- Run the embedded routine via CALL ---
60 CALL 768
70 END

\rem Hex byte values (verified by ACME 0.97, apple format):
\rem A2 00 BD 10 03 F0 08 09 80 20 ED FD E8 D0 F3 60 48 45 4C 4C 4F 20 57 4F 52 4C 44 21 0D 00
100 DATA 162, 0, 189, 16, 3, 240, 8, 9, 128, 32
110 DATA 237, 253, 232, 208, 243, 96, 72, 69, 76, 76
120 DATA 79, 32, 87, 79, 82, 76, 68, 33, 13, 0

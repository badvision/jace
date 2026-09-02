\rem HELLO WORLD! -- Mixed BASIC + Assembly via DATA/POKE/CALL
\rem This program embeds a 27-byte 6502 assembly routine in DATA statements,
\rem POKEs each byte to address $300 (768), then CALLs it to print "HELLO WORLD!" and return to BASIC.
\rem
\rem Assembly routine layout at $300:
\rem   $300: A2 00          LDX #0
\rem   $302: BD 0E 03       LDA $030E,X    (load char from msg table)
\rem   $305: F0 06          BEQ $30D        (done? jump to RTS)
\rem   $307: 20 ED FD       JSR $FDED       (COUT: print A-register char)
\rem   $30A: E8             INX             (advance index)
\rem   $30B: D0 F5          BNE $302        (loop back)
\rem   $30D: 60             RTS             (return to BASIC)
\rem   msg at $30E: "HELLO WORLD!" + CR ($0D) = 12 bytes
\rem Total embedded routine: 27 bytes (15 code + 12 msg)

10 REM --- POKE embedded assembly bytes to $300 ---
20 FOR I=0 TO 26
30 READ B:POKE 768+I,B
40 NEXT I
50 REM --- Run the embedded routine via CALL ---
60 CALL 768
70 END

\rem Hex byte values (verified by ACME 0.97, apple format):
\rem A2 00 BD 0E 03 F0 06 20 ED FD E8 D0 F5 60 48 45 4C 4C 4F 20 57 4F 52 4C 44 21 0D
100 DATA 162, 0, 189, 14, 3, 240, 6, 32, 237, 253
110 DATA 232, 208, 245, 96, 72, 69, 76, 76, 79, 32
120 DATA 87, 79, 82, 76, 68, 33, 13

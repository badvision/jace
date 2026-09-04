* = $800
; ============================================================================
; Roman numeral converter -- integer in (keyboard), Roman numeral out (screen).
;
; Apple II / 6502, ACME 0.97 syntax. Load at $800, run with 800G (JACE).
;
; How it works
;   1. Prints the prompt "NUMBER: " itself, char by char, via COUT.
;   2. Calls the ROM line editor at $FD6F (GETLN, minus the two instructions
;      at $FD6A-$FD6E that would print the ROM's own prompt character). The
;      line editor reads the typed line into the buffer at $200 and RETURNS
;      THE CHARACTER COUNT IN X (CR is not counted).
;   3. Parses the ASCII digits in $200 into a 16-bit integer n.
;   4. Converts n (1..3999) to a Roman numeral and prints it via COUT.
;
; Two Apple II / ACME gotchas this depends on
;   * KEYBOARD HIGH BIT: CGET returns every typed character with bit 7 SET.
;     '1' arrives as $B1, Enter as $8D (not $0D). The parser therefore does
;     `AND #$7F` before subtracting '0'.
;   * IMPLICIT ACCUMULATOR: ACME has no `asl a` / `rol a`. The accumulator
;     form is written bare -- `asl` / `rol`. (An operand makes it a memory
;     address, e.g. `asl $20`.)
;
; Zero-page map (all in RAM, none collide with the ROM's $00-$3F variables):
;   $40/$41 = pointer to the string currently being printed (putstr).
;   len/nl/nh/dig/s1 = parser scratch (live in the data block, absolute).
; ============================================================================

start
    ; --- 1. print our own prompt, char by char (COUT) ---
    ldx #0
pr_l
    lda prompt,x
    beq pr_d
    ora #$80          ; 80-column output
    jsr $FDED         ; COUT: console output, char in A
    inx
    jmp pr_l
pr_d
    ; --- 2. read the line: X = length, buffer at $200 (chars have bit 7 set) ---
    jsr $FD6F         ; ROM line editor (skips the ROM's prompt char)
    stx len           ; save the char count
    ; --- 3. parse ASCII digits into 16-bit n in nl:nh ---
    ldx #0
    lda #0
    sta nl
    sta nh
p_l
    lda $200,x
    and #$7f          ; strip the keyboard high bit
    sec
    sbc #48           ; '0'
    sta dig
    ; n = n*10 + dig   (n*10 = n*8 + n*2, no multiply instruction on the 6502)
    lda nl
    asl               ; nl*2, C = old nl bit 0
    sta s1
    lda nh
    rol               ; nh*2
    sta s1+1          ; s1 = n*2 (saved for the add below)
    lda nl
    asl               ; n = n*2
    sta nl
    lda nh
    rol
    sta nh
    lda nl
    asl               ; n = n*4
    sta nl
    lda nh
    rol
    sta nh
    lda nl
    asl               ; n = n*8
    sta nl
    lda nh
    rol
    sta nh
    lda nl            ; n = n*8 + n*2 = n*10
    clc
    adc s1
    sta nl
    lda nh
    adc s1+1
    sta nh
    lda nl            ; n = n*10 + digit
    clc
    adc dig
    sta nl
    lda nh
    adc #0
    sta nh
    inx
    cpx len
    bcc p_l
    ; --- bounds: n must be 1..3999, else fall through to err ---
    lda nl
    ora nh
    beq err           ; n == 0
    lda nh
    cmp #$0f
    bcc rom           ; nh < $0F  ->  n < 3840, fine
    bne err           ; nh > $0F  ->  n >= 4096, too big
    lda nl            ; nh == $0F: error iff n >= 4000 ($0FA0)
    cmp #$a0
    bcs err
rom
    ; --- 4. 16-bit n -> Roman numeral (greedy table walk) ---
    ldx #0
r_l
    lda nh
    cmp vh,x
    beq r_cmplo
    bcs r_print       ; n >= this value: print it, subtract, repeat
    jmp r_adv
r_cmplo
    lda nl
    cmp vl,x
    bcs r_print
    jmp r_adv
r_print
    lda slo,x         ; load the string pointer for this symbol
    sta $40
    lda shi,x
    sta $41
    jsr putstr
    lda nl            ; n = n - value
    sec
    sbc vl,x
    sta nl
    lda nh
    sbc vh,x
    sta nh
    lda nl
    ora nh
    bne r_l           ; n still non-zero, keep walking
    lda #$0d
    ora #$80          ; 80-column output
    jsr $FDED         ; newline after the numeral
    jmp halt
r_adv                 ; n < this value, skip to the next table entry (4 bytes)
    inx
    inx
    inx
    inx
    cpx #52           ; 13 entries * 4 bytes = 52
    bne r_l
    lda #$0d
    ora #$80          ; 80-column output
    jsr $FDED
    jmp halt
err
    lda #$2a          ; '*' -- out of range
    ora #$80
    jsr $FDED
    lda #$0d
    ora #$80          ; 80-column output
    jsr $FDED
halt
    rts               ; return to caller (BRUN/BASIC)

putstr                ; print the null-terminated string at ($40)
    ldy #0
pu_l
    lda ($40),y
    beq pu_d
    ora #$80          ; 80-column output
    jsr $FDED
    iny
    bne pu_l
pu_d
    rts

; --- data ---
vl    = val           ; table column offsets
vh    = val + 1
slo   = val + 2
shi   = val + 3
len   !byte 0         ; char count from the line editor
nl    !byte 0         ; n low
nh    !byte 0         ; n high
dig   !byte 0         ; current digit
s1    !byte 0, 0      ; n*2 temp
val                   ; 13 entries: value(2) + string pointer(2), descending
    !byte <1000, >1000, <sM, >sM
    !byte <900, >900, <sCM, >sCM
    !byte <500, >500, <sD, >sD
    !byte <400, >400, <sCD, >sCD
    !byte <100, >100, <sC, >sC
    !byte <90, >90, <sXC, >sXC
    !byte <50, >50, <sL, >sL
    !byte <40, >40, <sXL, >sXL
    !byte <10, >10, <sX, >sX
    !byte <9, >9, <sIX, >sIX
    !byte <5, >5, <sV, >sV
    !byte <4, >4, <sIV, >sIV
    !byte <1, >1, <sI, >sI
sM:  !text "M"
     !byte 0
sCM: !text "CM"
     !byte 0
sD:  !text "D"
     !byte 0
sCD: !text "CD"
     !byte 0
sC:  !text "C"
     !byte 0
sXC: !text "XC"
     !byte 0
sL:  !text "L"
     !byte 0
sXL: !text "XL"
     !byte 0
sX:  !text "X"
     !byte 0
sIX: !text "IX"
     !byte 0
sV:  !text "V"
     !byte 0
sIV: !text "IV"
     !byte 0
sI:  !text "I"
     !byte 0
prompt:
    !text "NUMBER: "
    !byte 0

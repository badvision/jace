* = $300
    ldx #0
loop
    lda msg,x
    beq done
    jsr $FDED         ; COUT: Apple II ROM console output, char in A
    inx
    bne loop
done
    rts               ; Return to BASIC (CALL pushes return address — RTS pops it)
msg
    !text "HELLO WORLD!"
    !byte $0D

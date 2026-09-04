* = $300
    ldx #0
loop
    lda msg,x
    beq done
    ora #$80          ; 80-column output
    jsr $FDED         ; COUT: Apple II ROM console output, char in A
    inx
    bne loop
done
    rts               ; Return to BASIC (CALL pushes return address — RTS pops it)
msg
    !text "HELLO WORLD!"
    !byte $0D
    !byte 0           ; terminate COUT-until-0

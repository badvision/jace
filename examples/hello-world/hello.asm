* = $800
start
    ldx #0
next
    lda msg,x
    beq done
    ora #$80          ; 80-column output
    jsr $FDED         ; COUT: Apple II ROM console output, char in A (docs/jace/debugging-guide.md:102)
    inx
    jmp next
done
    rts               ; return to caller (BRUN/BASIC)
msg:
    !text "HELLO WORLD"
    !byte $0D
    !byte 0           ; terminate COUT-until-0

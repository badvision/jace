* = $800
start
    ldx #0
next
    lda msg,x
    beq done
    jsr $FDED         ; COUT: Apple II ROM console output, char in A (docs/jace/debugging-guide.md:102)
    inx
    jmp next
done
    jmp done
msg:
    !text "HELLO WORLD"
    !byte $0D

* = $800

; DHGR Color Wheel - Lo-res palette colors via FLIP_NYBBLE remapping
;
; Each "color" is a lo-res index (0-15) remapped through the DHGR FLIP_NYBBLE system.
; The 7 bars shown are: magenta, red, yellow, green, cyan, violet, blue
; (FLIP indices: 1, 9, 13, 4, 6, 7, 3)
;
; Reference for all 16 lo-res colors via DHGR FLIP_NYBBLE:
;   FLIP 0 = black, 1 = magenta, 2 = royal blue, 3 = violet,
;   4 = green, 5 = gray, 6 = cyan/blue, 7 = lavender,
;   8 = olive/brown, 9 = red, 10 = yellow, 11 = chartreuse,
;   12 = blue, 13 = yellow (bright), 14 = white, 15 = white (max)
;
; IMPORTANT: DHGR "solid" colors require ALL 4 bytes per column-pair to jointly
; encode 7 repetitions of a single nibble value N through the 28-bit LSB-first
; assembly. A solid color CANNOT be produced by writing a single byte repeatedly.
; See FLIP_NYBBLE table in VideoDHGR.java for the remapping function.

init:
    ldx #$FF
    stx $C05F         ; DHIRES off
    stx $C056         ; HIRES off
    stx $C00C         ; 80COL off
    stx $C052         ; MIXED off
    stx $C050         ; TEXT off
    stx $C000         ; 80STORE off
    stx $C054         ; PAGE2 off

    ldx #$FF
    stx $C05E         ; DHIRES on
    stx $C057         ; HIRES on
    stx $C00D         ; 80COL on
    stx $C001         ; 80STORE on

; Zero-page: $30=$lo, $31=$hi (zp indirect-indexed with Y)
;            $32 = row counter

    ldy #0
fill_row:
    sty $32
    lda row_hi_tab,y
    sta $31
    lda row_lo_tab,y
    sta $30

; --- Pass 1: main bank (PAGE2=OFF) ---
    ldy #0
    lda #$00
    sta $C054         ; PAGE2 off
main_pass:
    lda col_main_tab,y
    sta ($30),y
    iny
    cpy #40
    bne main_pass

; --- Pass 2: aux bank (PAGE2=ON) ---
    ldy #0
    lda #$FF
    sta $C055         ; PAGE2 on
aux_pass:
    lda col_aux_tab,y
    sta ($30),y
    iny
    cpy #40
    bne aux_pass

; Next row
    ldy $32
    iny
    cpy #192
    bne fill_row

done:
    jmp done

col_main_tab:
    !byte $11, $44, $19, $66, $5D, $77, $44, $11
    !byte $66, $19, $77, $5D, $33, $4C, $11, $44
    !byte $19, $66, $5D, $77, $44, $11, $66, $19
    !byte $77, $5D, $33, $4C, $11, $44, $19, $66
    !byte $5D, $77, $44, $11, $66, $19, $77, $5D

; Aux bank: [b1,b3] per group
col_aux_tab:
    !byte $08, $22, $4C, $33, $6E, $3B, $22, $08
    !byte $33, $4C, $3B, $6E, $19, $66, $08, $22
    !byte $4C, $33, $6E, $3B, $22, $08, $33, $4C
    !byte $3B, $6E, $19, $66, $08, $22, $4C, $33
    !byte $6E, $3B, $22, $08, $33, $4C, $3B, $6E

; DRAM row address tables
row_hi_tab:
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F

row_lo_tab:
    !byte $00, $00, $00, $00, $00, $00, $00, $00
    !byte $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00
    !byte $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00
    !byte $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00
    !byte $80, $80, $80, $80, $80, $80, $80, $80
    !byte $28, $28, $28, $28, $28, $28, $28, $28
    !byte $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28
    !byte $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28
    !byte $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28
    !byte $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $50, $50, $50, $50, $50, $50, $50, $50
    !byte $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50
    !byte $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50
    !byte $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50
    !byte $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0

; =============================================================================
; DHGR Pinwheel -- procedural render using the Applesoft FP ROM package.
; BRUN PINWHEEL,A$4800.
; =============================================================================

GIVAYF   = $E2F2
LDFAC    = $EAF9
LDARG    = $E9E3
CPARGFAC = $EB53
MAF      = $EB66
FADDT    = $E7C1
FSUBT    = $E7AA
FMULTT   = $E982
FDIVT    = $EA69
SQR      = $EE8D
ATN      = $F09E
GETADR   = $E752
FAC      = $9D
FACSGN   = $A2
ARG      = $A5
ARGSGN   = $AA
SGNCPR   = $AB
FACEXT   = $AC
LINNUM   = $50

!set WSPTR = $5800
VROW_LO = $30
VROW_HI = $31
AROW_LO = $34
AROW_HI = $35
!macro reserve ~.addr, .n {
    .addr = WSPTR
    !set WSPTR = WSPTR + .n
}
+reserve ~ROW_IDX,   1
+reserve ~COL_IDX,   1
+reserve ~BAND_BYTE, 1
+reserve ~DX_LO,     1
+reserve ~DX_HI,     1
+reserve ~DY_LO,     1
+reserve ~DY_HI,     1
+reserve ~ABSDX_LO,  1
+reserve ~ABSDX_HI,  1
+reserve ~DXSIGN,    1
+reserve ~DYSIGN,    1
+reserve ~DY2_SAVE,  6
+reserve ~DIST_SAVE, 6
+reserve ~THETA_SAVE,6
+reserve ~PREMOD_SAVE,6
+reserve ~OUTIDX,    1
+reserve ~TMP,       1
+reserve ~BYTE_A0,   1
+reserve ~BYTE_M0,   1
+reserve ~BYTE_A1,   1
+reserve ~BYTE_M1,   1
+reserve ~NIB0,      1
+reserve ~NIB1,      1
+reserve ~NIB2,      1
+reserve ~NIB3,      1
+reserve ~NIB4,      1
+reserve ~NIB5,      1
+reserve ~NIB6,      1
+reserve ~MAIN_BUF,  40
+reserve ~AUX_BUF,   40
+reserve ~PIXELS,    140

!macro dbg .id {
    !byte $FC, $44, .id
}
!macro fbne .t { beq * + 5 : jmp .t }
!macro fbeq .t { bne * + 5 : jmp .t }
!macro fbmi .t { bpl * + 5 : jmp .t }
!macro fbcc .t { bcs * + 5 : jmp .t }
!macro fbcs .t { bcc * + 5 : jmp .t }
!macro fbpl .t { bmi * + 5 : jmp .t }

!macro ld_fac_int .hib, .lob {
    lda .hib
    ldy .lob
    jsr GIVAYF
    lda #0
    sta FACEXT
}
!macro ld_fac_const .tbl {
    lda #<.tbl
    ldy #>.tbl
    jsr LDFAC
    lda #0
    sta FACEXT
}
!macro fadd {
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FADDT
}
!macro fmul {
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FMULTT
}
!macro fdiv {
    lda FACSGN
    eor ARGSGN
    sta SGNCPR
    lda FAC
    jsr FDIVT
}
!macro save_fac .buf {
    lda FAC
    sta .buf
    lda FAC+1
    sta .buf+1
    lda FAC+2
    sta .buf+2
    lda FAC+3
    sta .buf+3
    lda FAC+4
    sta .buf+4
    lda FAC+5
    sta .buf+5
}
!macro restore_fac .buf {
    lda .buf
    sta FAC
    lda .buf+1
    sta FAC+1
    lda .buf+2
    sta FAC+2
    lda .buf+3
    sta FAC+3
    lda .buf+4
    sta FAC+4
    lda .buf+5
    sta FAC+5
    lda #0
    sta FACEXT
}
; r2 = dx*dx + dy*dy, leaves FAC=r2 and ARG=r2
!macro compute_r2 {
    +ld_fac_int DX_HI, DX_LO
    jsr MAF
    +ld_fac_int DX_HI, DX_LO
    +fmul
    jsr MAF
    +restore_fac DY2_SAVE
    +fadd
    jsr MAF
}

; =============================================================================
* = $4800
; =============================================================================
init:
    stx $C001
    stx $C00D
    stx $C05E
    stx $C057
    stx $C052
    stx $C050
    stx $C054
    ldy #0

rowloop:
    sty ROW_IDX

    lda #0
    sta DY_HI
    tya
    asl
    rol DY_HI
    sec
    sbc #<192
    sta DY_LO
    lda DY_HI
    sbc #>192
    sta DY_HI
    lda DY_HI
    and #$80
    sta DYSIGN

    +ld_fac_int DY_HI, DY_LO
    jsr MAF
    +ld_fac_int DY_HI, DY_LO
    +fmul
    +save_fac DY2_SAVE

    ldy ROW_IDX
    lda band_info,y
    sta BAND_BYTE

    ldx #0
clearpix:
    lda #0
    sta PIXELS,x
    inx
    cpx #140
    bne clearpix

    ldx #0
colloop:
    stx COL_IDX

    lda #0
    sta DX_HI
    txa
    asl
    rol DX_HI
    asl
    rol DX_HI
    sec
    sbc #<280
    sta DX_LO
    lda DX_HI
    sbc #>280
    sta DX_HI
    lda DX_HI
    and #$80
    sta DXSIGN
    +fbpl dxpos
    lda #0
    sec
    sbc DX_LO
    sta ABSDX_LO
    lda #0
    sbc DX_HI
    sta ABSDX_HI
    jmp dxabsdone
dxpos:
    lda DX_LO
    sta ABSDX_LO
    lda DX_HI
    sta ABSDX_HI
dxabsdone:

    +compute_r2
    ; GETADR only handles values < 65536 (exponent byte < $91); anything at or
    ; above that is far beyond the pinwheel radius, so skip straight to background.
    lda FAC
    cmp #$91
    +fbcs bg_path
    jsr GETADR          ; r2 -> LINNUM (16-bit unsigned int)

    ; hub if r2 <= 484
    lda LINNUM+1
    bne chk_pw
    lda LINNUM
    cmp #<485
    +fbcc in_hub
    jmp chk_pw
chk_pw:
    ; pinwheel if r2 <= 22500 ($57E4)
    lda LINNUM+1
    cmp #>22501
    +fbcc in_pinwheel
    bne bg_path
    lda LINNUM
    cmp #<22501
    +fbcc in_pinwheel
bg_path:

    lda ROW_IDX
    clc
    adc COL_IDX
    and #1
    +fbeq bg_lo
    lda BAND_BYTE
    lsr
    lsr
    lsr
    lsr
    jmp bg_store
bg_lo:
    lda BAND_BYTE
bg_store:
    and #$0F
    jmp store_px

in_hub:
    lda #$0F
    jmp store_px

in_pinwheel:
    jsr CPARGFAC
    jsr SQR
    +save_fac DIST_SAVE

    ; dx=0 (dead center column) would make FDIVT divide by zero; atan2(dy,0)
    ; is exactly +-PI/2 in that case, so special-case it directly.
    lda ABSDX_LO
    ora ABSDX_HI
    +fbeq dx_zero_case

    +ld_fac_int DY_HI, DY_LO
    jsr MAF
    +ld_fac_int ABSDX_HI, ABSDX_LO
    +fdiv
    jsr ATN
    jmp atn_done

dx_zero_case:
    lda DYSIGN
    +fbeq use_neg_half
    +ld_fac_const PI_HALF_NEG
    jmp atn_done
use_neg_half:
    +ld_fac_const PI_HALF_POS
atn_done:

    lda DXSIGN
    +fbeq have_theta
    lda FACSGN
    eor #$80
    sta FACSGN
    jsr MAF
    lda DYSIGN
    +fbeq use_ppi
    +ld_fac_const PI_NEG
    jmp pi_added
use_ppi:
    +ld_fac_const PI_CONST
pi_added:
    +fadd
have_theta:
    +save_fac THETA_SAVE

    +restore_fac DIST_SAVE
    jsr MAF
    +ld_fac_const TWIST_CONST
    +fmul
    jsr MAF
    +restore_fac THETA_SAVE
    +fadd



    jsr MAF
    +ld_fac_const PI_CONST
    +fadd

    +save_fac PREMOD_SAVE
    jsr MAF
    +ld_fac_const TWOPI_CONST
    lda FAC
    jsr FSUBT
    lda FACSGN
    +fbpl mod_done
    +restore_fac PREMOD_SAVE
mod_done:
    jsr MAF
    +ld_fac_const SEVEN_TWOPI
    +fmul
    jsr GETADR
    lda LINNUM
    cmp #7
    bne sector_ok
    lda #0
sector_ok:
    tax
    lda pinwheel_tab,x

store_px:
    ldx COL_IDX
    sta PIXELS,x
    inx
    cpx #140
    +fbne colloop

    jsr pack_and_write

    ldy ROW_IDX
    iny
    cpy #192
    +fbne rowloop
    jmp done

; =============================================================================
pack_and_write:
    ldy ROW_IDX
    lda row_hi,y
    sta VROW_HI
    lda row_lo,y
    sta VROW_LO

    lda #0
    sta OUTIDX
    ldx #0
clear_buffers:
    lda #0
    sta MAIN_BUF,x
    sta AUX_BUF,x
    inx
    cpx #40
    bne clear_buffers
    ldx #0
pw_group:
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB0
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB1
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB2
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB3
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB4
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB5
    inx
    lda PIXELS,x
    tay
    lda inv_flip_tab,y
    sta NIB6
    inx

    lda NIB1
    and #7
    asl
    asl
    asl
    asl
    ora NIB0
    sta BYTE_A0

    lda NIB3
    and #3
    asl
    asl
    asl
    asl
    asl
    sta TMP
    lda NIB2
    asl
    ora TMP
    sta TMP
    lda NIB1
    lsr
    lsr
    lsr
    and #1
    ora TMP
    sta BYTE_M0

    lda NIB5
    and #1
    asl
    asl
    asl
    asl
    asl
    asl
    sta TMP
    lda NIB4
    asl
    asl
    ora TMP
    sta TMP
    lda NIB3
    lsr
    lsr
    and #3
    ora TMP
    sta BYTE_A1

    lda NIB6
    asl
    asl
    asl
    sta TMP
    lda NIB5
    lsr
    and #7
    ora TMP
    sta BYTE_M1

    ldy OUTIDX
    lda AUX_BUF,y
    ora BYTE_A0
    sta AUX_BUF,y
    lda MAIN_BUF,y
    ora BYTE_M0
    sta MAIN_BUF,y
    iny
    lda AUX_BUF,y
    ora BYTE_A1
    sta AUX_BUF,y
    lda MAIN_BUF,y
    ora BYTE_M1
    sta MAIN_BUF,y

    lda OUTIDX
    clc
    adc #2
    sta OUTIDX

    cpx #140
    +fbne pw_group

    ; two-pass copy to real video memory, PAGE2 toggled to select bank
    lda #0
    sta $C054          ; PAGE2 off -> main bank at VROW address
    ldy #0
copy_main:
    lda MAIN_BUF,y
    sta (VROW_LO),y
    iny
    cpy #40
    bne copy_main

    lda #0
    sta $C055          ; PAGE2 on -> aux bank at the SAME VROW address
    ldy #0
copy_aux:
    lda AUX_BUF,y
    sta (VROW_LO),y
    iny
    cpy #40
    bne copy_aux

    lda #0
    sta $C054          ; leave PAGE2 off
    rts

done:
    rts               ; return to caller (BRUN/BASIC)


; ---- small helper tables (geometry/color, not the image itself) ----
row_hi:
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C, $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D, $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E, $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F, $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C, $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D, $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E, $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F, $23, $27, $2B, $2F, $33, $37, $3B, $3F
    !byte $20, $24, $28, $2C, $30, $34, $38, $3C, $20, $24, $28, $2C, $30, $34, $38, $3C
    !byte $21, $25, $29, $2D, $31, $35, $39, $3D, $21, $25, $29, $2D, $31, $35, $39, $3D
    !byte $22, $26, $2A, $2E, $32, $36, $3A, $3E, $22, $26, $2A, $2E, $32, $36, $3A, $3E
    !byte $23, $27, $2B, $2F, $33, $37, $3B, $3F, $23, $27, $2B, $2F, $33, $37, $3B, $3F
row_lo:
    !byte $00, $00, $00, $00, $00, $00, $00, $00, $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00, $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00, $80, $80, $80, $80, $80, $80, $80, $80
    !byte $00, $00, $00, $00, $00, $00, $00, $00, $80, $80, $80, $80, $80, $80, $80, $80
    !byte $28, $28, $28, $28, $28, $28, $28, $28, $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28, $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28, $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $28, $28, $28, $28, $28, $28, $28, $28, $A8, $A8, $A8, $A8, $A8, $A8, $A8, $A8
    !byte $50, $50, $50, $50, $50, $50, $50, $50, $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50, $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50, $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
    !byte $50, $50, $50, $50, $50, $50, $50, $50, $D0, $D0, $D0, $D0, $D0, $D0, $D0, $D0
band_info:
    !byte $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22, $22
    !byte $22, $22, $22, $23, $23, $23, $23, $23, $23, $23, $23, $23, $23, $23, $23, $23
    !byte $23, $23, $23, $33, $33, $33, $33, $33, $33, $33, $33, $33, $33, $33, $33, $33
    !byte $33, $33, $33, $33, $33, $33, $31, $31, $31, $31, $31, $31, $31, $31, $31, $31
    !byte $31, $31, $31, $31, $31, $31, $11, $11, $11, $11, $11, $11, $11, $11, $11, $11
    !byte $11, $11, $11, $11, $11, $11, $11, $11, $19, $19, $19, $19, $19, $19, $19, $19
    !byte $19, $19, $19, $19, $19, $19, $19, $19, $99, $99, $99, $99, $99, $99, $99, $99
    !byte $99, $99, $99, $99, $99, $99, $99, $99, $99, $99, $99, $9D, $9D, $9D, $9D, $9D
    !byte $9D, $9D, $9D, $9D, $9D, $9D, $9D, $9D, $9D, $9D, $9D, $DD, $DD, $DD, $DD, $DD
    !byte $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DD, $DB, $DB, $DB
    !byte $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $DB, $BB, $BB, $BB
    !byte $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB, $BB

inv_flip_tab:
    !byte $00, $08, $01, $09, $02, $0A, $03, $0B, $04, $0C, $05, $0D, $06, $0E, $07, $0F
pinwheel_tab:
    !byte $0D, $0C, $0E, $06, $03, $01, $09

HUB_THRESH   !byte $89, $72, $00, $00, $00
PW_THRESH    !byte $8F, $2F, $C8, $00, $00
PI_HALF_POS  !byte $81, $49, $0F, $DA, $A2
PI_HALF_NEG  !byte $81, $C9, $0F, $DA, $A2
PI_CONST     !byte $82, $49, $0F, $DA, $A2
PI_NEG       !byte $82, $C9, $0F, $DA, $A2
TWOPI_CONST  !byte $83, $49, $0F, $DA, $A2
TWIST_CONST  !byte $79, $44, $9B, $A5, $E3
SEVEN_TWOPI  !byte $81, $0E, $9A, $53, $01


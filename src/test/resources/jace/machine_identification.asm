JSR IDENTIFY
JMP END_DETECT_PROGRAM
;;; From http://www.1000bit.it/support/manuali/apple/technotes/misc/tn.misc.02.html
;;; *********************************************
;;; *                                           *
;;; *  Apple II Family Identification Program   *
;;; *                                           *
;;; *               Version 2.2                 *
;;; *                                           *
;;; *               March, 1990                 *
;;; *                                           *
;;; *  Includes support for the Apple IIe Card  *
;;; *  for the Macintosh LC.                    *
;;; *                                           *
;;; *********************************************

;  First, some global equates for the routine:
IIplain    = $01           ;Apple II
IIplus     = $02           ;Apple II+
IIIem      = $03           ;Apple /// in emulation mode
IIe        = $04           ;Apple IIe
IIc        = $05           ;Apple IIc
IIeCard    = $06           ;Apple IIe Card for the Macintosh LC

.safe       = $0001        ;start of code relocated to zp
.location   = $06          ;zero page location to use

.test1      = $AA          ;test byte #1
.test2      = $55          ;lsr of test1
.test3      = $88          ;test byte #3
.test4      = $EE          ;test byte #4

.begpage1   = $400         ;beginning of text page 1
.begpage2   = $800         ;beginning of text page 2
.begsprse   = $C00         ;byte after text page 2

.clr80col   = $C000        ;disable 80-column store
.set80col   = $C001        ;enable 80-column store
.rdmainram  = $C002        ;read main ram
.rdcardram  = $C003        ;read aux ram
.wrmainram  = $C004        ;write main ram
.wrcardram  = $C005        ;write aux ram
.rdramrd    = $C013        ;are we reading aux ram?
.rdaltzp    = $C016        ;are we reading aux zero page?
.rd80col    = $C018        ;are we using 80-columns?
.rdtext     = $C01A        ;read if text is displayed
.rdpage2    = $C01C        ;read if page 2 is displayed
.txtclr     = $C050        ;switch in graphics
.txtset     = $C051        ;switch in text
.txtpage1   = $C054        ;switch in page 1
.txtpage2   = $C055        ;switch in page 2
.ramin      = $C080        ;read LC bank 2, write protected
.romin      = $C081        ;read ROM, 2 reads write enable LC
.lcbank1    = $C08B        ;LC bank 1 enable

.lc1        = $E000        ;bytes to save for LC
.lc2        = $D000        ;save/restore routine
.lc3        = $D400
.lc4        = $D800

.idroutine  = $FE1F        ;IIgs id routine

;  Start by saving the state of the language card banks and
;  by switching in main ROM.

IDENTIFY
    php               ;save the processor state
    sei               ;before disabling interrupts
    lda .lc1          ;save four bytes from
    sta .save         ;ROM/RAM area for later
    lda .lc2          ;restoring of RAM/ROM
    sta .save+1       ;to original condition
    lda .lc3
    sta .save+2
    lda .lc4
    sta .save+3
    lda $C081         ;read ROM
    lda $C081
    lda #0            ;start by assuming unknown machine
    sta MACHINE
    sta ROMLEVEL
.IdStart
    lda .location     ;save zero page locations
    sta .save+4       ;for later restoration
    lda .location+1
    sta .save+5
    lda #$FB          ;all ID bytes are in page $FB
    sta .location+1   ;save in zero page as high byte
    ldx #0            ;init pointer to start of ID table
.loop	lda .IDTable,x    ;get the machine we are testing for
    sta MACHINE       ;and save it
    lda .IDTable+1,x  ;get the ROM level we are testing for
    sta ROMLEVEL      ;and save it
    ora MACHINE       ;are both zero?
    beq .matched      ;yes - at end of list - leave

.loop2	inx               ;bump index to loc/byte pair to test
    inx
    lda .IDTable,x    ;get the byte that should be in ROM
    beq .matched      ;if zero, we're at end of list
    sta .location     ;save in zero page

    ldy #0            ;init index for indirect addressing
    lda .IDTable+1,x  ;get the byte that should be in ROM
    cmp (.location),y ;is it there?
    beq .loop2        ;yes, so keep on looping

.loop3	inx               ;we didn't match. Scoot to the end of the
    inx               ;line in the ID table so we can start
    lda .IDTable,x    ;checking for another machine
    bne .loop3
    inx               ;point to start of next line
    bne .loop         ;should always be taken

.matched	; anop

;  Here we check the 16-bit ID routine at idroutine ($FE1F).  If it
;  returns with carry clear, we call it again in 16-bit
;  mode to provide more information on the machine.

    !cpu 65816 {
.idIIgs
    sec               ;set the carry bit
    jsr .idroutine    ;Apple IIgs ID Routine
    bcc .idIIgs2      ;it's a IIgs or equivalent
    jmp .IIgsOut      ;nope, go check memory
.idIIgs2
    lda MACHINE       ;get the value for machine
    ora #$80          ;and set the high bit
    sta MACHINE       ;put it back
    clc               ;get ready to switch into native mode
    xce
    php               ;save the processor status
    rep #$30          ;sets 16-bit registers
    !al {             ;longa on
    !rl {             ;longi on
    jsr .idroutine    ;call the ID routine again
    sta .IIgsA        ;16-bit store!
    stx .IIgsX        ;16-bit store!
    sty .IIgsY        ;16-bit store!
    plp               ;restores 8-bit registers
    xce               ;switches back to whatever it was before
    }                 ;longi off
    }                 ;longa off

    ldy .IIgsY        ;get the ROM vers number (starts at 0)
    cpy #$02          ;is it ROM 01 or 00?
    bcs .idIIgs3      ;if not, don't increment
    iny               ;bump it up for romlevel
.idIIgs3
    sty ROMLEVEL      ;and put it there
    cpy #$01          ;is it the first ROM?
    bne .IIgsOut      ;no, go on with things
    lda .IIgsY+1      ;check the other byte too
    bne .IIgsOut      ;nope, it's a IIgs successor
    lda #$7F          ;fix faulty ROM 00 on the IIgs
    sta .IIgsA
.IIgsOut	; anop
}

;;; ******************************************
;;; * This part of the code checks for the   *
;;; * memory configuration of the machine.   *
;;; * If it's a IIgs, we've already stored   *
;;; * the total memory from above.  If it's  *
;;; * a IIc or a IIe Card, we know it's      *
;;; * 128K; if it's a ][+, we know it's at   *
;;; * least 48K and maybe 64K.  We won't     *
;;; * check for less than 48K, since that's  *
;;; * a really rare circumstance.            *
;;; ******************************************

.exit	lda MACHINE       ;get the machine kind
    bmi .exit128      ;it's a 16-bit machine (has 128K)
    cmp #IIc          ;is it a IIc?
    beq .exit128      ;yup, it's got 128K
    cmp #IIeCard      ;is it a IIe Card?
    beq .exit128      ;yes, it's got 128K
    cmp #IIe          ;is it a IIe?
    bne .contexit     ;yes, go muck with aux memory
    jmp .muckaux
.contexit
    cmp #IIIem        ;is it a /// in emulation?
    bne .exitII       ;nope, it's a ][ or ][+
    lda #48           ;/// emulation has 48K
    jmp .exita
.exit128
    lda #128          ;128K
.exita	sta MEMORY
.exit1	lda .lc1          ;time to restore the LC
    cmp .save         ;if all 4 bytes are the same
    bne .exit2        ;then LC was never on so
    lda .lc2          ;do nothing
    cmp .save+1
    bne .exit2
    lda .lc3
    cmp .save+2
    bne .exit2
    lda .lc4
    cmp .save+3
    beq .exit6
.exit2	lda $C088         ;no match! so turn first LC
    lda .lc1          ;bank on and check
    cmp .save
    beq .exit3
    lda $C080
    jmp .exit6
.exit3	lda .lc2
    cmp .save+1       ;if all locations check
    beq .exit4        ;then do more more else
    lda $C080         ;turn on bank 2
    jmp .exit6
.exit4	lda .lc3          ;check second byte in bank 1
    cmp .save+2
    beq .exit5
    lda $C080         ;select bank 2
    jmp .exit6
.exit5	lda .lc4          ;check third byte in bank 1
    cmp .save+3
    beq .exit6
    lda $C080         ;select bank 2
.exit6	plp               ;restore interrupt status
    lda .save+4       ;put zero page back
    sta .location
    lda .save+5       ;like we found it
    sta .location+1
    rts               ;and go home.

.exitII
    lda .lcbank1      ;force in language card
    lda .lcbank1      ;bank 1
    ldx .lc2          ;save the byte there
    lda #.test1       ;use this as a test byte
    sta .lc2
    eor .lc2          ;if the same, should return zero
    bne .noLC
    lsr .lc2          ;check twice just to be sure
    lda #.test2       ;this is the shifted value
    eor .lc2          ;here's the second check
    bne .noLC
    stx .lc2          ;put it back!
    lda #64           ;there's 64K here
    jmp .exita
.noLC	lda #48           ;no restore - no LC!
    jmp .exita        ;and get out of here

.muckaux
    ldx .rdtext       ;remember graphics in X
    lda .rdpage2      ;remember current video display
    asl               ;in the carry bit
    lda #.test3       ;another test character
    bit .rd80col      ;remember video mode in N
    sta .set80col     ;enable 80-column store
    php               ;save N and C flags
    sta .txtpage2     ;set page two
    sta .txtset       ;set text
    ldy .begpage1     ;save first character
    sta .begpage1     ;and replace it with test character
    lda .begpage1     ;get it back
    sty .begpage1     ;and put back what was there
    plp
    bcs .muck2        ;stay in page 2
    sta .txtpage1     ;restore page 1
.muck1	bmi .muck2        ;stay in 80-columns
    sta $c000         ;turn off 80-columns
.muck2	tay               ;save returned character
    txa               ;get graphics/text setting
    bmi .muck3
    sta .txtclr       ;turn graphics back on
.muck3	cpy #.test3       ;finally compare it
    bne .nocard       ;no 80-column card!
    lda .rdramrd      ;is aux memory being read?
    bmi .muck128      ;yup, there's 128K!
    lda .rdaltzp      ;is aux zero page used?
    bmi .muck128      ;yup!
    ldy #.done-.start
.move	ldx .start-1,y    ;swap section of zero page
    lda <.safe-1,y    ;code needing safe location during
    stx <.safe-1,y    ;reading of aux mem
    sta .start-1,Y
    dey
    bne .move
    jmp .safe         ;jump to safe ground
.back	php               ;save status
    ldy #.done-.start ;move zero page back
.move2	lda .start-1,y
    sta .safe-1,y
    dey
    bne .move2
    pla
    bcs .noaux
.isaux	jmp .muck128      ;there is 128K

;;; *  You can put your own routine at "noaux" if you wish to
;;; *  distinguish between 64K without an 80-column card and
;;; *  64K with an 80-column card.

.noaux	; anop
.nocard	lda #64           ;only 64K
    jmp .exita
.muck128
    jmp .exit128      ;there's 128K

;;; *  This is the routine run in the safe area not affected
;;; *  by bank-switching the main and aux RAM.

.start	lda #.test4       ;yet another test byte
    sta .wrcardram    ;write to aux while on main zero page
    sta .rdcardram    ;read aux ram as well
    sta .begpage2     ;check for sparse memory mapping
    lda .begsprse     ;if sparse, these will be the same
    cmp #.test4       ;value since they're 1K apart
    bne .auxmem       ;yup, there's 128K!
    asl .begsprse     ;may have been lucky so we'll
    lda .begpage2     ;change the value and see what happens
    cmp .begsprse
    bne .auxmem
    sec               ;oops, no auxiliary memory
    bcs .goback
.auxmem	clc
.goback	sta .wrmainram    ;write main RAM
    sta .rdmainram    ;read main RAM
    jmp .back         ;continue with program in main mem
.done	nop               ;end of relocated program marker


;;; *  The storage locations for the returned machine ID:

.IIgsA	 !word  0         ;16-bit field
.IIgsX	 !word  0         ;16-bit field
.IIgsY	 !word  0         ;16-bit field
.save	 !fill  6,0       ;six bytes for saved data

.IDTable
    ;dc  I1'1,1'      ;Apple ][
    ;dc  H'B3 38 00'
    !byte 1,1
    !byte $B3,$38,0

    ;dc  I1'2,1'      ;Apple ][+
    ;dc  H'B3 EA 1E AD 00'
    !byte 2,1
    !byte $B3,$EA,$1E,$AD,0

    ;dc  I1'3,1'      ;Apple /// (emulation)
    ;dc  H'B3 EA 1E 8A 00'
    !byte 3,1
    !byte $B3,$EA,$1E,$8A,0

    ;dc  I1'4,1'      ;Apple IIe (original)
    ;dc  H'B3 06 C0 EA 00'
    !byte 4,1
    !byte $B3,$06,$C0,$EA,0

;  Note: You must check for the Apple IIe Card BEFORE you
;  check for the enhanced Apple IIe since the first
;  two identification bytes are the same.

    ;dc  I1'6,1'      ;Apple IIe Card for the Macintosh LC (1st release)
    ;dc  H'B3 06 C0 E0 DD 02 BE 00 00'
    !byte 6,1
    !byte $B3,$06,$C0,$E0,$DD,$02,$BE,$00,0

    ;dc  I1'4,2'      ;Apple IIe (enhanced)
    ;dc  H'B3 06 C0 E0 00'
    !byte 4,2
    !byte $B3,$06,$C0,$E0,0

    ;dc  I1'5,1'      ;Apple IIc (original)
    ;dc  H'B3 06 C0 00 BF FF 00'
    !byte 5,1
    !byte $B3,$06,$C0,$00,$BF,$FF,0

    ;dc  I1'5,2'      ;Apple IIc (3.5 ROM)
    ;dc  H'B3 06 C0 00 BF 00 00'
    !byte 5,2
    !byte $B3,$06,$C0,$00,$BF,$00,0

    ;dc  I1'5,3'      ;Apple IIc (Mem. Exp)
    ;dc  H'B3 06 C0 00 BF 03 00'
    !byte 5,3
    !byte $B3,$06,$C0,$00,$BF,$03,0

    ;dc  I1'5,4'      ;Apple IIc (Rev. Mem. Exp.)
    ;dc  H'B3 06 C0 00 BF 04 00'
    !byte 5,4
    !byte $B3,$06,$C0,$00,$BF,$04,0

    ;dc  I1'5,5'      ;Apple IIc Plus
    ;dc  H'B3 06 C0 00 BF 05 00'
    !byte 5,5
    !byte $B3,$06,$C0,$00,$BF,$05,0

    ;dc  I1'0,0'      ;end of table
    !byte 0,0
END_DETECT_PROGRAM

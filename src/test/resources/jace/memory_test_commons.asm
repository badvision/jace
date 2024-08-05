jmp START
MACHINE = $800          ;the type of Apple II
ROMLEVEL = $801         ;which revision of the machine
MEMORY = $802           ;how much memory (up to 128K)

LCRESULT = $10
LCRESULT1 = $11

lda #0
sta LCRESULT

;; Zero-page locations.
SCRATCH = $1
SCRATCH2 = $2
SCRATCH3 = $3
LCRESULT = $10
LCRESULT1 = $11
AUXRESULT = $12
SOFTSWITCHRESULT = $13

CSW = $36
KSW = $38

PCL=$3A
PCH=$3B
A1L=$3C
A1H=$3D
A2L=$3E
A2H=$3F
A3L=$40
A3H=$41
A4L=$42
A4H=$43

!addr   tmp0 = $f9
!addr   tmp1 = $fa
!addr   tmp2 = $fb
!addr   tmp3 = $fc
!addr   tmp4 = $fd
!addr   tmp5 = $fe
!addr   tmp6 = $ff
.checkdata = tmp1

STRINGS = $8000
!set LASTSTRING = STRINGS

KBD      =   $C000
KBDSTRB  =   $C010

;; Monitor locations.
;HOME = $FC58
COUT = $FDED
COUT1 = $FDF0
KEYIN = $FD1B
CROUT = $FD8E
PRBYTE = $FDDA
PRNTYX = $F940

;; Softswitch locations.
RESET_80STORE = $C000
SET_80STORE = $C001
READ_80STORE = $C018

RESET_RAMRD = $C002
SET_RAMRD = $C003
READ_RAMRD = $C013

RESET_RAMWRT = $C004
SET_RAMWRT = $C005
READ_RAMWRT = $C014

RESET_INTCXROM = $C006
SET_INTCXROM = $C007
READ_INTCXROM = $C015

RESET_ALTZP = $C008
SET_ALTZP = $C009
READ_ALTZP = $C016

RESET_SLOTC3ROM = $C00A
SET_SLOTC3ROM = $C00B
READ_SLOTC3ROM = $C017

RESET_80COL = $C00C
SET_80COL = $C00D
READ_80COL = $C01F

RESET_ALTCHRSET = $C00E
SET_ALTCHRSET = $C00F
READ_ALTCHRSET = $C01E

RESET_TEXT = $C050
SET_TEXT = $C051
READ_TEXT = $C01A

RESET_MIXED = $C052
SET_MIXED = $C053
READ_MIXED = $C01B

RESET_PAGE2 = $C054
SET_PAGE2 = $C055
READ_PAGE2 = $C01C

RESET_HIRES = $C056
SET_HIRES = $C057
READ_HIRES = $C01D

RESET_AN3 = $C05E
SET_AN3 = $C05F

RESET_INTC8ROM = $CFFF

;; Readable things without corresponding set/reset pairs.
READ_HRAM_BANK2 = $C011
READ_HRAMRD = $C012
READ_VBL = $C019

print
    lda $C081
    lda $C081
    pla
    sta getch+1
    pla
    sta getch+2
-	inc getch+1
    bne getch
    inc getch+2
getch	lda $FEED		; FEED gets modified
    beq +
    jsr COUT
    jmp -
+	rts

PRINTTEST
-
    ldy #0
    lda (PCL),y
    cmp #$20
    beq +++
    lda #'-'
    jsr COUT
    lda #' '
    jsr COUT
    ldx #0
    lda (PCL,x)
    jsr $f88e
    ldx #3
    jsr $f8ea
    jsr $f953
    sta PCL
    sty PCH
    lda #$8D
    jsr COUT
    jmp -
+++	rts

;;; Increment .checkdata pointer to the next memory location, and load
;;; it into the accumulator. X and Y are preserved.
NEXTCHECK
    inc .checkdata
    bne CURCHECK
    inc .checkdata+1
CURCHECK
    sty SCRATCH
    ldy #0
    lda (.checkdata),y
    ldy SCRATCH
    ora #0
    rts

!macro print {
    jsr LASTSTRING
    !set TEMP = *
    * = LASTSTRING
    jsr print
}
!macro printed {
    !byte 0
    !set LASTSTRING=*
    * = TEMP
}

START
;;; Reset all soft-switches to known-good state. Burns $300 and $301 in main mem.
RESETALL
    ; The COUT hook isn't set up yet, so the monitor routine will crash unless we set it up
    lda #<COUT1
    sta CSW
    lda #>COUT1
    sta CSW+1
    sta RESET_RAMRD
    sta RESET_RAMWRT
    ;; Save return address in X and A, in case we switch zero-page memory.
    sta RESET_80STORE
    sta RESET_INTCXROM
    sta RESET_ALTZP
    sta RESET_SLOTC3ROM
    sta RESET_INTC8ROM
    sta RESET_80COL
    sta RESET_ALTCHRSET
    sta SET_TEXT
    sta RESET_MIXED
    sta RESET_PAGE2
    sta RESET_HIRES
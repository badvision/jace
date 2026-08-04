; Fixture source for Jace symbol-awareness tests.
; Mirrors the shape of the pf/a2 DHGR probe: a few zero-page state bytes and
; a mainloop whose addresses would shift if any code above it changed size.
!cpu 65c02

frame_count = $1a
harry_x     = $1d
scene_flags = $21

* = $4000

entry
        lda #0
        sta frame_count
        sta scene_flags

mainloop
        jsr draw_scene
after_draw
        inc frame_count
        lda frame_count
        cmp #$ff
        bne mainloop
        rts

draw_scene
        inc harry_x
        rts

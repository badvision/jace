0 REM Joystick test, shows X and Y position of joystick and button state
10 ? pdl(0), pdl(1), peek(49249)>127; " "; peek(49250)>127:goto 10 
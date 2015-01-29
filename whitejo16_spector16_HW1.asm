#Jordan and Kirkland Test pidgin file
#Runs 5! and stores it in R1, R2, and R3.

SET R0 5
SET R1 1
SET R2 1
SET R3 1

:loop
MUL R2 R0 R2
ADD R1 R1 R3
SUB R0 R0 R3
BNE R0 R3 loop

SAVE R2 0
PUSH R2
POP R1
LOAD R3 0
TRAP
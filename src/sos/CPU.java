package sos;

import java.util.*;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer.  This includes a processor chip, RAM and I/O devices.  It is
 * designed to demonstrate a simulated operating system (SOS).
 *
 *@author Jordan White
 *@author Kirkland Spector
 *
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU
{
    
    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    //These constants define the instructions available on the chip
    public static final int SET    = 0;    /* set value of reg */
    public static final int ADD    = 1;    // put reg1 + reg2 into reg3
    public static final int SUB    = 2;    // put reg1 - reg2 into reg3
    public static final int MUL    = 3;    // put reg1 * reg2 into reg3
    public static final int DIV    = 4;    // put reg1 / reg2 into reg3
    public static final int COPY   = 5;    // copy reg1 to reg2
    public static final int BRANCH = 6;    // goto address in reg
    public static final int BNE    = 7;    // branch if not equal
    public static final int BLT    = 8;    // branch if less than
    public static final int POP    = 9;    // load value from stack
    public static final int PUSH   = 10;   // save value to stack
    public static final int LOAD   = 11;   // load value from heap
    public static final int SAVE   = 12;   // save value to heap
    public static final int TRAP   = 15;   // system call
    
    //These constants define the indexes to each register
    public static final int R0   = 0;     // general purpose registers
    public static final int R1   = 1;
    public static final int R2   = 2;
    public static final int R3   = 3;
    public static final int R4   = 4;
    public static final int PC   = 5;     // program counter
    public static final int SP   = 6;     // stack pointer
    public static final int BASE = 7;     // bottom of currently accessible RAM
    public static final int LIM  = 8;     // top of accessible RAM
    public static final int NUMREG = 9;   // number of registers

    //Misc constants
    public static final int NUMGENREG = PC; // the number of general registers
    public static final int INSTRSIZE = 4;  // number of ints in a single instr +
                                            // args.  (Set to a fixed value for simplicity.)

    //======================================================================
    //Member variables
    //----------------------------------------------------------------------
    /**
     * specifies whether the CPU should output details of its work
     **/
    private boolean m_verbose = true;

    /**
     * This array contains all the registers on the "chip".
     **/
    private int m_registers[];

    /**
     * A pointer to the RAM used by this CPU
     *
     * @see RAM
     **/
    private RAM m_RAM = null;
    
    /**
     * Indicates the memory location where the stack begins
     */
    private int bottomOfStack = -1;

    //======================================================================
    //Methods
    //----------------------------------------------------------------------

    /**
     * CPU ctor
     *
     * Intializes all member variables.
     */
    public CPU(RAM ram)
    {
        m_registers = new int[NUMREG];
        for(int i = 0; i < NUMREG; i++)
        {
            m_registers[i] = 0;
        }
        m_RAM = ram;

    }//CPU ctor

    /**
     * getPC
     *
     * @return the value of the program counter
     */
    public int getPC()
    {
        return m_registers[PC];
    }

    /**
     * getSP
     *
     * @return the value of the stack pointer
     */
    public int getSP()
    {
        return m_registers[SP];
    }

    /**
     * getBASE
     *
     * @return the value of the base register
     */
    public int getBASE()
    {
        return m_registers[BASE];
    }

    /**
     * getLIMIT
     *
     * @return the value of the limit register
     */
    public int getLIM()
    {
        return m_registers[LIM];
    }
    
    /**
     * getBottomOfStack
     *
     * @return the address of the first valid stack memory location
     */
    public int getBottomOfStack()
    {
        return bottomOfStack;
    }

    /**
     * getRegisters
     *
     * @return the registers
     */
    public int[] getRegisters()
    {
        return m_registers;
    }

    /**
     * setPC
     *
     * @param v the new value of the program counter
     */
    public void setPC(int v)
    {
        m_registers[PC] = v;
    }

    /**
     * setSP
     *
     * @param v the new value of the stack pointer
     */
    public void setSP(int v)
    {
        m_registers[SP] = v;
    }

    /**
     * setBASE
     *
     * @param v the new value of the base register
     */
    public void setBASE(int v)
    {
        m_registers[BASE] = v;
    }

    /**
     * setLIM
     *
     * @param v the new value of the limit register
     */
    public void setLIM(int v)
    {
        m_registers[LIM] = v;
    }
    
    /**
     * setBottomOfStack
     *
     * @param v the memory address where the stack begins
     */
    public void setBottomOfStack(int v)
    {
        bottomOfStack = v;
    }

    /**
     * regDump
     *
     * Prints the values of the registers.  Useful for debugging.
     */
    private void regDump()
    {
        for(int i = 0; i < NUMGENREG; i++)
        {
            System.out.print("r" + i + "=" + m_registers[i] + " ");
        }//for
        System.out.print("PC=" + m_registers[PC] + " ");
        System.out.print("SP=" + m_registers[SP] + " ");
        System.out.print("BASE=" + m_registers[BASE] + " ");
        System.out.print("LIM=" + m_registers[LIM] + " ");
        System.out.println("");
    }//regDump

    /**
     * printIntr
     *
     * Prints a given instruction in a user readable format.  Useful for
     * debugging.
     *
     * @param instr the current instruction
     */
    public static void printInstr(int[] instr)
    {
            switch(instr[0])
            {
                case SET:
                    System.out.println("SET R" + instr[1] + " = " + instr[2]);
                    break;
                case ADD:
                    System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R" + instr[3]);
                    break;
                case SUB:
                    System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R" + instr[3]);
                    break;
                case MUL:
                    System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R" + instr[3]);
                    break;
                case DIV:
                    System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R" + instr[3]);
                    break;
                case COPY:
                    System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
                    break;
                case BRANCH:
                    System.out.println("BRANCH @" + instr[1]);
                    break;
                case BNE:
                    System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @" + instr[3]);
                    break;
                case BLT:
                    System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @" + instr[3]);
                    break;
                case POP:
                    System.out.println("POP R" + instr[1]);
                    break;
                case PUSH:
                    System.out.println("PUSH R" + instr[1]);
                    break;
                case LOAD:
                    System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
                    break;
                case SAVE:
                    System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
                    break;
                case TRAP:
                    System.out.print("TRAP ");
                    break;
                default:        // should never be reached
                    System.out.println("?? ");
                    break;          
            }//switch

    }//printInstr


    /**
     * run
     * 
     * Main loop for the CPU
     */
    public void run()
    {
    	//Infinite loop for CPU
    	while (true){
    		//retrieve current instruction
    		int [] instruction = m_RAM.fetch(getPC());
    		//if verbose is true print extras
    		if (m_verbose) {
    			regDump();
    			printInstr(instruction);
    		}
    		
    		//takes opcode and performs instruction
    		switch (instruction[0]) {
    			case CPU.SET:	
    				m_registers[instruction[1]] = instruction[2];
    				break;
    			case CPU.ADD:
    				m_registers[instruction[1]] = m_registers[instruction[2]] + m_registers[instruction[3]];
    				break;
    			case CPU.SUB:
    				m_registers[instruction[1]] = m_registers[instruction[2]] - m_registers[instruction[3]];
    				break;
    			case CPU.MUL:
    				m_registers[instruction[1]] = m_registers[instruction[2]] * m_registers[instruction[3]];
    				break;
    			case CPU.DIV:
    				m_registers[instruction[1]] = m_registers[instruction[2]] / m_registers[instruction[3]];
    				break;
    			case CPU.COPY:
    				m_registers[instruction[1]] = m_registers[instruction[2]];
    				break;
    			case CPU.BRANCH:
    				setPC(instruction[1]);
    				break;
    			case CPU.BNE:
    				if (m_registers[instruction[1]] != m_registers[instruction[2]]) setPC(instruction[3]);
    				break;
    			case CPU.BLT:
    				if (m_registers[instruction[1]] < m_registers[instruction[2]]) setPC(instruction[3]);
    				break;
    			case CPU.POP:
    				m_registers[instruction[1]] = pop();
    				break;
    			case CPU.PUSH:
    				push(m_registers[instruction[1]]);
    				break;
    			case CPU.LOAD:
    				
    			    // Attempts to load the value in the memory location given by 
    			    // the second register into the first register
    				if (load(instruction[1], instruction[2])) break;
    				
    				// Escape the loop if the load fails
    				else return;
    				
    			case CPU.SAVE:
    			    
    			    // Attempts to save the value in the first register to the memory
                    // location given by the second register
    				if (save(instruction[1], instruction[2])) break;
    				
    				// Escape the loop if the save fails
    				else return;
    				
    			case CPU.TRAP:
    				if(trap()) return;
    				else {
    				    errorMessage("Trap instruction failed");
    				    return;
    				}
    			default:
    				errorMessage("Illegal opcode");
    				return;
    		}
    		
    		//increment PC to next instruction
    		setPC(getPC() + 4);
    	} 
    }//run
    
    
    /**
     * pop
     * 
     * reads the top of the stack and decrements the SP by one
     * 
     * @return popped the value popped off the stack
     */
    private int pop() {
        
        // Prevent stack pointer from falling into instruction memory
        if(getSP() + 1 > getLIM()) {
            errorMessage("Popping off of empty stack");
            System.exit(1);
        }
        
        // Return the last element added to the stack and decrement the
        // stack pointer
    	int popped = m_RAM.read(getSP());
        setSP(getSP() + 1);
    	return popped;
    }//pop
    
    
    /**
     * push
     * 
     * increment the SP and write the value of the register
     * to the stack
     * 
     * @param reg the value to push to the stack
     */
    private void push(int reg) {
        
        // Increment the SP and ensure that the SP is pointing at
        // memory owned by the stack
        setSP(getSP() - 1);
        if(getSP() < getBASE()) {
            errorMessage("STACK OVERFLOW");
            System.exit(1);
        }
        
        // Write the pushed value to memory
    	m_RAM.write(getSP(), reg);
    }//push
    
    

    /**
     * load
     * 
     * loads the value at the address in the address register into the target
     * register
     * 
     * @param targetReg register receiving the data from memory
     * @param addrReg register containing the memory location of the data
     * @return true if successful, false if an error occurs
     */
    private boolean load(int targetReg, int addrReg) {
      //checks if trying to access out of base or limit
        if (checkAddr(m_registers[addrReg])) {
            m_registers[targetReg] = m_RAM.read(m_registers[addrReg] + getBASE());
            return true;
        } else {
            return false;
        }
        
    }//load
    
    /**
     * save
     * 
     * saves the value at target register to the memory location in the address register
     * 
     * @param targetReg register receiving the data from memory
     * @param addrReg register containing the memory location of the data
     * @return true if successful, false if an error occurs
     */
    private boolean save(int targetReg, int addrReg) {
      //checks if trying to access out of base or limit
        if (checkAddr(m_registers[addrReg])) {
            m_RAM.write(m_registers[addrReg] + getBASE(), m_registers[targetReg]);
            return true;
        }  else {
            return false;
        }
        
    }//save
    
    
    /**
     * trap
     * 
     * Run the trap instruction
     * 
     * @return For now, only return true.
     */
    private boolean trap() {
        
        return true;
    }//trap
    
    /**
     * errorMessage
     * 
     * Print a supplied error message to the console with an "Error: " prefix
     * 
     * @param err String containing an informative message describing the error
     */
    public void errorMessage(String err) {
    	System.out.println("ERROR: " + err);
    }//errorMessage
    
    
    /**
     * checkAddr
     * 
     * checks if the given address is within the base and limit
     * 
     * @param addr the address in ram of interest
     * @return true if address is allowed, false if not
     */
    private boolean checkAddr(int addr) {
    	if (addr < 0) {
    		errorMessage("Specified address is lower than base");
    		return false;
    	}
    	if (addr > getLIM() - getBASE()) {
    		errorMessage("Specified address is greater than limit");
    		return false;
    	}
    	return true;
    }//checkAddr
    
};//class CPU

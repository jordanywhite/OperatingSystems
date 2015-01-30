
package sos;


import java.util.*;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 * 
 *@author Jordan White
 *@author Kirkland Spector
 */

public class SOS
{
    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful status
     * messages
     **/
    public static final boolean m_verbose = false;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;

    /*
     * ======================================================================
     * Constructors & Debugging
     * ----------------------------------------------------------------------
     */

    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r)
    {
        // Init member list
        m_CPU = c;
        m_RAM = r;
    }// SOS ctor

    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s)
    {
        if (m_verbose)
        {
            System.out.print(s);
        }
    }

    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s)
    {
        if (m_verbose)
        {
            System.out.println(s);
        }
    }

    /*
     * ======================================================================
     * Memory Block Management Methods
     * ----------------------------------------------------------------------
     */

    // None yet!

    /*
     * ======================================================================
     * Device Management Methods
     * ----------------------------------------------------------------------
     */

    // None yet!

    /*
     * ======================================================================
     * Process Management Methods
     * ----------------------------------------------------------------------
     */

    // None yet!

    /*
     * ======================================================================
     * Program Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * createProcess starts a process
     * 
     * @param prog
     * @param allocSize
     */
    public void createProcess(Program prog, int allocSize)
    {
        // copy the program into an array of ints
        int[] program = prog.export();
        int progSize = program.length - 1; //minus one because of 0th position

        // Split the RAM into 3 parts: the program, the stack, and the heap
        int[][] split_mem = assignMemory(progSize, allocSize, 0);

        // When an error occurs while allocating memory, quit
        if (split_mem == null)
        {
            m_CPU.errorMessage("Memory allocation failed, terminating");
            System.exit(1);
        }

        // write program to ram
        for (int i = 0; i < progSize; i++) {
            m_RAM.write(split_mem[0][0] + i, program[i]);
        }

        // set bases and limits
        m_CPU.setBASE(split_mem[2][0]);
        m_CPU.setLIM(split_mem[2][1]);
        
        // Define the top of stack as the first entry after the program
        m_CPU.setSP(split_mem[1][0]);
        
        // Point the program counter to the first instruction
        m_CPU.setPC(split_mem[0][0]);

    }// createProcess

    /**
     * assignMemory
     * 
     * Calculate the memory bounds between the program, stack, and
     * heap, where the program gets as much space as it needs while the stack
     * and heap each get half of the remaining space.
     * 
     * @param progSize the memory required for the program
     * @param allocSize the total amount of memory given to the program
     * @return mem_bounds a two dimensional array with the distributed memory
     */
    private int[][] assignMemory(int progSize, int allocSize, int memOffset)
    {
        // Divides the memory between the program, stack and heap
        int[][] mem_bounds = new int[3][2];

        // Memory not taken up by the program
        int memory = allocSize - progSize;

        // Program memory
        mem_bounds[0][0] = 4 + memOffset; //starting point of program
        mem_bounds[0][1] = progSize + memOffset;

        // Stack memory
        mem_bounds[1][0] = allocSize + memOffset; //starting point of stack
        mem_bounds[1][1] = progSize + 1 + memOffset; //ending point of stack

        // Base/Limit
        mem_bounds[2][0] = memOffset; // Base
        mem_bounds[2][1] = allocSize + memOffset; // Limit

        return mem_bounds;
    
    }//assignMemory

    /*
     * ======================================================================
     * Interrupt Handlers
     * ----------------------------------------------------------------------
     */

    // None yet!

    /*
     * ======================================================================
     * System Calls
     * ----------------------------------------------------------------------
     */

    // None yet!

};// class SOS

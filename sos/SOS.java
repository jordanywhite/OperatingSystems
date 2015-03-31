
package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language. File
 * History: HW 1 Stephen Robinson and Camden McKone, HW 2 Stephen Robinson and
 * Nathan Brown HW 3 Stephen Robinson and Connor Haas HW 4 Stephen Robinson and
 * Jordan White HW 5 Jordan White and Micah Alconcel HW 6 Jordan White
 * 
 * @author Stephen Robinson
 * @author Camden McKone
 * @author Nathan Brown
 * @author Connor Haas
 * @author Jordan White
 * @author Micah Alconcel
 */

public class SOS implements CPU.TrapHandler {

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful status
     * messages
     **/
    public static final boolean m_verbose = false;

    /**
     * The ProcessControlBlock of the current process
     **/
    private ProcessControlBlock m_currProcess = null;

    /**
     * List of all processes currently loaded into RAM and in one of the major
     * states
     **/
    Vector<ProcessControlBlock> m_processes = null;

    /**
     * A Vector of DeviceInfo objects
     **/
    private Vector<DeviceInfo> m_devices = null;

    /**
     * A Vector of all the Program objects that are available to the operating
     * system.
     **/
    Vector<Program> m_programs = null;

    /**
     * The position where the next program will be loaded.
     **/
    private int m_nextLoadPos = 0;

    /**
     * The ID which will be assigned to the next process that is loaded
     **/
    private int m_nextProcessID = 1001;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;
    
    /**
     * Starve counters for statistical analysis
     */
    private double totalAvgStarveTime = 0;
    private double maxStarvTime = 0;

    /**
     * Priority queues for every process, low medium or high
     */
    private Vector<ProcessControlBlock> highPrio;
    private Vector<ProcessControlBlock> normPrio;
    private Vector<ProcessControlBlock> lowPrio;

    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    // These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT = 0; /* exit the current program */
    public static final int SYSCALL_OUTPUT = 1; /* outputs a number */
    public static final int SYSCALL_GETPID = 2; /* get current process id */
    public static final int SYSCALL_OPEN = 3; /* access a device */
    public static final int SYSCALL_CLOSE = 4; /* release a device */
    public static final int SYSCALL_READ = 5; /* get input from device */
    public static final int SYSCALL_WRITE = 6; /* send output to device */
    public static final int SYSCALL_EXEC = 7; /* spawn a new process */
    public static final int SYSCALL_YIELD = 8; /*
                                                * yield the CPU to another
                                                * process
                                                */
    public static final int SYSCALL_COREDUMP = 9; /*
                                                   * print process state and
                                                   * exit
                                                   */

    // Return codes for syscalls
    public static final int SYSCALL_RET_SUCCESS = 0; /* no problem */
    public static final int SYSCALL_RET_DNE = 1; /* device doesn't exist */
    public static final int SYSCALL_RET_NOT_SHARE = 2; /*
                                                        * device is not sharable
                                                        */
    public static final int SYSCALL_RET_ALREADY_OPEN = 3; /*
                                                           * device is already
                                                           * open
                                                           */
    public static final int SYSCALL_RET_NOT_OPEN = 4; /* device is not yet open */
    public static final int SYSCALL_RET_RO = 5; /* device is read only */
    public static final int SYSCALL_RET_WO = 6; /* device is write only */

    /** This process is used as the idle process' id */
    public static final int IDLE_PROC_ID = 999;

    public static final int THE_QUANTUM = 10; // Max time a process can run

    public static final int MAX_ALLOWED_STARVE = 400; // Max time a process will
                                                      // be left to starve
                                                      // before
                                                      // being increased prio

    /*
     * ======================================================================
     * Constructors & Debugging
     * ----------------------------------------------------------------------
     */

    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r) {
        // Init member list
        m_CPU = c;
        m_RAM = r;
        
        m_CPU.registerTrapHandler(this);

        initVars();
    }// SOS ctor

    /**
     * initVars initialize all lists of porgrams, devices, processes, and
     * process priority queues
     */
    private void initVars() {
        m_devices = new Vector<DeviceInfo>();
        m_programs = new Vector<Program>();
        m_processes = new Vector<ProcessControlBlock>();
        
        lowPrio = new Vector<ProcessControlBlock>();
        normPrio = new Vector<ProcessControlBlock>();
        highPrio = new Vector<ProcessControlBlock>();
    }

    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s) {
        if (m_verbose) {
            System.out.print(s);
        }
    }

    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s) {
        if (m_verbose) {
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
     * -------------------------------------d---------------------------------
     */

    /**
     * registerDevice adds a new device to the list of devices managed by the OS
     * 
     * @param dev the device driver
     * @param id the id to assign to this device
     */
    public void registerDevice(Device dev, int id) {
        m_devices.add(new DeviceInfo(dev, id));
    } // registerDevice

    /**
     * getDeviceInfo gets a device info by id.
     * 
     * @param id the id of the device
     * @return the device info instance if it exists, else null
     */
    private DeviceInfo getDeviceInfo(int id) {
        Iterator<DeviceInfo> i = m_devices.iterator();

        while (i.hasNext()) {
            DeviceInfo devInfo = i.next();
            if (devInfo.getId() == id) {
                return devInfo;
            }
        }

        return null;
    }

    /*
     * ======================================================================
     * Process Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * createIdleProcess creates a one instruction process that immediately
     * exits. This is used to buy time until device I/O completes and unblocks a
     * legitimate process.
     */
    public void createIdleProcess() {
        int progArr[] = {
                0, 0, 0, 0, // SET r0=0
                0, 0, 0, 0, // SET r0=0 (repeated instruction to account for
                            // vagaries in student implementation of the CPU
                            // class)
                10, 0, 0, 0, // PUSH r0
                15, 0, 0, 0
        }; // TRAP

        // Initialize the starting position for this program
        int baseAddr = m_nextLoadPos;

        // Load the program into RAM
        for (int i = 0; i < progArr.length; i++) {
            m_RAM.write(baseAddr + i, progArr[i]);
        }

        // Save the register info from the current process (if there is one)
        if (m_currProcess != null) {
            m_currProcess.save(m_CPU);
        }

        // Set the appropriate registers
        m_CPU.setPC(0);
        m_CPU.setSP(progArr.length + 20);
        m_CPU.setBASE(baseAddr);
        m_CPU.setLIM(baseAddr + progArr.length + 20);

        // Save the relevant info as a new entry in m_processes
        m_currProcess = new ProcessControlBlock(IDLE_PROC_ID);
        m_processes.add(m_currProcess);

        // The idle process is always low priority
        lowPrio.addElement(m_currProcess);

    }// createIdleProcess

    /**
     * printProcessTable **DEBUGGING** prints all the processes in the process
     * table
     */
    private void printProcessTable() {
        debugPrintln("");
        debugPrintln("Process Table (" + m_processes.size() + " processes)");
        debugPrintln("======================================================================");
        for (ProcessControlBlock pi : m_processes) {
            debugPrintln("    " + pi);
        }// for
        debugPrintln("----------------------------------------------------------------------");

    }// printProcessTable

    /**
     * removeCurrentProcess Removes the currently running process from the list
     * of all processes priorities. Schedules a new process.
     */
    public void removeCurrentProcess() {
        printProcessTable();
        if (m_currProcess.getProcessId() != IDLE_PROC_ID) {
            if (m_currProcess.avgStarve >= 0) {
                totalAvgStarveTime += m_currProcess.avgStarve;
            }
            totalAvgStarveTime /= 2;

            if (m_currProcess.maxStarve >= 0) {
                maxStarvTime += m_currProcess.maxStarve;
            }
            maxStarvTime /= 2;

            System.out.println("Avg max starve time: " + maxStarvTime + "\n"
                    + "Avg avg starve time: " + totalAvgStarveTime);
        }

        m_processes.remove(m_currProcess);
        lowPrio.remove(m_currProcess);
        normPrio.remove(m_currProcess);
        highPrio.remove(m_currProcess);
        m_currProcess = null;
        scheduleNewProcess();
    }// removeCurrentProcess


    /**
     * selectBlockedProcess select a process to unblock that might be waiting to
     * perform a given action on a given device. This is a helper method for
     * system calls and interrupts that deal with devices.
     * 
     * @param dev the Device that the process must be waiting for
     * @param op the operation that the process wants to perform on the device.
     *            Use the SYSCALL constants for this value.
     * @param addr the address the process is reading from. If the operation is
     *            a Write or Open then this value can be anything
     * @return the process to unblock -OR- null if none match the given criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr) {
        DeviceInfo devInfo = getDeviceInfo(dev.getId());
        ProcessControlBlock selected = null;

        for (ProcessControlBlock pi : devInfo.getPCBs()) {
            if (pi.isBlockedForDevice(dev, op, addr)) {
                selected = pi;
                break;
            }
        }// for

        return selected;

    }// selectBlockedProcess

    /**
     * getRandomProcess selects a non-Blocked process at random from the
     * ProcessTable.
     * 
     * @return a reference to the ProcessControlBlock struct of the selected
     *         process -OR- null if no non-blocked process exists
     */
    ProcessControlBlock getRandomProcess() {
        // Calculate a random offset into the m_processes list
        int offset = ((int) (Math.random() * 2147483647)) % m_processes.size();

        // Iterate until a non-blocked process is found
        ProcessControlBlock newProc = null;
        for (int i = 0; i < m_processes.size(); i++) {
            newProc = m_processes.get((i + offset) % m_processes.size());
            if (!newProc.isBlocked()) {
                return newProc;
            }
        }// for

        return null; // no processes are Ready
    }// getRandomProcess

    /**
     * scheduleNewProcess Selects a new non-blocked process to run and replaces
     * the old running process.
     */
    /**
     * scheduleNewProcess Selects a new non-blocked process to run and replaces
     * the old running process.
     */
    public void scheduleNewProcess()
    {
        // If we have nothing left to run, we are done
        if (m_processes.size() == 0) {
            System.exit(0);
        }

        // Choose a new process based on priority (null if all blocked)
        ProcessControlBlock proc = chooseNewProcess();
        //ProcessControlBlock proc = getRandomProcess();
        
        // Every process is blocked
        if (proc == null) {

            // Schedule an idle process.
            createIdleProcess();
            return;
        }

        // We are just scheduling ourself again don't bother context switching!
        if (proc == m_currProcess) {
            return;
        }

        // Save the CPU registers
        if (m_currProcess != null) {
            m_currProcess.save(m_CPU);
        }

        // Set this process as the new current process
        m_currProcess = proc;
        m_currProcess.restore(m_CPU);
    }// scheduleNewProcess

    /**
     * chooseNewProcess
     * 
     * @return the next process that should be run
     */
    public ProcessControlBlock chooseNewProcess() {

        ProcessControlBlock proc = null;

        if (m_currProcess != null) {
            
            // If we can, we will want to keep the current process running
            if (!m_currProcess.isBlocked()) {

                // Check that the process has not used up its quantum
                m_currProcess.quantum++;
                if (THE_QUANTUM < m_currProcess.quantum) {

                    // Using up the quantum results running less often
                    m_currProcess.quantum = 0;
                    moveToLowPrio(m_currProcess);
                } else {
                    // keep this one going
                    return m_currProcess;
                }
            }
        }

        // Even if the process used up its quantum, we still want to keep the
        // current process without a context switch if all others are blocked
        if (m_currProcess != null && !m_currProcess.isBlocked()) {
            proc = m_currProcess;
        }
        

        // There should always be at least one process in normal prio
        normalizePrio();

        /**
         * Take the first process from each queue and return the highest 
         * priority process found.
         */

        for (int i = 0; i < highPrio.size(); i++) {
            if (!highPrio.get(i).isBlocked()) {
                return highPrio.get(i);
            }
        }

        for (int i = 0; i < normPrio.size(); i++) {
            if (!normPrio.get(i).isBlocked()) {
                return normPrio.get(i);
            }
        }
        for (int i = 0; i < lowPrio.size(); i++) {
            if (!lowPrio.get(i).isBlocked()) {
                return lowPrio.get(i);
            }
        }

        return proc;
    }// chooseNewProcess

    /**
     * normalizePrio attempt to better distribute process over priorities
     */
    private void normalizePrio() {
        if (normPrio.isEmpty()) {
            if (!lowPrio.isEmpty()) {
                moveToNormPrio(lowPrio.get(0));
            } else if (!highPrio.isEmpty()) {
                moveToNormPrio(highPrio.get(0));
            }
        } else if (highPrio.isEmpty()) {
            moveToHighPrio(normPrio.get(0));

        }
    }

    /**
     * upPrio ups the priority of a process
     * 
     * @param proc process to have its priority upped
     */
    private void upPrio(ProcessControlBlock proc) {
        if (lowPrio.contains(proc))
            moveToNormPrio(proc);
        else if (normPrio.contains(proc))
            moveToHighPrio(proc);
    }

    /**
     * downPrio downs the priority of a process
     * 
     * @param proc process to have its priority downed
     */
    private void downPrio(ProcessControlBlock proc) {
        if (highPrio.contains(proc))
            moveToNormPrio(proc);
        else if (normPrio.contains(proc))
            moveToLowPrio(proc);
    }

    /**
     * moveToHighPrio checks if this process is currently in high prio. If it is
     * not, add it. Else do nothing.
     * 
     * @param proc
     */
    private void moveToHighPrio(ProcessControlBlock proc) {
        if (!highPrio.contains(proc)) {
            highPrio.addElement(proc);
            lowPrio.remove(proc);
            normPrio.remove(proc);
        }
    }

    /**
     * moveToNormPrio checks if this process is currently in norm prio. If it is
     * not, add it. Else do nothing.
     * 
     * @param proc
     */
    private void moveToNormPrio(ProcessControlBlock proc) {
        if (!normPrio.contains(proc)) {
            normPrio.addElement(proc);
            lowPrio.remove(proc);
            highPrio.remove(proc);
        }
    }

    /**
     * moveToLowPrio checks if this process is currently in high low. If it is
     * not, add it. Else do nothing.
     * 
     * @param proc
     */
    private void moveToLowPrio(ProcessControlBlock proc) {
        if (!lowPrio.contains(proc)) {
            lowPrio.addElement(proc);
            normPrio.remove(proc);
            highPrio.remove(proc);
        }
    }

    /**
     * addProgram registers a new program with the simulated OS that can be used
     * when the current process makes an Exec system call. (Normally the program
     * is specified by the process via a filename but this is a simulation so
     * the calling process doesn't actually care what program gets loaded.)
     * 
     * @param prog the program to add
     */
    public void addProgram(Program prog) {
        m_programs.add(prog);
    }// addProgram

    /*
     * ======================================================================
     * Program Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * createProcess Creates one process for the CPU.
     * 
     * @param prog The program class to be loaded into memory.
     * @param allocSize The amount of memory to allocate for the program.
     */
    public void createProcess(Program prog, int allocSize) {

        int base = m_nextLoadPos;
        int lim = base + allocSize;

        if (lim >= m_RAM.getSize()) {
            debugPrintln("Error: Out of memory for new process!");
            System.exit(0);
        }

        m_nextLoadPos = lim + 1;

        if (m_currProcess != null) {
            debugPrintln("Moving process " + m_currProcess.getProcessId()
                    + " from RUNNING to READY.");
            m_currProcess.save(m_CPU);
        }

        m_CPU.setBASE(base);
        m_CPU.setLIM(lim);
        m_CPU.setPC(0);         // We are going to use a logical (not physical) PC
        m_CPU.setSP(allocSize); // Stack starts at the bottom and grows up.
                                // The Stack is also logical

        m_currProcess = new ProcessControlBlock(m_nextProcessID++);
        m_processes.add(m_currProcess);
        m_currProcess.save(m_CPU);

        // Write the program code to memory
        int[] progArray = prog.export();

        for (int progAddr = 0; progAddr < progArray.length; ++progAddr) {
            m_RAM.write(base + progAddr, progArray[progAddr]);
        }

        // Everything initially gets medium prio unless IO or a syscall is
        // used later on.
        normPrio.addElement(m_currProcess);

    }// createProcess

    /*
     * ======================================================================
     * Interrupt Handlers
     * ----------------------------------------------------------------------
     */

    /**
     * interruptIllegalMemoryAccess Handles Illegal Memory Access interrupts.
     * 
     * @param addr The address which was attempted to be accessed
     */
    public void interruptIllegalMemoryAccess(int addr) {
        System.out.println("Error: Illegal Memory Access at addr " + addr);
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * interruptDivideByZero Handles Divide by Zero interrupts.
     */
    public void interruptDivideByZero() {
        System.out.println("Error: Divide by Zero");
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * interruptIOReadComplete Locates a process that was waiting to read a
     * device and moves it to the ready state. Pushes the data read onto the
     * process's stack, updates process stack pointer, saves registers, and
     * restores the current process
     * 
     * @param devID device to be written to by the process
     * @param addr address space to be written
     * @param data data to be pushed to the stack that was read from the device
     */
    @Override
    public void interruptIOReadComplete(int devID, int addr, int data) {

        Device dev = getDeviceInfo(devID).getDevice();
        ProcessControlBlock waitingProcess = selectBlockedProcess(dev,
                SYSCALL_READ, addr);

        // Make sure this IO bound process is high priority
        moveToHighPrio(waitingProcess);

        // The waiting process is no longer blocked
        waitingProcess.unblock();

        // Save the current process and focus on the waiting process
        m_currProcess.save(m_CPU);
        waitingProcess.restore(m_CPU);

        // Push appropriate data and success code for the waiting process's
        // stack
        m_CPU.pushStack(data);
        m_CPU.pushStack(SYSCALL_RET_SUCCESS);

        // Refocus to the current process
        waitingProcess.save(m_CPU);
        m_currProcess.restore(m_CPU);

    }

    /**
     * interruptIOWriteComplete Locates a process that was waiting to write to a
     * device and moves it to the ready state.
     * 
     * @param devID device to be written to by the process
     * @param addr address space to be written
     */

    @Override
    public void interruptIOWriteComplete(int devID, int addr) {

        Device dev = getDeviceInfo(devID).getDevice();
        ProcessControlBlock waitingProcess = selectBlockedProcess(dev,
                SYSCALL_WRITE, addr);

        // Make sure this IO bound process is high priority
        moveToHighPrio(waitingProcess);

        // The waiting process is no longer blocked
        waitingProcess.unblock();

        // Save the current process and focus on the waiting process
        m_currProcess.save(m_CPU);
        waitingProcess.restore(m_CPU);

        // Push the success code onto the stack.
        m_CPU.pushStack(SYSCALL_RET_SUCCESS);

        // Refocus to the current process
        waitingProcess.save(m_CPU);
        m_currProcess.restore(m_CPU);

    }

    /**
     * interruptIllegalInstruction Handles Illegal Instruction interrupts.
     * 
     * @param instr The instruction which caused the interrupt
     */
    public void interruptIllegalInstruction(int[] instr) {
        System.out.println("Error: Illegal Instruction:");
        System.out.println(instr[0] + ", " + instr[1] + ", " + instr[2] + ", "
                + instr[3]);
        System.out.println("NOW YOU DIE!!!");
        System.exit(0);
    }

    /**
     * interruptClock Called whenever a process uses up its quantum
     */
    public void interruptClock() {

        scheduleNewProcess();

    }

    /*
     * ======================================================================
     * System Calls
     * ----------------------------------------------------------------------
     */

    /**
     * syscallExit Exits from the current process.
     */
    private void syscallExit() {
        debugPrintln("Removing Process " + m_currProcess.getProcessId()
                + " from RAM.");
        removeCurrentProcess();
    }

    /**
     * syscallOutput Outputs the top number from the stack.
     */
    private void syscallOutput() {
        System.out.println("OUTPUT: " + m_CPU.popStack());
    }

    /**
     * syscallGetPID Pushes the PID to the stack.
     */
    private void syscallGetPID() {
        m_CPU.pushStack(m_currProcess.getProcessId());
    }

    /**
     * syscallOpen Open a device.
     */
    private void syscallOpen() {
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (devInfo.containsProcess(m_currProcess)) {
            m_CPU.pushStack(SYSCALL_RET_ALREADY_OPEN);
            return;
        }
        if (!devInfo.device.isSharable() && !devInfo.unused()) {

            // addr = -1 because this is not a read
            m_currProcess.block(m_CPU, devInfo.getDevice(), SYSCALL_OPEN, -1);
            devInfo.addProcess(m_currProcess);
            m_CPU.pushStack(SYSCALL_RET_SUCCESS);
            scheduleNewProcess();
            return;
        }

        // Associate the process with this device.
        devInfo.addProcess(m_currProcess);

        m_CPU.pushStack(SYSCALL_RET_SUCCESS);
    }

    /**
     * syscallClose Close a device.
     */
    private void syscallClose() {
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }
        if (!devInfo.containsProcess(m_currProcess)) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }

        // De-associate the process with this device.
        devInfo.removeProcess(m_currProcess);

        m_CPU.pushStack(SYSCALL_RET_SUCCESS);

        // Unblock next proc which wants to open this device
        ProcessControlBlock proc = selectBlockedProcess(devInfo.getDevice(),
                SYSCALL_OPEN, -1);

        // Unblock the process if it exists
        if (proc != null) {
            proc.unblock();
        }
    }

    /**
     * syscallRead Read from an open device.
     */
    private void syscallRead() {

        int addr = m_CPU.popStack();
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }

        // Check that the process has the process opened and the device is
        // writable
        if (!devInfo.containsProcess(m_currProcess)) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }
        if (!devInfo.device.isReadable()) {
            m_CPU.pushStack(SYSCALL_RET_WO);
            return;
        }

        // Device is available to read from
        if (devInfo.getDevice().isAvailable()) {

            // Read from device
            devInfo.getDevice().read(addr);

            // Interupt for read
            m_currProcess.block(m_CPU, devInfo.getDevice(), SYSCALL_READ, addr);
            debugPrintln(m_currProcess.toString());

        } else {

            // Re-run the trap call at a later time
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

            // Syscall could not complete, so put everything back on
            // the stack and resume at a later point
            m_CPU.pushStack(devNum);
            m_CPU.pushStack(addr);
            m_CPU.pushStack(SYSCALL_READ);

        }

        // Run next process while this process is waiting
        scheduleNewProcess();

    }

    /**
     * syscallWrite Write to an open device.
     */
    private void syscallWrite() {
        int value = m_CPU.popStack();
        int addr = m_CPU.popStack();
        int devNum = m_CPU.popStack();
        DeviceInfo devInfo = getDeviceInfo(devNum);

        // Device not found
        if (devInfo == null) {
            m_CPU.pushStack(SYSCALL_RET_DNE);
            return;
        }

        // Check that the process has opened the device and the device is
        // writable
        if (!devInfo.containsProcess(m_currProcess)) {
            m_CPU.pushStack(SYSCALL_RET_NOT_OPEN);
            return;
        }
        if (!devInfo.device.isWriteable()) {
            m_CPU.pushStack(SYSCALL_RET_RO);
            return;
        }

        // Device is available to write to
        if (devInfo.getDevice().isAvailable()) {

            // Write to device
            devInfo.getDevice().write(addr, value);

            // Interrupt for write
            m_currProcess.block(m_CPU, devInfo.getDevice(),
                    SYSCALL_WRITE, addr);
            debugPrintln(m_currProcess.toString());

        } else {

            // Re-run the trap call at a later time
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

            // Syscall could not complete, so put everything back on
            // the stack and resume at a later point
            m_CPU.pushStack(devInfo.getId());
            m_CPU.pushStack(addr);
            m_CPU.pushStack(value);
            m_CPU.pushStack(SYSCALL_WRITE);

        }

        // Run next process while this process is waiting
        scheduleNewProcess();

    }

    /**
     * syscallCoreDump Prints the registers and top three stack items, then
     * exits the process.
     */
    private void syscallCoreDump() {

        System.out.println("\n\nCORE DUMP!");

        m_CPU.regDump();

        System.out.println("Top three stack items:");
        for (int i = 0; i < 3; ++i) {
            if (m_CPU.validMemory(m_CPU.getSP() + 1 + m_CPU.getBASE())) {
                System.out.println(m_CPU.popStack());
            } else {
                System.out.println(" -- NULL -- ");
            }
        }
        syscallExit();
    }

    /**
     * syscallExec creates a new process. The program used to create that
     * process is chosen semi-randomly from all the programs that have been
     * registered with the OS via {@link #addProgram}. Limits are put into place
     * to ensure that each process is run an equal number of times. If no
     * programs have been registered then the simulation is aborted with a fatal
     * error.
     */
    private void syscallExec() {
        // If there is nothing to run, abort. This should never happen.
        if (m_programs.size() == 0) {
            System.err.println("ERROR!  syscallExec has no programs to run.");
            System.exit(-1);
        }

        // find out which program has been called the least and record how many
        // times it has been called
        int leastCallCount = m_programs.get(0).callCount;
        for (Program prog : m_programs) {
            if (prog.callCount < leastCallCount) {
                leastCallCount = prog.callCount;
            }
        }

        // Create a vector of all programs that have been called the least
        // number
        // of times
        Vector<Program> cands = new Vector<Program>();
        for (Program prog : m_programs) {
            cands.add(prog);
        }

        // Select a random program from the candidates list
        Random rand = new Random();
        int pn = rand.nextInt(m_programs.size());
        Program prog = cands.get(pn);

        // Determine the address space size using the default if available.
        // Otherwise, use a multiple of the program size.
        int allocSize = prog.getDefaultAllocSize();
        if (allocSize <= 0) {
            allocSize = prog.getSize() * 2;
        }

        // Load the program into RAM
        createProcess(prog, allocSize);

        // Adjust the PC since it's about to be incremented by the CPU
        m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);

    }// syscallExec

    /**
     * syscallYield Allow process to voluntarily move from Running to Ready.
     */
    private void syscallYield() {
        scheduleNewProcess();
    }// syscallYield

    /**
     * systemCall Occurs when TRAP is encountered in child process.
     */
    public void systemCall() {
        int syscallNum = m_CPU.popStack();

        switch (syscallNum) {
            case SYSCALL_EXIT:
                syscallExit();
                break;
            case SYSCALL_OUTPUT:
                downPrio(m_currProcess);
                syscallOutput();
                break;
            case SYSCALL_GETPID:
                downPrio(m_currProcess);
                syscallGetPID();
                break;
            case SYSCALL_OPEN:
                syscallOpen();
                break;
            case SYSCALL_CLOSE:
                syscallClose();
                break;
            case SYSCALL_READ:
                moveToHighPrio(m_currProcess);
                syscallRead();
                break;
            case SYSCALL_WRITE:
                moveToHighPrio(m_currProcess);
                syscallWrite();
                break;
            case SYSCALL_EXEC:
                syscallExec();
                break;
            case SYSCALL_YIELD:
                upPrio(m_currProcess);
                syscallYield();
                break;
            case SYSCALL_COREDUMP:
                syscallCoreDump();
                break;
        }
    }

    // ======================================================================
    // Inner Classes
    // ----------------------------------------------------------------------

    /**
     * class ProcessControlBlock This class contains information about a
     * currently active process.
     */
    private class ProcessControlBlock {
        /**
         * a unique id for this process
         */
        private int processId = 0;

        /**
         * These are the process' current registers. If the process is in the
         * "running" state then these are out of date
         */
        private int[] registers = null;

        /**
         * If this process is blocked a reference to the Device is stored here
         */
        private Device blockedForDevice = null;

        /**
         * If this process is blocked a reference to the type of I/O operation
         * is stored here (use the SYSCALL constants defined in SOS)
         */
        private int blockedForOperation = -1;

        /**
         * If this process is blocked reading from a device, the requested
         * address is stored here.
         */
        private int blockedForAddr = -1;

        /**
         * the time it takes to load and save registers, specified as a number
         * of CPU ticks
         */
        private static final int SAVE_LOAD_TIME = 30;

        /**
         * Used to store the system time when a process is moved to the Ready
         * state.
         */
        private int lastReadyTime = -1;

        /**
         * Used to store the number of times this process has been in the ready
         * state
         */
        private int numReady = 0;

        /**
         * Used to store the maximum starve time experienced by this process
         */
        private int maxStarve = -1;

        /**
         * Used to store the average starve time for this process
         */
        private double avgStarve = 0;

        /**
         * Number of ticks this process has run since being blocked
         */
        private int quantum = 0;

        /**
         * constructor
         * 
         * @param pid a process id for the process. The caller is responsible
         *            for making sure it is unique.
         */
        public ProcessControlBlock(int pid) {
            this.processId = pid;
        }

        /**
         * @return the last time this process was put in the Ready state
         */
        public long getLastReadyTime()
        {
            return lastReadyTime;
        }

        /**
         * @return the current process' id
         */
        public int getProcessId() {
            return this.processId;
        }

        /**
         * save saves the current CPU registers into this.registers
         *
         * @param cpu the CPU object to save the values from
         */
        public void save(CPU cpu)
        {
            // A context switch is expensive. We simluate that here by
            // adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            // Save the registers
            int[] regs = cpu.getRegisters();
            this.registers = new int[CPU.NUMREG];
            for (int i = 0; i < CPU.NUMREG; i++)
            {
                this.registers[i] = regs[i];
            }

            // Assuming this method is being called because the process is
            // moving
            // out of the Running state, record the current system time for
            // calculating starve times for this process. If this method is
            // being called for a Block, we'll adjust lastReadyTime in the
            // unblock method.
            numReady++;
            lastReadyTime = m_CPU.getTicks();

        }// save

        /**
         * restore restores the saved values in this.registers to the current
         * CPU's registers
         *
         * @param cpu the CPU object to restore the values to
         */
        public void restore(CPU cpu)
        {
            // A context switch is expensive. We simluate that here by
            // adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);

            // Restore the register values
            int[] regs = cpu.getRegisters();
            for (int i = 0; i < CPU.NUMREG; i++)
            {
                regs[i] = this.registers[i];
            }

            // Record the starve time statistics
            int starveTime = m_CPU.getTicks() - lastReadyTime;
            if (starveTime > maxStarve)
            {
                maxStarve = starveTime;
            }
            double d_numReady = (double) numReady;
            avgStarve = avgStarve * (d_numReady - 1.0) / d_numReady;
            avgStarve = avgStarve + (starveTime * (1.0 / d_numReady));
        }// restore

        /**
         * block blocks the current process to wait for I/O. The caller is
         * responsible for calling {@link CPU#scheduleNewProcess} after calling
         * this method.
         * 
         * @param cpu the CPU that the process is running on
         * @param dev the Device that the process must wait for
         * @param op the operation that the process is performing on the device.
         *            Use the SYSCALL constants for this value.
         * @param addr the address the process is reading from (for
         *            SYSCALL_READ)
         */
        public void block(CPU cpu, Device dev, int op, int addr) {
            blockedForDevice = dev;
            blockedForOperation = op;
            blockedForAddr = addr;

        }// block

        /**
         * unblock moves this process from the Blocked (waiting) state to the
         * Ready state.
         */
        public void unblock()
        {
            // Reset the info about the block
            blockedForDevice = null;
            blockedForOperation = -1;
            blockedForAddr = -1;

            // Assuming this method is being called because the process is
            // moving
            // from the Blocked state to the Ready state, record the current
            // system time for calculating starve times for this process.
            lastReadyTime = m_CPU.getTicks();

        }// unblock

        /**
         * isBlocked
         * 
         * @return true if the process is blocked
         */
        public boolean isBlocked() {
            return (blockedForDevice != null);
        }// isBlocked

        /**
         * isBlockedForDevice Checks to see if the process is blocked for the
         * given device, operation and address. If the operation is not an open,
         * the given address is ignored.
         * 
         * @param dev check to see if the process is waiting for this device
         * @param op check to see if the process is waiting for this operation
         * @param addr check to see if the process is reading from this address
         * @return true if the process is blocked by the given parameters
         */
        public boolean isBlockedForDevice(Device dev, int op, int addr) {
            if ((blockedForDevice == dev) && (blockedForOperation == op)) {
                if (op == SYSCALL_OPEN) {
                    return true;
                }

                if (addr == blockedForAddr) {
                    return true;
                }
            }// if

            return false;
        }// isBlockedForDevice

        /**
         * overallAvgStarve
         *
         * @return the overall average starve time for all currently running
         *         processes
         */
        public double overallAvgStarve()
        {
            double result = 0.0;
            int count = 0;
            for (ProcessControlBlock pi : m_processes)
            {
                if (pi.avgStarve > 0)
                {
                    result = result + pi.avgStarve;
                    count++;
                }
            }
            if (count > 0)
            {
                result = result / count;
            }

            return result;
        }// overallAvgStarve

        /**
         * compareTo compares this to another ProcessControlBlock object based
         * on the BASE addr register. Read about Java's Collections class for
         * info on how this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi) {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
        }

        /**
         * getRegisterValue Retrieves the value of a process' register that is
         * stored in this object (this.registers).
         * 
         * @param idx the index of the register to retrieve. Use the constants
         *            in the CPU class
         * @return one of the register values stored in in this object or -999
         *         if an invalid index is given
         */
        public int getRegisterValue(int idx) {
            if ((idx < 0) || (idx >= CPU.NUMREG)) {
                return -999; // invalid index
            }

            return this.registers[idx];
        }// getRegisterValue

        /**
         * setRegisterValue Sets the value of a process' register that is stored
         * in this object (this.registers).
         * 
         * @param idx the index of the register to set. Use the constants in the
         *            CPU class. If an invalid index is given, this method does
         *            nothing.
         * @param val the value to set the register to
         */
        public void setRegisterValue(int idx, int val) {
            if ((idx < 0) || (idx >= CPU.NUMREG)) {
                return; // invalid index
            }

            this.registers[idx] = val;
        }// setRegisterValue

        /**
         * toString **DEBUGGING**
         *
         * @return a string representation of this class
         */
        public String toString()
        {
            // Print the Process ID and process state (READY, RUNNING, BLOCKED)
            String result = "Process id " + processId + " ";
            if (isBlocked())
            {
                result = result + "is BLOCKED for ";
                // Print device, syscall and address that caused the BLOCKED
                // state
                if (blockedForOperation == SYSCALL_OPEN)
                {
                    result = result + "OPEN";
                }
                else
                {
                    result = result + "WRITE @" + blockedForAddr;
                }
                for (DeviceInfo di : m_devices)
                {
                    if (di.getDevice() == blockedForDevice)
                    {
                        result = result + " on device #" + di.getId();
                        break;
                    }
                }
                result = result + ": ";
            }
            else if (this == m_currProcess)
            {
                result = result + "is RUNNING: ";
            }
            else
            {
                result = result + "is READY: ";
            }

            // Print the register values stored in this object. These don't
            // necessarily match what's on the CPU for a Running process.
            if (registers == null)
            {
                result = result + "<never saved>";
                return result;
            }

            for (int i = 0; i < CPU.NUMGENREG; i++)
            {
                result = result + ("r" + i + "=" + registers[i] + " ");
            }// for
            result = result + ("PC=" + registers[CPU.PC] + " ");
            result = result + ("SP=" + registers[CPU.SP] + " ");
            result = result + ("BASE=" + registers[CPU.BASE] + " ");
            result = result + ("LIM=" + registers[CPU.LIM] + " ");

            // Print the starve time statistics for this process
            result = result + "\n\t\t\t";
            result = result + " Max Starve Time: " + maxStarve;
            result = result + " Avg Starve Time: " + avgStarve;

            return result;
        }// toString

    }// class ProcessControlBlock

    /**
     * class DeviceInfo This class contains information about a device that is
     * currently registered with the system.
     */
    private class DeviceInfo {
        /** every device has a unique id */
        private int id;
        /** a reference to the device driver for this device */
        private Device device;
        /** a list of processes that have opened this device */
        private Vector<ProcessControlBlock> procs;

        /**
         * constructor
         * 
         * @param d a reference to the device driver for this device
         * @param initID the id for this device. The caller is responsible for
         *            guaranteeing that this is a unique id.
         */
        public DeviceInfo(Device d, int initID) {
            this.id = initID;
            this.device = d;
            d.setId(initID);
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId() {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice() {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi) {
            procs.add(pi);
        }

        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi) {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi) {
            return procs.contains(pi);
        }

        /**
         * @return a vector of ProcessControlBlocks which have the device open
         *         (or are blocked for it.)
         */
        public Vector<ProcessControlBlock> getPCBs() {
            return procs;
        }

        /** Is this device currently not opened by any process? */
        public boolean unused() {
            return procs.size() == 0;
        }

    }// class DeviceInfo

};// class SOS

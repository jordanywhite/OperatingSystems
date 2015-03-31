Author: Jordan White

Algorithm:
Three priority queues collectively contain every process. If there is 
a currently running process, check if it is running over
its quantum. If it is not, allow it to continue running without
interruption. If it has exceeded its quantum, lower its priority
and select a new process to run if there is one. The first
unblocked process found in the highest priority queue will be selected
 to run. The additions include 3 Vectors containing 3 priorities of
 processes, high-normal-low, a quantum for every PCB instance, and
 a max quantum of 10 ticks for all process to abide by.
 
 Installation:
 Download sos package containing .java files. Compile the package
 in a manner of your choosing.
 
 
 
 
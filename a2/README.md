# CS456 A2

## Compilation

    $ make

    On host1, run:
	$ ./nEmulator <emulator's receiving UDP port number in the forward (sender) direction>
                  <receiver's network address>
                  <receiver's receiving UDP port number>
                  <emulator's receiving UDP port number in the backward (receiver) direction>
                  <sender's network address>
                  <sender's receiving UDP port number>
                  <maximum delay of the link in units of millisecond>
                  <packet discard probability>
                  <verbose-mode> 
    
    On host2, run:
	$ java Receiver <hostname for the network emulator>
                    <UDP port number used by the link emulator to receive ACKs from the receiver>
                    <UDP port number used by the receiver to receive data from the emulator>
                    <name of the file into which the received data is written>

    On host3, run:
    $ java Sender <host address of the network emulator>
                  <UDP port number used by the emulator to receive data from the sender>
                  <UDP port number used by the sender to receive ACKs from the emulator>
                  <name of the file to be transferred>

## Testing
### Tested on:
	ubuntu1604-002.student.cs.uwaterloo.ca (nEmulator)
	ubuntu1604-004.student.cs.uwaterloo.ca (Sender)
	ubuntu1604-006.student.cs.uwaterloo.ca (Receiver)


### Java Version:
	javac 9-internal
    openjdk version "9-internal"
    
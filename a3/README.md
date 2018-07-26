# CS456 A3

## Compilation

    $ make

    On host1, run:
    $ ./nse-linux386 <host where routers are running>
                      <Network State Emulator port number>

    On host2, run 5 Router programs, each with:
    $ java Router <router's id> 
                  <host  where  the  Network  State  Emulator  is  running>
                  <port  number  of  the  Network  State  Emulator>
                  <router's port>


## Testing
### Tested on:
	ubuntu1604-002.student.cs.uwaterloo.ca (Network  State  Emulator )
	ubuntu1604-004.student.cs.uwaterloo.ca (Routers)


### Java Version:
	javac 9-internal
    openjdk version "9-internal"
    
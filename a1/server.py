from socket import *
import sys

# check number of args
if len(sys.argv) != 2:
	raise ValueError("invalid number of input arguments")

# check if numerical input is valid
try:
    req_code = int(sys.argv[1])
except ValueError:
    raise ValueError("invalid input type for req_code")

serverSocket = socket(AF_INET, SOCK_STREAM)
serverSocket.bind(("", 0))
n_port = serverSocket.getsockname()[1]
print("SERVER_PORT={}".format(n_port))
serverSocket.listen(1)
print ('TCP server is ready to receive')

while True:
	# listen for TCP handshake
	connectionSocket, addr = serverSocket.accept()
	client_req_code = int(connectionSocket.recv(1024).decode())
	
    # if client's req_code matches with server side, open up a UDP port and send the port number back
	if client_req_code == req_code:
		# open up a UDP port
		udpSocket = socket(AF_INET, SOCK_DGRAM)
		udpSocket.bind(("", 0))
		r_port = udpSocket.getsockname()[1]
        # send the port number back as the TCP response
		connectionSocket.send(str(r_port))

        # listen for data transmission at the UDP port
		msg, client_address = udpSocket.recvfrom(1024)
        # process the input and send it back via UDP
		modified_msg = msg.decode()[::-1]
		udpSocket.sendto(modified_msg.encode(), client_address)
		udpSocket.close()
	connectionSocket.close()
	